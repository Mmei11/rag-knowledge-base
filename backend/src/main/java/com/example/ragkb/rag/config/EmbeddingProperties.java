package com.example.ragkb.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

    private String provider = "mock";

    private int dimension = 384;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("embedding dimension must be positive");
        }
        this.dimension = dimension;
    }
}
