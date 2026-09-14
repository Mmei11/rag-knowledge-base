package com.example.ragkb.rag.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatModelPropertiesTests {

    @Test
    void shouldRejectInvalidTemperature() {
        ChatModelProperties properties = new ChatModelProperties();

        assertThatThrownBy(() -> properties.setTemperature(2.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chat model temperature must be between 0 and 2");
    }
}
