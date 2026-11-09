package com.example.ragkb.rag.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rerank")
public class RerankProperties {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("none", "mock", "dashscope");
    private static final int MAX_CANDIDATE_LIMIT = 100;

    private String provider = "none";

    private String endpoint =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private String model = "qwen3-rerank";

    private String apiKey;

    private int candidateLimit = 30;

    private int timeoutMs = 3000;

    private int maxDocumentChars = 2000;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        String normalized = provider == null || provider.isBlank()
                ? "none"
                : provider.trim().toLowerCase();
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported rerank provider: " + provider);
        }
        this.provider = normalized;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("rerank endpoint must not be blank");
        }
        this.endpoint = endpoint.trim();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("rerank model must not be blank");
        }
        this.model = model.trim();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getCandidateLimit() {
        return candidateLimit;
    }

    public void setCandidateLimit(int candidateLimit) {
        if (candidateLimit <= 0 || candidateLimit > MAX_CANDIDATE_LIMIT) {
            throw new IllegalArgumentException(
                    "rerank candidate limit must be between 1 and " + MAX_CANDIDATE_LIMIT);
        }
        this.candidateLimit = candidateLimit;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        if (timeoutMs < 100 || timeoutMs > 30_000) {
            throw new IllegalArgumentException("rerank timeout must be between 100 and 30000 ms");
        }
        this.timeoutMs = timeoutMs;
    }

    public int getMaxDocumentChars() {
        return maxDocumentChars;
    }

    public void setMaxDocumentChars(int maxDocumentChars) {
        if (maxDocumentChars < 100 || maxDocumentChars > 8000) {
            throw new IllegalArgumentException("rerank max document chars must be between 100 and 8000");
        }
        this.maxDocumentChars = maxDocumentChars;
    }

    public int effectiveCandidateLimit(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        return Math.max(topK, Math.min(candidateLimit, MAX_CANDIDATE_LIMIT));
    }

    public String resolveApiKey(QwenProperties qwenProperties) {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        return qwenProperties.requireApiKey();
    }
}
