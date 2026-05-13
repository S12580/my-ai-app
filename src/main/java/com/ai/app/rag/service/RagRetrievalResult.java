package com.ai.app.rag.service;

import com.ai.app.rag.dto.RagCitation;

import java.util.List;

public class RagRetrievalResult {

    private String contextText;
    private List<RagCitation> citations;

    public String getContextText() {
        return contextText;
    }

    public void setContextText(String contextText) {
        this.contextText = contextText;
    }

    public List<RagCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<RagCitation> citations) {
        this.citations = citations;
    }
}
