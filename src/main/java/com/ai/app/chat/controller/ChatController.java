package com.ai.app.chat.controller;

import com.ai.app.chat.dto.CreateSessionRequest;
import com.ai.app.chat.dto.MessageResponse;
import com.ai.app.chat.dto.PageResponse;
import com.ai.app.chat.dto.PatchSessionRequest;
import com.ai.app.chat.dto.SendMessageRequest;
import com.ai.app.chat.dto.SendMessageResponse;
import com.ai.app.chat.dto.SessionResponse;
import com.ai.app.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public SessionResponse createSession(@RequestBody @Valid CreateSessionRequest request) {
        return chatService.createSession(request);
    }

    @GetMapping("/sessions")
    public PageResponse<SessionResponse> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return chatService.listSessions(page, size);
    }

    @GetMapping("/sessions/{id}")
    public SessionResponse getSession(@PathVariable("id") Long id) {
        return chatService.getSession(id);
    }

    @PatchMapping("/sessions/{id}")
    public SessionResponse patchSession(
            @PathVariable("id") Long id,
            @RequestBody @Valid PatchSessionRequest request) {
        return chatService.patchSession(id, request);
    }

    @DeleteMapping("/sessions/{id}")
    public void deleteSession(@PathVariable("id") Long id) {
        chatService.deleteSession(id);
    }

    @GetMapping("/sessions/{id}/messages")
    public List<MessageResponse> listMessages(
            @PathVariable("id") Long sessionId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "500") int limit) {
        return chatService.listMessages(sessionId, beforeId, limit);
    }

    @PostMapping("/sessions/{id}/messages")
    public SendMessageResponse sendMessage(
            @PathVariable("id") Long sessionId,
            @RequestBody @Valid SendMessageRequest request) {
        return chatService.sendNonStreaming(sessionId, request);
    }

    @DeleteMapping("/sessions/{id}/messages/{messageId}")
    public void deleteMessage(
            @PathVariable("id") Long sessionId,
            @PathVariable("messageId") Long messageId) {
        chatService.deleteMessage(sessionId, messageId);
    }

    @PostMapping(value = "/sessions/{id}/messages/stream", produces = "application/x-ndjson;charset=UTF-8")
    public Flux<String> streamMessages(
            @PathVariable("id") Long sessionId,
            @RequestBody @Valid SendMessageRequest request) {
        return chatService.streamMessages(sessionId, request);
    }
}
