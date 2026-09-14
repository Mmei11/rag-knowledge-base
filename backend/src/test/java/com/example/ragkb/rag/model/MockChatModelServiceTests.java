package com.example.ragkb.rag.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ragkb.rag.config.ChatModelProperties;
import org.junit.jupiter.api.Test;

class MockChatModelServiceTests {

    @Test
    void shouldGenerateMockAnswerWhenPromptHasContext() {
        ChatModelProperties properties = new ChatModelProperties();
        properties.setModel("mock-chat");
        MockChatModelService service = new MockChatModelService(properties);

        String answer = service.generate("【资料片段】\n[资料 1]\ncontent: Spring Boot notes");

        assertThat(answer).contains("mock-chat");
        assertThat(answer).contains("模拟回答");
    }

    @Test
    void shouldReturnFallbackWhenPromptHasNoContext() {
        MockChatModelService service = new MockChatModelService(new ChatModelProperties());

        String answer = service.generate("【资料片段】\n无");

        assertThat(answer).isEqualTo("根据当前知识库资料，我无法回答这个问题。");
    }

    @Test
    void shouldRejectBlankPrompt() {
        MockChatModelService service = new MockChatModelService(new ChatModelProperties());

        assertThatThrownBy(() -> service.generate(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prompt must not be blank");
    }
}
