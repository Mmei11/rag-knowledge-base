package com.example.ragkb.rag.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiChatModelServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExtractOutputTextShortcut() throws Exception {
        String json = """
                {
                  "output_text": "answer"
                }
                """;

        String text = OpenAiChatModelService.extractOutputText(objectMapper.readTree(json));

        assertThat(text).isEqualTo("answer");
    }

    @Test
    void shouldExtractOutputTextFromContentArray() throws Exception {
        String json = """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "answer from content"
                        }
                      ]
                    }
                  ]
                }
                """;

        String text = OpenAiChatModelService.extractOutputText(objectMapper.readTree(json));

        assertThat(text).isEqualTo("answer from content");
    }

    @Test
    void shouldRejectResponseWithoutText() throws Exception {
        String json = """
                {
                  "output": []
                }
                """;

        assertThatThrownBy(() -> OpenAiChatModelService.extractOutputText(objectMapper.readTree(json)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI response does not contain output text");
    }
}
