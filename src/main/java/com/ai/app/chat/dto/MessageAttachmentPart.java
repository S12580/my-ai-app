package com.ai.app.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Client-provided attachment text (typically from {@code /attachments/analyze}) to merge into the user message.
 */
public class MessageAttachmentPart {

    @NotBlank
    @Size(max = 64)
    private String attachmentId;

    @NotBlank
    @Size(max = 512)
    private String fileName;

    @Size(max = 128)
    private String mimeType;

    @NotBlank
    @Size(max = 500_000)
    private String extractedText;

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }
}
