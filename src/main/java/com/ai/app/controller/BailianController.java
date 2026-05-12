package com.ai.app.controller;

import com.ai.app.service.BailianService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/bailian")
public class BailianController {

    @Autowired
    private BailianService bailianService;

    @Value("${bailian.api.model}")
    private String defaultModel;

    @Value("${bailian.api.max-tokens}")
    private int defaultMaxTokens;

    @GetMapping("/chat")
    public String chat(
            @RequestParam(defaultValue = "你好，请介绍一下你自己") String message,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "0") int maxTokens) {
        
        String actualModel = model.isEmpty() ? defaultModel : model;
        int actualMaxTokens = maxTokens == 0 ? defaultMaxTokens : maxTokens;
        
        return bailianService.chat(message, actualModel, actualMaxTokens);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @RequestParam(defaultValue = "你好，请介绍一下你自己") String message,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "0") int maxTokens) {
        
        String actualModel = model.isEmpty() ? defaultModel : model;
        int actualMaxTokens = maxTokens == 0 ? defaultMaxTokens : maxTokens;
        
        return bailianService.chatStream(message, actualModel, actualMaxTokens);
    }
}