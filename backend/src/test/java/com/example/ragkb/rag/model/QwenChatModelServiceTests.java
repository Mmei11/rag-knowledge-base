package com.example.ragkb.rag.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QwenChatModelServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExtractMessageContent() throws Exception {
        String json = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "answer"
                      }
                    }
                  ]
                }
                """;

        String text = QwenChatModelService.extractMessageContent(objectMapper.readTree(json));

        assertThat(text).isEqualTo("answer");
    }

    @Test
    void shouldRejectResponseWithoutChoices() throws Exception {
        String json = """
                {
                  "choices": []
                }
                """;

        assertThatThrownBy(() -> QwenChatModelService.extractMessageContent(objectMapper.readTree(json)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Qwen response does not contain choices");
    }

    @Test
    void shouldRejectResponseWithoutMessageContent() throws Exception {
        String json = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant"
                      }
                    }
                  ]
                }
                """;

        assertThatThrownBy(() -> QwenChatModelService.extractMessageContent(objectMapper.readTree(json)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Qwen response does not contain message content");
    }

    @Test
    void shouldExtractStreamDeltaContent() throws Exception {
        String json = """
                {
                  "choices": [
                    {
                      "delta": {
                        "content": "stream answer"
                      }
                    }
                  ]
                }
                """;

        assertThat(QwenChatModelService.extractStreamContent(objectMapper.readTree(json)))
                .isEqualTo("stream answer");
        assertThat(QwenChatModelService.extractStreamContent(objectMapper.readTree("{\"choices\":[{}]}")))
                .isEmpty();
    }
}
