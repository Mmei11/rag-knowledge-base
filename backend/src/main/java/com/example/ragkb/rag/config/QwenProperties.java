package com.example.ragkb.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.qwen")
public class QwenProperties {

    private String apiKey;

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String chatModel = "qwen-plus";

    private String embeddingModel = "text-embedding-v4";

    private int embeddingBatchSize = 10;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Qwen base URL must not be blank");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        if (chatModel == null || chatModel.isBlank()) {
            throw new IllegalArgumentException("Qwen chat model must not be blank");
        }
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("Qwen embedding model must not be blank");
        }
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingBatchSize() {
        return embeddingBatchSize;
    }

    public void setEmbeddingBatchSize(int embeddingBatchSize) {
        if (embeddingBatchSize <= 0) {
            throw new IllegalArgumentException("Qwen embedding batch size must be positive");
        }
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Qwen API key is required when Qwen provider is enabled");
        }
        return apiKey;
    }
}
