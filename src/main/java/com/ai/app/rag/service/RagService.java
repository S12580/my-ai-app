package com.ai.app.rag.service;

import com.ai.app.rag.domain.KbChunk;
import com.ai.app.rag.domain.KbDocument;
import com.ai.app.rag.dto.RagCitation;
import com.ai.app.chat.dto.PageResponse;
import com.ai.app.rag.dto.RagDocumentResponse;
import com.ai.app.rag.mapper.KbChunkMapper;
import com.ai.app.rag.mapper.KbDocumentMapper;
import com.ai.app.service.BailianService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final int DEFAULT_CHUNK_SIZE = 700;
    private static final int DEFAULT_CHUNK_OVERLAP = 120;
    /** Chunks passed to the LLM; must be > naive top-N or one large doc dominates. */
    private static final int DEFAULT_TOP_K = 12;
    private static final int MAX_TOP_K = 24;
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;
    /** Max size per file for extract-only (chat) and ingest validation. */
    public static final long MAX_EXTRACT_FILE_BYTES = 15L * 1024 * 1024;

    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkMapper kbChunkMapper;
    private final BailianService bailianService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.storage.type:local}")
    private String storageType;

    @Value("${rag.storage.local-base-dir:./data/kb-files}")
    private String localBaseDir;

    public RagService(KbDocumentMapper kbDocumentMapper, KbChunkMapper kbChunkMapper, BailianService bailianService) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.bailianService = bailianService;
    }

    @Transactional
    public RagDocumentResponse uploadAndIngest(MultipartFile file) {
        validateMultipartForTextExtract(file);

        String originalName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String lowerName = originalName.toLowerCase(Locale.ROOT);

        Long docId = prepareDocumentSlotForUpload(originalName);

        try {
            Path savedPath = saveFile(file, docId, originalName);
            kbDocumentMapper.updateSourcePath(docId, savedPath.toString());
            String content = extractText(savedPath, lowerName, file.getContentType());
            List<String> chunks = splitIntoChunks(content, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
            if (chunks.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document has no readable text");
            }

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                List<Double> embedding = bailianService.embedText(chunkText);
                KbChunk chunk = new KbChunk();
                chunk.setDocumentId(docId);
                chunk.setChunkIndex(i);
                chunk.setChunkText(chunkText);
                chunk.setTokenCount(estimateTokenCount(chunkText));
                chunk.setEmbeddingJson(objectMapper.writeValueAsString(embedding));
                kbChunkMapper.insert(chunk);
            }

            kbDocumentMapper.updateStatus(docId, "ready", null);
        } catch (ResponseStatusException e) {
            kbDocumentMapper.updateStatus(docId, "failed", limitError(e.getReason()));
            throw e;
        } catch (Exception e) {
            kbDocumentMapper.updateStatus(docId, "failed", limitError(e.getMessage()));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "rag ingest failed: " + e.getMessage());
        }

        KbDocument loaded = kbDocumentMapper.findById(docId);
        if (loaded == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "document row missing after ingest");
        }
        return toResponse(loaded);
    }

    /**
     * Validates type/size for text extraction (shared by RAG ingest and chat attachment analyze).
     */
    public void validateMultipartForTextExtract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        if (file.getSize() > MAX_EXTRACT_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file exceeds 15MB");
        }
        String originalName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (!isAllowedRagFilename(lowerName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "supported: txt, md, pdf, png, jpg, jpeg, gif, webp");
        }
        if (isRagImage(lowerName) && file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image exceeds 12MB");
        }
    }

    /**
     * Extracts plain text (or image-to-text) without persisting to kb_*.
     */
    public String extractTextFromUpload(MultipartFile file) {
        validateMultipartForTextExtract(file);
        String originalName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        String suffix = fileSuffixForTemp(lowerName);
        Path temp = null;
        try {
            temp = Files.createTempFile("extract-", suffix);
            Files.copy(file.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);
            String text = extractText(temp, lowerName, file.getContentType());
            if (text == null || text.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document has no readable text");
            }
            return text.trim();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to read upload: " + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Extracts text from a file already stored on disk (e.g. chat attachment pending blob).
     */
    public String extractTextFromPersistedPath(Path path, String lowerName, String uploadedContentType) {
        validatePersistedExtractFile(path, lowerName);
        try {
            String text = extractText(path, lowerName, uploadedContentType);
            if (text == null || text.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document has no readable text");
            }
            return text.trim();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to read file: " + e.getMessage());
        }
    }

    private void validatePersistedExtractFile(Path path, String lowerName) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is missing");
        }
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot read file size");
        }
        if (size > MAX_EXTRACT_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file exceeds 15MB");
        }
        if (!isAllowedRagFilename(lowerName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "supported: txt, md, pdf, png, jpg, jpeg, gif, webp");
        }
        if (isRagImage(lowerName) && size > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image exceeds 12MB");
        }
    }

    private static String fileSuffixForTemp(String lowerName) {
        if (lowerName.endsWith(".pdf")) {
            return ".pdf";
        }
        if (lowerName.endsWith(".md")) {
            return ".md";
        }
        if (lowerName.endsWith(".txt")) {
            return ".txt";
        }
        if (lowerName.endsWith(".png")) {
            return ".png";
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (lowerName.endsWith(".gif")) {
            return ".gif";
        }
        if (lowerName.endsWith(".webp")) {
            return ".webp";
        }
        return ".bin";
    }

    @Transactional
    public void deleteKbDocument(Long id) {
        KbDocument doc = kbDocumentMapper.findById(id);
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        kbChunkMapper.deleteByDocumentId(id);
        deleteLocalFileIfPresent(doc.getSourcePath());
        kbDocumentMapper.deleteById(id);
    }

    /**
     * New name: insert a row. Existing name(s): delete chunks and on-disk files, keep the highest id,
     * remove duplicate rows so list/citations stay unique per display name.
     */
    private Long prepareDocumentSlotForUpload(String originalName) {
        List<Long> sameNameIds = kbDocumentMapper.findIdsByName(originalName);
        if (sameNameIds.isEmpty()) {
            KbDocument doc = new KbDocument();
            doc.setName(originalName);
            doc.setSourceType("upload_" + storageType);
            doc.setStatus("processing");
            kbDocumentMapper.insert(doc);
            return doc.getId();
        }
        Long keepId = sameNameIds.get(sameNameIds.size() - 1);
        for (Long id : sameNameIds) {
            kbChunkMapper.deleteByDocumentId(id);
            KbDocument existing = kbDocumentMapper.findById(id);
            deleteLocalFileIfPresent(existing != null ? existing.getSourcePath() : null);
            if (!id.equals(keepId)) {
                kbDocumentMapper.deleteById(id);
            }
        }
        kbDocumentMapper.updateStatus(keepId, "processing", null);
        return keepId;
    }

    private void deleteLocalFileIfPresent(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return;
        }
        try {
            Path p = Paths.get(sourcePath).toAbsolutePath().normalize();
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best-effort cleanup of previous revision
        }
    }

    public PageResponse<RagDocumentResponse> listDocuments(int page, int size) {
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
        long total = kbDocumentMapper.countAll();
        int offset = page * size;
        List<KbDocument> rows = kbDocumentMapper.findPage(offset, size);
        List<RagDocumentResponse> content = rows.stream().map(this::toResponse).toList();
        PageResponse<RagDocumentResponse> out = new PageResponse<>();
        out.setContent(content);
        out.setTotalElements(total);
        out.setPage(page);
        out.setSize(size);
        return out;
    }

    /**
     * All document ids (for RAG scope when the client sends the full kb set); lightweight vs paged list.
     */
    public List<Long> listDocumentIds() {
        return kbDocumentMapper.findAllIds();
    }

    public RagRetrievalResult retrieve(String query, List<Long> documentIds, Integer topK) {
        List<Double> queryEmbedding = bailianService.embedText(query);
        int actualTopK = topK == null || topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, MAX_TOP_K);
        List<KbChunk> chunks = kbChunkMapper.findByDocumentIds(documentIds);
        if (chunks.isEmpty()) {
            RagRetrievalResult empty = new RagRetrievalResult();
            empty.setContextText("");
            empty.setCitations(List.of());
            return empty;
        }

        Map<Long, String> docNameMap = kbDocumentMapper.findAll().stream()
                .collect(Collectors.toMap(KbDocument::getId, KbDocument::getName, (a, b) -> a));

        List<ScoredChunk> scored = new ArrayList<>();
        for (KbChunk chunk : chunks) {
            List<Double> emb = parseEmbedding(chunk.getEmbeddingJson());
            if (emb.isEmpty()) {
                continue;
            }
            double score = cosine(queryEmbedding, emb);
            scored.add(new ScoredChunk(chunk, score));
        }

        List<ScoredChunk> sorted = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();
        List<ScoredChunk> top = pickDiverseTopChunks(sorted, actualTopK);

        StringBuilder contextBuilder = new StringBuilder();
        List<RagCitation> citations = new ArrayList<>();
        for (ScoredChunk s : top) {
            KbChunk chunk = s.chunk();
            contextBuilder.append("[doc:")
                    .append(chunk.getDocumentId())
                    .append(",chunk:")
                    .append(chunk.getChunkIndex())
                    .append("]\n")
                    .append(chunk.getChunkText())
                    .append("\n\n");

            RagCitation c = new RagCitation();
            c.setDocumentId(chunk.getDocumentId());
            c.setDocumentName(docNameMap.getOrDefault(chunk.getDocumentId(), "doc-" + chunk.getDocumentId()));
            c.setChunkIndex(chunk.getChunkIndex());
            c.setScore(s.score());
            c.setSnippet(chunk.getChunkText().length() > 120 ? chunk.getChunkText().substring(0, 120) + "..." : chunk.getChunkText());
            citations.add(c);
        }

        RagRetrievalResult out = new RagRetrievalResult();
        out.setContextText(contextBuilder.toString().trim());
        out.setCitations(citations);
        return out;
    }

    /**
     * Avoids one large document occupying every top slot: first take best chunks with a per-document cap,
     * then fill remaining slots by global score (so small / later uploads still get in).
     */
    private List<ScoredChunk> pickDiverseTopChunks(List<ScoredChunk> sortedByScoreDesc, int limit) {
        if (sortedByScoreDesc.isEmpty() || limit <= 0) {
            return List.of();
        }
        long distinctDocs = sortedByScoreDesc.stream()
                .map(s -> s.chunk().getDocumentId())
                .distinct()
                .count();
        int perDocCapPass1 = distinctDocs <= 1
                ? limit
                : Math.min(limit, Math.max(2, (int) Math.ceil((double) limit / distinctDocs)));

        List<ScoredChunk> picked = new ArrayList<>();
        Set<Long> seenChunkIds = new HashSet<>();
        Map<Long, Integer> countByDoc = new HashMap<>();

        for (ScoredChunk s : sortedByScoreDesc) {
            if (picked.size() >= limit) {
                break;
            }
            Long chunkId = s.chunk().getId();
            if (chunkId != null && seenChunkIds.contains(chunkId)) {
                continue;
            }
            long docId = s.chunk().getDocumentId();
            if (countByDoc.getOrDefault(docId, 0) >= perDocCapPass1) {
                continue;
            }
            if (chunkId != null) {
                seenChunkIds.add(chunkId);
            }
            picked.add(s);
            countByDoc.merge(docId, 1, Integer::sum);
        }

        for (ScoredChunk s : sortedByScoreDesc) {
            if (picked.size() >= limit) {
                break;
            }
            Long chunkId = s.chunk().getId();
            if (chunkId != null && seenChunkIds.contains(chunkId)) {
                continue;
            }
            if (chunkId != null) {
                seenChunkIds.add(chunkId);
            }
            picked.add(s);
        }

        return picked;
    }

    private Path saveFile(MultipartFile file, Long documentId, String originalName) throws IOException {
        if (!"local".equalsIgnoreCase(storageType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MVP currently supports rag.storage.type=local");
        }
        Path base = Paths.get(localBaseDir).toAbsolutePath().normalize();
        Files.createDirectories(base);
        String safeName = UUID.randomUUID() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = base.resolve(documentId + "_" + safeName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private boolean isAllowedRagFilename(String lowerName) {
        return lowerName.endsWith(".txt")
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".pdf")
                || isRagImage(lowerName);
    }

    private static boolean isRagImage(String lowerName) {
        return lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif")
                || lowerName.endsWith(".webp");
    }

    /** True if the lower-case filename is a supported image extension (chat/RAG uploads). */
    public static boolean isImageFilename(String lowerName) {
        return isRagImage(lowerName);
    }

    /**
     * Normalizes client-reported MIME (often empty or {@code application/octet-stream} on Windows)
     * using the filename so chat metadata can mark images correctly.
     */
    public static String normalizeUploadMimeType(String lowerFilename, String contentType) {
        String raw = contentType == null || contentType.isBlank()
                ? ""
                : contentType.split(";")[0].trim();
        if (raw.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return raw;
        }
        if (isRagImage(lowerFilename)) {
            return resolveImageMime(lowerFilename, contentType);
        }
        if (raw.isEmpty()) {
            return "application/octet-stream";
        }
        return raw;
    }

    private static String resolveImageMime(String lowerName, String uploadedContentType) {
        if (uploadedContentType != null && uploadedContentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return uploadedContentType.split(";")[0].trim();
        }
        if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerName.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private String extractText(Path path, String lowerName, String uploadedContentType) throws IOException {
        if (isRagImage(lowerName)) {
            byte[] bytes = Files.readAllBytes(path);
            String mime = resolveImageMime(lowerName, uploadedContentType);
            try {
                return bailianService.visionExtractTextForRag(bytes, mime);
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "image to text failed (check vision model / API): " + e.getMessage());
            }
        }
        if (lowerName.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(path.toFile())) {
                return new PDFTextStripper().getText(doc);
            }
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private List<String> splitIntoChunks(String text, int size, int overlap) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        int step = Math.max(1, size - overlap);
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(normalized.length(), start + size);
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                out.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
        }
        return out;
    }

    private int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private List<Double> parseEmbedding(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int n = Math.min(a.size(), b.size());
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < n; i++) {
            double av = a.get(i);
            double bv = b.get(i);
            dot += av * bv;
            na += av * av;
            nb += bv * bv;
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String limitError(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private RagDocumentResponse toResponse(KbDocument doc) {
        RagDocumentResponse r = new RagDocumentResponse();
        r.setId(doc.getId());
        r.setName(doc.getName());
        r.setStatus(doc.getStatus());
        r.setErrorMessage(doc.getErrorMessage());
        r.setCreatedAt(doc.getCreatedAt());
        r.setUpdatedAt(doc.getUpdatedAt());
        return r;
    }

    private record ScoredChunk(KbChunk chunk, double score) {
    }
}
