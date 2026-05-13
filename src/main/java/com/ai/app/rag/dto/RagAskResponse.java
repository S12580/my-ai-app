package com.ai.app.rag.dto;

import java.util.List;

public class RagAskResponse {

    private String answer;
    private List<RagCitation> citations;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<RagCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<RagCitation> citations) {
        this.citations = citations;
    }
}
