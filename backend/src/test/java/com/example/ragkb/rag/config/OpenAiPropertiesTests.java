package com.example.ragkb.rag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpenAiPropertiesTests {

    @Test
    void shouldNormalizeBaseUrl() {
        OpenAiProperties properties = new OpenAiProperties();

        properties.setBaseUrl("https://api.openai.com/v1/");

        assertThat(properties.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void shouldRequireApiKeyWhenOpenAiProviderIsUsed() {
        OpenAiProperties properties = new OpenAiProperties();

        assertThatThrownBy(properties::requireApiKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI API key is required when OpenAI provider is enabled");
    }
}
