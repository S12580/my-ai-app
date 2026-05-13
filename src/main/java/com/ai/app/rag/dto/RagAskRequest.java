package com.ai.app.rag.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class RagAskRequest {

    @NotBlank
    private String question;

    private Integer topK;

    private List<Long> documentIds;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public List<Long> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds;
    }
}
