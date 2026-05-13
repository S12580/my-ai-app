package com.ai.app.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public class SendMessageRequest {

    @Size(max = 200_000)
    private String content = "";

    @Valid
    @Size(max = 3)
    private List<MessageAttachmentPart> attachments;

    @Size(max = 64)
    private String model;

    private Integer maxTokens;

    private Boolean useRag;

    private List<Long> ragDocumentIds;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getUseRag() {
        return useRag;
    }

    public void setUseRag(Boolean useRag) {
        this.useRag = useRag;
    }

    public List<Long> getRagDocumentIds() {
        return ragDocumentIds;
    }

    public void setRagDocumentIds(List<Long> ragDocumentIds) {
        this.ragDocumentIds = ragDocumentIds;
    }

    public List<MessageAttachmentPart> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<MessageAttachmentPart> attachments) {
        this.attachments = attachments;
    }
}
