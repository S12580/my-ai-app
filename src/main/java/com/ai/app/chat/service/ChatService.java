package com.ai.app.chat.service;

import com.ai.app.chat.domain.ChatMessage;
import com.ai.app.chat.domain.ChatSession;
import com.ai.app.chat.dto.AnalyzedAttachmentPart;
import com.ai.app.chat.dto.CreateSessionRequest;
import com.ai.app.chat.dto.MessageAttachmentPart;
import com.ai.app.chat.dto.MessageResponse;
import com.ai.app.chat.dto.PageResponse;
import com.ai.app.chat.dto.PatchSessionRequest;
import com.ai.app.chat.dto.SendMessageRequest;
import com.ai.app.chat.dto.SendMessageResponse;
import com.ai.app.chat.dto.SessionResponse;
import com.ai.app.chat.mapper.ChatMessageMapper;
import com.ai.app.chat.mapper.ChatSessionMapper;
import com.ai.app.rag.dto.RagCitation;
import com.ai.app.rag.service.RagRetrievalResult;
import com.ai.app.rag.service.RagService;
import com.ai.app.service.BailianService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {

    private static final int MAX_REFERENCE_SOURCES = 5;

    private static final int DEFAULT_MESSAGE_LIMIT = 500;
    private static final int MAX_CHAT_ATTACH_FILES = 3;
    private static final int MAX_EXTRACT_CHARS_PER_PART = 120_000;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatRepositoryService chatRepositoryService;
    private final ChatAttachmentStorage chatAttachmentStorage;
    private final BailianService bailianService;
    private final RagService ragService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${bailian.api.model}")
    private String defaultModel;

    /** Used when the request includes image attachments (multimodal); overrides session model unless request sets model. */
    @Value("${bailian.api.chat-vision-model:qwen-vl-plus-latest}")
    private String chatVisionModel;

    @Value("${bailian.api.max-tokens}")
    private int defaultMaxTokens;

    public ChatService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatRepositoryService chatRepositoryService,
            ChatAttachmentStorage chatAttachmentStorage,
            BailianService bailianService,
            RagService ragService) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatRepositoryService = chatRepositoryService;
        this.chatAttachmentStorage = chatAttachmentStorage;
        this.bailianService = bailianService;
        this.ragService = ragService;
    }

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        ChatSession s = new ChatSession();
        String title = request.getTitle() != null ? request.getTitle().trim() : "";
        s.setTitle(title);
        String model = (request.getModel() != null && !request.getModel().isBlank())
                ? request.getModel().trim()
                : defaultModel;
        s.setModel(model);
        chatSessionMapper.insert(s);
        if (s.getId() == null) {
            throw new IllegalStateException(
                    "Insert did not populate generated id; check MyBatis insert keyColumn and JDBC driver.");
        }
        ChatSession loaded = chatSessionMapper.findById(s.getId());
        if (loaded == null) {
            throw new IllegalStateException("Session not found immediately after insert, id=" + s.getId());
        }
        return toSessionResponse(loaded);
    }

    public PageResponse<SessionResponse> listSessions(int page, int size) {
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
        long total = chatSessionMapper.countAll();
        int offset = page * size;
        List<ChatSession> rows = chatSessionMapper.findPage(offset, size);
        List<SessionResponse> content = rows.stream().map(this::toSessionResponse).toList();
        PageResponse<SessionResponse> out = new PageResponse<>();
        out.setContent(content);
        out.setTotalElements(total);
        out.setPage(page);
        out.setSize(size);
        return out;
    }

    public SessionResponse getSession(Long id) {
        ChatSession s = chatSessionMapper.findById(id);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
        return toSessionResponse(s);
    }

    public SessionResponse patchSession(Long id, PatchSessionRequest request) {
        ChatSession s = chatSessionMapper.findById(id);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
        chatSessionMapper.updateTitle(id, request.getTitle().trim());
        return toSessionResponse(chatSessionMapper.findById(id));
    }

    @Transactional
    public void deleteSession(Long id) {
        ensureSession(id);
        chatMessageMapper.deleteBySessionId(id);
        chatSessionMapper.deleteById(id);
        chatAttachmentStorage.deleteSessionFolders(id);
    }

    @Transactional
    public void deleteMessage(Long sessionId, Long messageId) {
        ensureSession(sessionId);
        ChatMessage row = chatMessageMapper.findById(messageId);
        if (row == null || !Objects.equals(sessionId, row.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found");
        }
        boolean userRow = "user".equalsIgnoreCase(row.getRole());
        int affected = chatMessageMapper.deleteByIdAndSessionId(messageId, sessionId);
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found");
        }
        if (userRow) {
            chatAttachmentStorage.deleteMessageFolder(sessionId, messageId);
        }
        chatSessionMapper.touchUpdatedAt(sessionId);
    }

    public List<MessageResponse> listMessages(Long sessionId, Long beforeId, int limit) {
        ensureSession(sessionId);
        if (limit < 1) {
            limit = DEFAULT_MESSAGE_LIMIT;
        }
        if (limit > DEFAULT_MESSAGE_LIMIT) {
            limit = DEFAULT_MESSAGE_LIMIT;
        }
        List<ChatMessage> rows;
        if (beforeId == null) {
            rows = chatMessageMapper.findBySessionIdAsc(sessionId, limit);
        } else {
            rows = chatMessageMapper.findBySessionIdAscBefore(sessionId, beforeId, limit);
            Collections.reverse(rows);
        }
        return rows.stream().map(this::toMessageResponse).toList();
    }

    public List<AnalyzedAttachmentPart> analyzeChatAttachments(Long sessionId, List<MultipartFile> files) {
        ensureSession(sessionId);
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "files required");
        }
        if (files.size() > MAX_CHAT_ATTACH_FILES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "at most " + MAX_CHAT_ATTACH_FILES + " files");
        }
        long totalBytes = 0;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file in list");
            }
            totalBytes += f.getSize();
        }
        if (totalBytes > RagService.MAX_EXTRACT_FILE_BYTES * MAX_CHAT_ATTACH_FILES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "total upload size too large");
        }
        List<AnalyzedAttachmentPart> out = new ArrayList<>();
        for (MultipartFile f : files) {
            ragService.validateMultipartForTextExtract(f);
            String originalName = f.getOriginalFilename() != null ? f.getOriginalFilename() : "file";
            String lowerName = originalName.toLowerCase(Locale.ROOT);
            String mime = RagService.normalizeUploadMimeType(lowerName, f.getContentType());
            String attachmentId = UUID.randomUUID().toString();
            Path pendingPath = chatAttachmentStorage.pendingBlobPath(sessionId, attachmentId);
            try {
                byte[] raw = f.getBytes();
                chatAttachmentStorage.savePending(sessionId, attachmentId, raw);
                String text;
                if (RagService.isImageFilename(lowerName)) {
                    // Images are sent as multimodal parts at chat time; skip slow vision OCR here.
                    text = "[图片附件]";
                } else {
                    text = ragService.extractTextFromPersistedPath(pendingPath, lowerName, mime);
                    text = truncateForStore(text);
                }
                AnalyzedAttachmentPart p = new AnalyzedAttachmentPart();
                p.setAttachmentId(attachmentId);
                p.setFileName(originalName);
                p.setMimeType(mime);
                p.setExtractedText(text);
                out.add(p);
            } catch (ResponseStatusException e) {
                try {
                    Files.deleteIfExists(pendingPath);
                } catch (IOException ignored) {
                }
                throw e;
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(pendingPath);
                } catch (IOException ignored) {
                }
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "failed to store attachment: " + e.getMessage());
            }
        }
        return out;
    }

    @Transactional
    public SendMessageResponse sendNonStreaming(Long sessionId, SendMessageRequest request) {
        ChatSession session = ensureSession(sessionId);
        String model = resolveModel(session, request);
        int maxTokens = resolveMaxTokens(request);

        validateSendPayload(sessionId, request);
        String mergedUserContent = mergeUserMessageBody(request);
        String attachmentMeta = buildAttachmentMetaJson(request);

        ChatMessage user = chatRepositoryService.insertUserMessage(sessionId, mergedUserContent, attachmentMeta);

        bindPendingAttachments(sessionId, user.getId(), request);

        List<ChatMessage> history = chatMessageMapper.findBySessionIdAsc(sessionId, DEFAULT_MESSAGE_LIMIT);
        List<Map<String, Object>> apiMessages =
                toApiMessagesForSend(sessionId, user.getId(), history, request);
        RagRetrievalResult retrieval = maybeRetrieveRag(request, mergedUserContent);
        injectRagContext(apiMessages, retrieval);
        String reply = bailianService.chat(apiMessages, model, maxTokens);
        reply = appendCitations(reply, retrieval.getCitations());

        Long assistantId = chatRepositoryService.insertAssistantMessageSameTx(sessionId, reply, null);

        ChatMessage assistantRow = chatMessageMapper.findById(assistantId);
        SendMessageResponse out = new SendMessageResponse();
        out.setUserMessage(toMessageResponse(user));
        out.setAssistantMessage(toMessageResponse(assistantRow));
        return out;
    }

    public Flux<String> streamMessages(Long sessionId, SendMessageRequest request) {
        ChatSession session = ensureSession(sessionId);
        String model = resolveModel(session, request);
        int maxTokens = resolveMaxTokens(request);

        validateSendPayload(sessionId, request);
        String mergedUserContent = mergeUserMessageBody(request);
        String attachmentMeta = buildAttachmentMetaJson(request);

        ChatMessage user = chatRepositoryService.insertUserMessageNewTx(sessionId, mergedUserContent, attachmentMeta);
        try {
            bindPendingAttachments(sessionId, user.getId(), request);
        } catch (RuntimeException e) {
            chatMessageMapper.deleteByIdAndSessionId(user.getId(), sessionId);
            throw e;
        }

        List<ChatMessage> history = chatMessageMapper.findBySessionIdAsc(sessionId, DEFAULT_MESSAGE_LIMIT);
        List<Map<String, Object>> apiMessages =
                toApiMessagesForSend(sessionId, user.getId(), history, request);
        RagRetrievalResult retrieval = maybeRetrieveRag(request, mergedUserContent);
        injectRagContext(apiMessages, retrieval);

        StringBuilder accumulator = new StringBuilder();

        return bailianService.chatStream(apiMessages, model, maxTokens)
                .doOnNext(accumulator::append)
                .map(delta -> ndJsonLine(Map.of("type", "delta", "text", delta)))
                .concatWith(Mono.fromCallable(() -> {
                    Long assistantMessageId = chatRepositoryService.insertAssistantMessage(
                            sessionId,
                            appendCitations(accumulator.toString(), retrieval.getCitations()),
                            null);
                    return ndJsonLine(Map.of(
                            "type", "done",
                            "assistantMessageId", assistantMessageId));
                }))
                .onErrorResume(e -> Flux.just(ndJsonLine(Map.of(
                        "type", "error",
                        "message", e.getMessage() != null ? e.getMessage() : "stream error"))));
    }

    private ChatSession ensureSession(Long sessionId) {
        ChatSession s = chatSessionMapper.findById(sessionId);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
        return s;
    }

    private String resolveModel(ChatSession session, SendMessageRequest request) {
        if (request.getModel() != null && !request.getModel().isBlank()) {
            return request.getModel().trim();
        }
        if (hasImageAttachments(request)) {
            return chatVisionModel;
        }
        return session.getModel();
    }

    private int resolveMaxTokens(SendMessageRequest request) {
        if (request.getMaxTokens() != null && request.getMaxTokens() > 0) {
            return request.getMaxTokens();
        }
        return defaultMaxTokens;
    }

    private List<Map<String, Object>> toApiMessagesForSend(
            long sessionId,
            long lastUserMessageId,
            List<ChatMessage> rows,
            SendMessageRequest request) {
        List<Map<String, Object>> list = new ArrayList<>();
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            ChatMessage row = rows.get(i);
            boolean last = (i == n - 1);
            if (last
                    && "user".equalsIgnoreCase(row.getRole())
                    && request != null
                    && hasImageAttachments(request)) {
                list.add(buildMultimodalUserMessage(sessionId, lastUserMessageId, request));
            } else {
                Map<String, Object> m = new HashMap<>();
                m.put("role", row.getRole());
                m.put("content", row.getContent());
                list.add(m);
            }
        }
        return list;
    }

    private boolean hasImageAttachments(SendMessageRequest request) {
        List<MessageAttachmentPart> parts = request.getAttachments();
        if (parts == null || parts.isEmpty()) {
            return false;
        }
        for (MessageAttachmentPart p : parts) {
            if (isImageAttachmentPart(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean isImageAttachmentPart(MessageAttachmentPart p) {
        String mime = p.getMimeType();
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String fn = p.getFileName();
        if (fn == null || fn.isBlank()) {
            return false;
        }
        return RagService.isImageFilename(fn.toLowerCase(Locale.ROOT));
    }

    /**
     * Text for the multimodal user turn: typed message plus non-image attachment extracts.
     * Image bytes are sent separately as {@code image_url} parts.
     */
    private String buildMultimodalUserText(SendMessageRequest request) {
        String base = request.getContent() == null ? "" : request.getContent().trim();
        List<MessageAttachmentPart> parts = request.getAttachments();
        if (parts == null || parts.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        for (MessageAttachmentPart p : parts) {
            if (isImageAttachmentPart(p)) {
                continue;
            }
            sb.append("\n\n--- ").append(p.getFileName()).append(" ---\n");
            sb.append(truncateForStore(p.getExtractedText()));
        }
        long imgCount = parts.stream().filter(this::isImageAttachmentPart).count();
        if (imgCount > 0) {
            sb.append("\n\n[附 ").append(imgCount).append(" 张图片，模型将直接阅读下方图像]");
        }
        return sb.toString();
    }

    private Map<String, Object> buildMultimodalUserMessage(
            long sessionId,
            long messageId,
            SendMessageRequest request) {
        List<Map<String, Object>> contentParts = new ArrayList<>();
        String text = buildMultimodalUserText(request);
        if (text.isBlank()) {
            text = "(请结合附图回答)";
        }
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", text);
        contentParts.add(textPart);

        List<MessageAttachmentPart> parts = request.getAttachments();
        if (parts != null) {
            for (MessageAttachmentPart p : parts) {
                if (!isImageAttachmentPart(p)) {
                    continue;
                }
                String aid = p.getAttachmentId().trim();
                Path path = chatAttachmentStorage.messageBlobPath(sessionId, messageId, aid);
                byte[] bytes;
                try {
                    bytes = Files.readAllBytes(path);
                } catch (IOException e) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "failed to read image attachment");
                }
                String lowerFn =
                        (p.getFileName() != null ? p.getFileName() : "image.png")
                                .toLowerCase(Locale.ROOT);
                String mime = RagService.normalizeUploadMimeType(lowerFn, p.getMimeType());
                if (!mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    mime = "image/png";
                }
                String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
                Map<String, Object> imagePart = new HashMap<>();
                imagePart.put("type", "image_url");
                Map<String, String> imageUrl = new HashMap<>();
                imageUrl.put("url", dataUrl);
                imagePart.put("image_url", imageUrl);
                contentParts.add(imagePart);
            }
        }

        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", contentParts);
        return user;
    }

    private SessionResponse toSessionResponse(ChatSession s) {
        SessionResponse r = new SessionResponse();
        r.setId(s.getId());
        r.setTitle(s.getTitle());
        r.setModel(s.getModel());
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }

    private MessageResponse toMessageResponse(ChatMessage m) {
        MessageResponse r = new MessageResponse();
        r.setId(m.getId());
        r.setSessionId(m.getSessionId());
        r.setRole(m.getRole());
        r.setContent(m.getContent());
        r.setMetaJson(m.getMetaJson());
        r.setCreatedAt(m.getCreatedAt());
        return r;
    }

    private String ndJsonLine(Map<String, ?> map) {
        try {
            return objectMapper.writeValueAsString(map) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private RagRetrievalResult maybeRetrieveRag(SendMessageRequest request, String mergedUserMessage) {
        if (request.getUseRag() == null || !request.getUseRag()) {
            RagRetrievalResult empty = new RagRetrievalResult();
            empty.setContextText("");
            empty.setCitations(List.of());
            return empty;
        }
        String query = ragQueryForRetrieval(request, mergedUserMessage);
        return ragService.retrieve(query, request.getRagDocumentIds(), null);
    }

    private void validateSendPayload(Long sessionId, SendMessageRequest request) {
        String base = request.getContent() == null ? "" : request.getContent().trim();
        List<MessageAttachmentPart> parts = request.getAttachments();
        boolean hasParts = parts != null && !parts.isEmpty();
        if (base.isEmpty() && !hasParts) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content or attachments required");
        }
        if (hasParts) {
            if (parts.size() > MAX_CHAT_ATTACH_FILES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "at most " + MAX_CHAT_ATTACH_FILES + " attachments");
            }
            Set<String> seen = new HashSet<>();
            for (MessageAttachmentPart p : parts) {
                if (p.getFileName() == null || p.getFileName().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attachment fileName required");
                }
                if (p.getExtractedText() == null || p.getExtractedText().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attachment extractedText required");
                }
                if (p.getAttachmentId() == null || p.getAttachmentId().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attachment attachmentId required");
                }
                String aid = p.getAttachmentId().trim();
                if (!seen.add(aid)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate attachmentId");
                }
                if (!chatAttachmentStorage.pendingExists(sessionId, aid)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "invalid or expired attachmentId (analyze again)");
                }
            }
        }
    }

    private String mergeUserMessageBody(SendMessageRequest request) {
        String base = request.getContent() == null ? "" : request.getContent().trim();
        List<MessageAttachmentPart> parts = request.getAttachments();
        if (parts == null || parts.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n--- 附件 ---");
        for (MessageAttachmentPart p : parts) {
            sb.append("\n\n--- ").append(p.getFileName()).append(" ---\n");
            if (isImageAttachmentPart(p)) {
                sb.append("[图片附件]");
            } else {
                sb.append(truncateForStore(p.getExtractedText()));
            }
        }
        return sb.toString();
    }

    private String truncateForStore(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_EXTRACT_CHARS_PER_PART) {
            return text;
        }
        return text.substring(0, MAX_EXTRACT_CHARS_PER_PART) + "\n...[truncated]";
    }

    private String buildAttachmentMetaJson(SendMessageRequest request) {
        List<MessageAttachmentPart> parts = request.getAttachments();
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, String>> list = new ArrayList<>();
            for (MessageAttachmentPart p : parts) {
                Map<String, String> e = new HashMap<>();
                e.put("attachmentId", p.getAttachmentId().trim());
                e.put("fileName", p.getFileName());
                e.put("mimeType", p.getMimeType() != null ? p.getMimeType() : "");
                list.add(e);
            }
            return objectMapper.writeValueAsString(Map.of("attachments", list));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String ragQueryForRetrieval(SendMessageRequest request, String mergedUserMessage) {
        String q = request.getContent() == null ? "" : request.getContent().trim();
        if (!q.isEmpty()) {
            return q;
        }
        if (mergedUserMessage == null || mergedUserMessage.isEmpty()) {
            return "";
        }
        return mergedUserMessage.length() > 2000 ? mergedUserMessage.substring(0, 2000) : mergedUserMessage;
    }

    private void injectRagContext(List<Map<String, Object>> apiMessages, RagRetrievalResult retrieval) {
        if (retrieval.getContextText() == null || retrieval.getContextText().isBlank()) {
            return;
        }
        Map<String, Object> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", "你是知识库问答助手。优先依据给定参考资料回答，若资料不足请明确说不知道。\n\n参考资料:\n"
                + retrieval.getContextText());
        apiMessages.add(0, system);
    }

    public ResponseEntity<Resource> serveChatAttachment(long sessionId, long messageId, String attachmentId) {
        ensureSession(sessionId);
        ChatMessage m = chatMessageMapper.findById(messageId);
        if (m == null || !Objects.equals(sessionId, m.getSessionId()) || !"user".equalsIgnoreCase(m.getRole())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found");
        }
        if (attachmentId == null || attachmentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attachmentId required");
        }
        String aid = attachmentId.trim();
        if (!metaContainsAttachmentId(m.getMetaJson(), aid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "attachment not found");
        }
        String fileName = readAttachmentFileName(m.getMetaJson(), aid);
        String mime = readAttachmentMime(m.getMetaJson(), aid);
        Resource body = chatAttachmentStorage.loadMessageBlob(sessionId, messageId, aid);
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (mime != null && !mime.isBlank()) {
            try {
                mt = MediaType.parseMediaType(mime);
            } catch (Exception ignored) {
            }
        }
        boolean inline = mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/");
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mt)
                .body(body);
    }

    private void bindPendingAttachments(long sessionId, long messageId, SendMessageRequest request) {
        List<String> ids = extractAttachmentIds(request);
        if (ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            if (!chatAttachmentStorage.pendingExists(sessionId, id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "invalid or expired attachmentId (analyze again)");
            }
        }
        try {
            chatAttachmentStorage.bindAll(sessionId, messageId, ids);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to bind attachments");
        }
    }

    private List<String> extractAttachmentIds(SendMessageRequest request) {
        List<MessageAttachmentPart> parts = request.getAttachments();
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (MessageAttachmentPart p : parts) {
            if (p.getAttachmentId() != null && !p.getAttachmentId().isBlank()) {
                ids.add(p.getAttachmentId().trim());
            }
        }
        return ids;
    }

    private boolean metaContainsAttachmentId(String metaJson, String attachmentId) {
        if (metaJson == null || metaJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(metaJson);
            JsonNode arr = root.path("attachments");
            if (!arr.isArray()) {
                return false;
            }
            for (JsonNode n : arr) {
                if (attachmentId.equals(n.path("attachmentId").asText(""))) {
                    return true;
                }
            }
        } catch (JsonProcessingException ignored) {
        }
        return false;
    }

    private String readAttachmentFileName(String metaJson, String attachmentId) {
        try {
            JsonNode root = objectMapper.readTree(metaJson);
            for (JsonNode n : root.path("attachments")) {
                if (attachmentId.equals(n.path("attachmentId").asText(""))) {
                    String fn = n.path("fileName").asText("file");
                    return fn.isBlank() ? "file" : fn;
                }
            }
        } catch (JsonProcessingException ignored) {
        }
        return "file";
    }

    private String readAttachmentMime(String metaJson, String attachmentId) {
        try {
            JsonNode root = objectMapper.readTree(metaJson);
            for (JsonNode n : root.path("attachments")) {
                if (attachmentId.equals(n.path("attachmentId").asText(""))) {
                    return n.path("mimeType").asText("");
                }
            }
        } catch (JsonProcessingException ignored) {
        }
        return "";
    }

    private String appendCitations(String answer, List<RagCitation> citations) {
        String part = RagCitation.formatReferenceAppendix(citations, MAX_REFERENCE_SOURCES);
        if (part.isEmpty()) {
            return answer;
        }
        return (answer == null ? "" : answer.trim()) + part;
    }
}
