package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.ChatModelProperties;
import com.example.ragkb.rag.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "app.chat-model", name = "provider", havingValue = "openai")
public class OpenAiChatModelService implements ChatModelService {

    private final OpenAiProperties openAiProperties;
    private final ChatModelProperties chatModelProperties;   //聊天模型的通用参数
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiChatModelService(
            OpenAiProperties openAiProperties,
            ChatModelProperties chatModelProperties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.openAiProperties = openAiProperties;
        this.chatModelProperties = chatModelProperties;
        this.objectMapper = objectMapper;
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
                .body(requestBody(prompt, false))
                .retrieve()
                .body(JsonNode.class);

        return extractOutputText(response);
    }

    @Override
    public void generateStream(String prompt, Consumer<String> onDelta) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }

        restClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> headers.setBearerAuth(openAiProperties.requireApiKey()))
                .body(requestBody(prompt, true))
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("OpenAI 流式响应失败：" + response.getStatusCode());
                    }
                    SseDataReader.read(response.getBody(), data -> consumeStreamData(data, onDelta));
                    return null;
                });
    }

    private Map<String, Object> requestBody(String prompt, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiProperties.getChatModel());
        body.put("input", prompt);
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
            throw new IllegalStateException("OpenAI 流式响应格式错误", ex);
        }
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

    static String extractStreamContent(JsonNode response) {
        if (response == null || !"response.output_text.delta".equals(response.path("type").asText())) {
            return "";
        }
        JsonNode delta = response.path("delta");
        return delta.isTextual() ? delta.asText() : "";
    }
}
