package com.ai.app.chat.service;

import com.ai.app.chat.domain.ChatMessage;
import com.ai.app.chat.mapper.ChatMessageMapper;
import com.ai.app.chat.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatRepositoryService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ChatRepositoryService(ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Transactional
    public ChatMessage insertUserMessage(Long sessionId, String content) {
        return insertUserBody(sessionId, content);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage insertUserMessageNewTx(Long sessionId, String content) {
        return insertUserBody(sessionId, content);
    }

    private ChatMessage insertUserBody(Long sessionId, String content) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setRole("user");
        m.setContent(content);
        chatMessageMapper.insert(m);
        chatSessionMapper.touchUpdatedAt(sessionId);
        return m;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long insertAssistantMessage(Long sessionId, String content, String metaJson) {
        return insertAssistantBody(sessionId, content, metaJson);
    }

    @Transactional
    public Long insertAssistantMessageSameTx(Long sessionId, String content, String metaJson) {
        return insertAssistantBody(sessionId, content, metaJson);
    }

    private Long insertAssistantBody(Long sessionId, String content, String metaJson) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setRole("assistant");
        m.setContent(content);
        m.setMetaJson(metaJson);
        chatMessageMapper.insert(m);
        chatSessionMapper.touchUpdatedAt(sessionId);
        return m.getId();
    }
}
