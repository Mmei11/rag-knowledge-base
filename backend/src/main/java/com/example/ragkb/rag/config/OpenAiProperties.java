package com.example.ragkb.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.openai")
public class OpenAiProperties {

    private String apiKey;

    private String baseUrl = "https://api.openai.com/v1";

    private String chatModel = "gpt-5-mini";

    private String embeddingModel = "text-embedding-3-small";

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
            throw new IllegalArgumentException("OpenAI base URL must not be blank");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        if (chatModel == null || chatModel.isBlank()) {
            throw new IllegalArgumentException("OpenAI chat model must not be blank");
        }
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("OpenAI embedding model must not be blank");
        }
        this.embeddingModel = embeddingModel;
    }

    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is required when OpenAI provider is enabled");
        }
        return apiKey;
    }
}
