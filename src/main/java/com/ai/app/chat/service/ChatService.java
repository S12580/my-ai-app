package com.ai.app.chat.service;

import com.ai.app.chat.domain.ChatMessage;
import com.ai.app.chat.domain.ChatSession;
import com.ai.app.chat.dto.CreateSessionRequest;
import com.ai.app.chat.dto.MessageResponse;
import com.ai.app.chat.dto.PageResponse;
import com.ai.app.chat.dto.PatchSessionRequest;
import com.ai.app.chat.dto.SendMessageRequest;
import com.ai.app.chat.dto.SendMessageResponse;
import com.ai.app.chat.dto.SessionResponse;
import com.ai.app.chat.mapper.ChatMessageMapper;
import com.ai.app.chat.mapper.ChatSessionMapper;
import com.ai.app.service.BailianService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final int DEFAULT_MESSAGE_LIMIT = 500;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatRepositoryService chatRepositoryService;
    private final BailianService bailianService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${bailian.api.model}")
    private String defaultModel;

    @Value("${bailian.api.max-tokens}")
    private int defaultMaxTokens;

    public ChatService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatRepositoryService chatRepositoryService,
            BailianService bailianService) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatRepositoryService = chatRepositoryService;
        this.bailianService = bailianService;
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
    }

    @Transactional
    public void deleteMessage(Long sessionId, Long messageId) {
        ensureSession(sessionId);
        int affected = chatMessageMapper.deleteByIdAndSessionId(messageId, sessionId);
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found");
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

    @Transactional
    public SendMessageResponse sendNonStreaming(Long sessionId, SendMessageRequest request) {
        ChatSession session = ensureSession(sessionId);
        String model = resolveModel(session, request);
        int maxTokens = resolveMaxTokens(request);

        ChatMessage user = chatRepositoryService.insertUserMessage(sessionId, request.getContent().trim());

        List<ChatMessage> history = chatMessageMapper.findBySessionIdAsc(sessionId, DEFAULT_MESSAGE_LIMIT);
        List<Map<String, Object>> apiMessages = toApiMessages(history);
        String reply = bailianService.chat(apiMessages, model, maxTokens);

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

        chatRepositoryService.insertUserMessageNewTx(sessionId, request.getContent().trim());

        List<ChatMessage> history = chatMessageMapper.findBySessionIdAsc(sessionId, DEFAULT_MESSAGE_LIMIT);
        List<Map<String, Object>> apiMessages = toApiMessages(history);

        StringBuilder accumulator = new StringBuilder();

        return bailianService.chatStream(apiMessages, model, maxTokens)
                .doOnNext(accumulator::append)
                .map(delta -> ndJsonLine(Map.of("type", "delta", "text", delta)))
                .concatWith(Mono.fromCallable(() -> {
                    Long assistantMessageId = chatRepositoryService.insertAssistantMessage(
                            sessionId,
                            accumulator.toString(),
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
        return session.getModel();
    }

    private int resolveMaxTokens(SendMessageRequest request) {
        if (request.getMaxTokens() != null && request.getMaxTokens() > 0) {
            return request.getMaxTokens();
        }
        return defaultMaxTokens;
    }

    private List<Map<String, Object>> toApiMessages(List<ChatMessage> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatMessage row : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", row.getRole());
            m.put("content", row.getContent());
            list.add(m);
        }
        return list;
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
}
