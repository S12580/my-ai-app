package com.ai.app.rag.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class RagCitation {

    private Long documentId;
    private String documentName;
    private Integer chunkIndex;
    private double score;
    private String snippet;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    /**
     * Builds the trailing "参考来源" block: one line per document (first-seen order),
     * chunk indices merged; at most {@code maxDocuments} lines.
     */
    public static String formatReferenceAppendix(List<RagCitation> citations, int maxDocuments) {
        if (citations == null || citations.isEmpty() || maxDocuments <= 0) {
            return "";
        }
        record Agg(String name, TreeSet<Integer> chunks) {
        }
        Map<Long, Agg> byDoc = new LinkedHashMap<>();
        for (RagCitation c : citations) {
            Long id = c.getDocumentId();
            if (id == null) {
                continue;
            }
            String raw = c.getDocumentName();
            final String nameForDoc = (raw == null || raw.isBlank()) ? "doc-" + id : raw;
            Agg agg = byDoc.computeIfAbsent(id, k -> new Agg(nameForDoc, new TreeSet<>()));
            if (c.getChunkIndex() != null) {
                agg.chunks().add(c.getChunkIndex());
            }
        }
        if (byDoc.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n参考来源:");
        int idx = 1;
        int lines = 0;
        for (Agg agg : byDoc.values()) {
            if (lines >= maxDocuments) {
                break;
            }
            sb.append("\n").append(idx++).append(". ").append(agg.name()).append(" — ");
            if (agg.chunks().isEmpty()) {
                sb.append("#?");
            } else {
                boolean first = true;
                for (Integer ch : agg.chunks()) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    sb.append("#").append(ch);
                }
            }
            lines++;
        }
        return sb.toString();
    }
}
