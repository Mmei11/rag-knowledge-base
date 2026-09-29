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

    @Test
    void shouldUseQwen37EmbeddingDefaults() {
        QwenProperties properties = new QwenProperties();

        assertThat(properties.getEmbeddingModel()).isEqualTo("qwen3.7-text-embedding-flash");
        assertThat(properties.getEmbeddingBatchSize()).isEqualTo(20);
        assertThat(properties.getEmbeddingConcurrency()).isEqualTo(3);
    }

    @Test
    void shouldRejectInvalidEmbeddingConcurrency() {
        QwenProperties properties = new QwenProperties();

        assertThatThrownBy(() -> properties.setEmbeddingConcurrency(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Qwen embedding concurrency must be between 1 and 8");
    }
}
