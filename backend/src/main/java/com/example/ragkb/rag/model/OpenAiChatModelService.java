package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.ChatModelProperties;
import com.example.ragkb.rag.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "app.chat-model", name = "provider", havingValue = "openai")
public class OpenAiChatModelService implements ChatModelService {

    private final OpenAiProperties openAiProperties;
    private final ChatModelProperties chatModelProperties;
    private final RestClient restClient;

    public OpenAiChatModelService(
            OpenAiProperties openAiProperties,
            ChatModelProperties chatModelProperties,
            RestClient.Builder restClientBuilder) {
        this.openAiProperties = openAiProperties;
        this.chatModelProperties = chatModelProperties;
        this.restClient = restClientBuilder
                .baseUrl(openAiProperties.getBaseUrl())
                .build();
    }

    @Override
    public String generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }

        JsonNode response = restClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(openAiProperties.requireApiKey()))
                .body(requestBody(prompt))
                .retrieve()
                .body(JsonNode.class);

        return extractOutputText(response);
    }

    private Map<String, Object> requestBody(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiProperties.getChatModel());
        body.put("input", prompt);
        body.put("temperature", chatModelProperties.getTemperature());
        return body;
    }

    static String extractOutputText(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("OpenAI response is empty");
        }

        JsonNode outputText = response.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = response.path("output");
        if (output.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    JsonNode textNode = contentItem.path("text");
                    if (textNode.isTextual() && !textNode.asText().isBlank()) {
                        text.append(textNode.asText());
                    }
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }

        throw new IllegalStateException("OpenAI response does not contain output text");
    }
}
