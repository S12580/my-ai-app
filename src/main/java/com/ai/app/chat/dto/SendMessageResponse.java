package com.ai.app.chat.dto;

public class SendMessageResponse {

    private MessageResponse userMessage;
    private MessageResponse assistantMessage;

    public MessageResponse getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(MessageResponse userMessage) {
        this.userMessage = userMessage;
    }

    public MessageResponse getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(MessageResponse assistantMessage) {
        this.assistantMessage = assistantMessage;
    }
}
