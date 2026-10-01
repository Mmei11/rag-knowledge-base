package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.ChatModelProperties;
import com.example.ragkb.rag.config.QwenProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "app.chat-model", name = "provider", havingValue = "qwen")
public class QwenChatModelService implements ChatModelService {

    private final QwenProperties qwenProperties;
    private final ChatModelProperties chatModelProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public QwenChatModelService(
            QwenProperties qwenProperties,
            ChatModelProperties chatModelProperties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.qwenProperties = qwenProperties;
        this.chatModelProperties = chatModelProperties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(qwenProperties.getBaseUrl())
                .build();
    }

    @Override
    public String generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }

        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(qwenProperties.requireApiKey()))
                .body(requestBody(prompt, false))
                .retrieve()
                .body(JsonNode.class);

        return extractMessageContent(response);
    }

    @Override
    public void generateStream(String prompt, Consumer<String> onDelta) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }

        restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> headers.setBearerAuth(qwenProperties.requireApiKey()))
                .body(requestBody(prompt, true))
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("Qwen 流式响应失败：" + response.getStatusCode());
                    }
                    SseDataReader.read(response.getBody(), data -> consumeStreamData(data, onDelta));
                    return null;
                });
    }

    private Map<String, Object> requestBody(String prompt, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", qwenProperties.getChatModel());
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("temperature", chatModelProperties.getTemperature());
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private void consumeStreamData(String data, Consumer<String> onDelta) {
        if ("[DONE]".equals(data)) {
            return;
        }
        try {
            String content = extractStreamContent(objectMapper.readTree(data));
            if (!content.isEmpty()) {
                onDelta.accept(content);
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Qwen 流式响应格式错误", ex);
        }
    }

    static String extractMessageContent(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("Qwen response is empty");
        }

        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("Qwen response does not contain choices");
        }

        JsonNode content = choices.get(0).path("message").path("content");
        if (content.isTextual() && !content.asText().isBlank()) {
            return content.asText();
        }

        throw new IllegalStateException("Qwen response does not contain message content");
    }

    static String extractStreamContent(JsonNode response) {
        if (response == null) {
            return "";
        }
        JsonNode content = response.path("choices").path(0).path("delta").path("content");
        return content.isTextual() ? content.asText() : "";
    }
}
