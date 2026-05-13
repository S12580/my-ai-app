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
import java.util.Base64;
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

    @Value("${bailian.api.embedding-url:}")
    private String embeddingUrl;

    @Value("${bailian.api.embedding-model:text-embedding-v4}")
    private String embeddingModel;

    /** Vision model for image → text (RAG ingest). DashScope compatible OpenAI API. */
    @Value("${bailian.api.vision-model:qwen-vl-plus}")
    private String visionModel;

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

    public List<Double> embedText(String text) {
        String input = text == null ? "" : text.trim();
        if (input.isEmpty()) {
            return List.of();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", embeddingModel);
        requestBody.put("input", input);

        String url = resolveEmbeddingUrl();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (response.getBody() == null) {
                return List.of();
            }
            Object dataObj = response.getBody().get("data");
            if (dataObj instanceof List<?> dataList && !dataList.isEmpty()) {
                Object first = dataList.get(0);
                if (first instanceof Map<?, ?> map) {
                    Object emb = map.get("embedding");
                    if (emb instanceof List<?> embList) {
                        List<Double> out = new ArrayList<>(embList.size());
                        for (Object item : embList) {
                            if (item instanceof Number n) {
                                out.add(n.doubleValue());
                            }
                        }
                        return out;
                    }
                }
            }
            return List.of();
        } catch (Exception e) {
            throw new IllegalStateException("embedding call failed: " + e.getMessage(), e);
        }
    }

    private String resolveEmbeddingUrl() {
        if (embeddingUrl != null && !embeddingUrl.isBlank()) {
            return embeddingUrl.trim();
        }
        if (apiUrl != null && apiUrl.contains("/chat/completions")) {
            return apiUrl.replace("/chat/completions", "/embeddings");
        }
        return apiUrl;
    }

    /**
     * Uses the multimodal chat API to turn an image into plain text for chunking and embedding.
     * Requires a vision-capable model (see {@code bailian.api.vision-model}).
     */
    public String visionExtractTextForRag(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalStateException("empty image bytes");
        }
        String mime = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType;
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        List<Map<String, Object>> contentParts = new ArrayList<>();
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new HashMap<>();
        imageUrl.put("url", dataUrl);
        imagePart.put("image_url", imageUrl);
        contentParts.add(imagePart);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text",
                "请识别并转写图中所有可见文字（尽量保持行序）。若无文字，请简要描述图像中的关键物体与场景，便于后续检索问答。");
        contentParts.add(textPart);

        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", contentParts);

        List<Map<String, Object>> messages = List.of(user);
        return chatVisionOrThrow(messages, visionModel, Math.min(4096, Math.max(defaultMaxTokens, 2048)));
    }

    @SuppressWarnings("unchecked")
    private String chatVisionOrThrow(List<Map<String, Object>> messages, String model, int maxTokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
            if (response.getBody() == null) {
                throw new IllegalStateException("vision API returned empty body");
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("vision API returned no choices");
            }
            Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
            if (messageObj == null) {
                throw new IllegalStateException("vision API returned no message");
            }
            Object contentObj = messageObj.get("content");
            String text = stringifyVisionContent(contentObj);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("vision API returned empty content");
            }
            return text.trim();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("vision call failed: " + e.getMessage(), e);
        }
    }

    private static String stringifyVisionContent(Object contentObj) {
        if (contentObj == null) {
            return "";
        }
        if (contentObj instanceof String s) {
            return s;
        }
        if (contentObj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object t = m.get("type");
                    Object txt = m.get("text");
                    if ("text".equals(t) && txt != null) {
                        if (!sb.isEmpty()) {
                            sb.append('\n');
                        }
                        sb.append(txt.toString());
                    }
                }
            }
            return sb.toString();
        }
        return contentObj.toString();
    }
}
