package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.ChatModelProperties;
import com.example.ragkb.rag.config.QwenProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
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

    public QwenChatModelService(
            QwenProperties qwenProperties,
            ChatModelProperties chatModelProperties,
            RestClient.Builder restClientBuilder) {
        this.qwenProperties = qwenProperties;
        this.chatModelProperties = chatModelProperties;
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
                .body(requestBody(prompt))
                .retrieve()
                .body(JsonNode.class);

        return extractMessageContent(response);
    }

    private Map<String, Object> requestBody(String prompt) {
        return Map.of(
                "model", qwenProperties.getChatModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", chatModelProperties.getTemperature());
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
}
