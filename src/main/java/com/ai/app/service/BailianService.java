package com.ai.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BailianService {

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${bailian.api.url}")
    private String apiUrl;

    @Value("${bailian.api.key}")
    private String apiKey;

    @Value("${bailian.api.model}")
    private String defaultModel;

    @Value("${bailian.api.max-tokens}")
    private int defaultMaxTokens;

    public BailianService() {
        this.restTemplate = new RestTemplate();
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String chat(String message) {
        return chat(message, defaultModel, defaultMaxTokens);
    }

    public String chat(String message, int maxTokens) {
        return chat(message, defaultModel, maxTokens);
    }

    public String chat(String message, String model) {
        return chat(message, model, defaultMaxTokens);
    }

    public String chat(String message, String model, int maxTokens) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", message);
        messages.add(user);
        return chat(messages, model, maxTokens);
    }

    public String chat(List<Map<String, Object>> messages, String model, int maxTokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) messageObj.get("content");
                    return content != null ? content.trim() : "";
                }
            }
            return "未获取到有效响应";
        } catch (Exception e) {
            return "调用失败: " + e.getMessage();
        }
    }

    public Flux<String> chatStream(String message) {
        return chatStream(message, defaultModel, defaultMaxTokens);
    }

    public Flux<String> chatStream(String message, int maxTokens) {
        return chatStream(message, defaultModel, maxTokens);
    }

    public Flux<String> chatStream(String message, String model) {
        return chatStream(message, model, defaultMaxTokens);
    }

    public Flux<String> chatStream(String message, String model, int maxTokens) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", message);
        messages.add(user);
        return chatStream(messages, model, maxTokens);
    }

    public Flux<String> chatStream(List<Map<String, Object>> messages, String model, int maxTokens) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", true);

        return webClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(120))
                .concatMap(this::splitLines)
                .map(this::normalizeSsePayload)
                .filter(payload -> !payload.isEmpty() && !"[DONE]".equals(payload))
                .map(this::extractContentFromJsonLine)
                .filter(content -> content != null && !content.isEmpty());
    }

    private Flux<String> splitLines(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromArray(chunk.split("\\r?\\n"));
    }

    private String normalizeSsePayload(String line) {
        String t = line.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (t.startsWith("data:")) {
            t = t.substring(5).trim();
        }
        return t;
    }

    private String extractContentFromJsonLine(String data) {
        try {
            JsonNode jsonNode = objectMapper.readTree(data);
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    return delta.get("content").asText();
                }
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }
}
