package com.ai.app.chat.dto;

/**
 * One file analyzed for chat attachments (not persisted to knowledge base).
 */
public class AnalyzedAttachmentPart {

    /** Server-side id for the raw file on disk until bound to a user message. */
    private String attachmentId;
    private String fileName;
    private String mimeType;
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
