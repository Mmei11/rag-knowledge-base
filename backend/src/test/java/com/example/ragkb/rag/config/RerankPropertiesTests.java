package com.example.ragkb.rag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RerankPropertiesTests {

    @Test
    void shouldUseDefaults() {
        RerankProperties properties = new RerankProperties();

        assertThat(properties.getProvider()).isEqualTo("none");
        assertThat(properties.getModel()).isEqualTo("qwen3-rerank");
        assertThat(properties.getCandidateLimit()).isEqualTo(30);
        assertThat(properties.getTimeoutMs()).isEqualTo(3000);
        assertThat(properties.getMaxDocumentChars()).isEqualTo(2000);
        assertThat(properties.effectiveCandidateLimit(5)).isEqualTo(30);
        assertThat(properties.effectiveCandidateLimit(50)).isEqualTo(50);
    }

    @Test
    void shouldNormalizeAndValidateProvider() {
        RerankProperties properties = new RerankProperties();

        properties.setProvider(" DASHSCOPE ");

        assertThat(properties.getProvider()).isEqualTo("dashscope");
        assertThatThrownBy(() -> properties.setProvider("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldValidateLimits() {
        RerankProperties properties = new RerankProperties();

        assertThatThrownBy(() -> properties.setCandidateLimit(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setCandidateLimit(101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setTimeoutMs(99))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxDocumentChars(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldResolveApiKeyFromOwnValueThenQwen() {
        RerankProperties properties = new RerankProperties();
        QwenProperties qwenProperties = new QwenProperties();
        qwenProperties.setApiKey("qwen-key");

        assertThat(properties.resolveApiKey(qwenProperties)).isEqualTo("qwen-key");

        properties.setApiKey("rerank-key");

        assertThat(properties.resolveApiKey(qwenProperties)).isEqualTo("rerank-key");
    }

    @Test
    void shouldRejectMissingApiKey() {
        RerankProperties properties = new RerankProperties();
        QwenProperties qwenProperties = new QwenProperties();

        assertThatThrownBy(() -> properties.resolveApiKey(qwenProperties))
                .isInstanceOf(IllegalStateException.class);
    }
}
