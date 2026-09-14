package com.example.ragkb.rag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QwenPropertiesTests {

    @Test
    void shouldNormalizeBaseUrl() {
        QwenProperties properties = new QwenProperties();

        properties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/");

        assertThat(properties.getBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
    }

    @Test
    void shouldRequireApiKeyWhenQwenProviderIsUsed() {
        QwenProperties properties = new QwenProperties();

        assertThatThrownBy(properties::requireApiKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Qwen API key is required when Qwen provider is enabled");
    }

    @Test
    void shouldRejectInvalidEmbeddingBatchSize() {
        QwenProperties properties = new QwenProperties();

        assertThatThrownBy(() -> properties.setEmbeddingBatchSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Qwen embedding batch size must be positive");
    }
}
