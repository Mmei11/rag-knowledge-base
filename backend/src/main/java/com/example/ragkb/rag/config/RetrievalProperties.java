package com.example.ragkb.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.retrieval")
public class RetrievalProperties {

    private double similarityThreshold = 0.35;

    private int candidateMultiplier = 3;

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        if (similarityThreshold < -1 || similarityThreshold > 1) {
            throw new IllegalArgumentException("retrieval similarity threshold must be between -1 and 1");
        }
        this.similarityThreshold = similarityThreshold;
    }

    public int getCandidateMultiplier() {
        return candidateMultiplier;
    }

    public void setCandidateMultiplier(int candidateMultiplier) {
        if (candidateMultiplier <= 0) {
            throw new IllegalArgumentException("retrieval candidate multiplier must be positive");
        }
        this.candidateMultiplier = candidateMultiplier;
    }
}
