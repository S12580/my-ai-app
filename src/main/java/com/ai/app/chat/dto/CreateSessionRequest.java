package com.ai.app.chat.dto;

import jakarta.validation.constraints.Size;

public class CreateSessionRequest {

    @Size(max = 255)
    private String title;

    @Size(max = 64)
    private String model;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
