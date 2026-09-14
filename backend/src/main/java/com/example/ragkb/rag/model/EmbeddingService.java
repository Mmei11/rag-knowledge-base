package com.example.ragkb.rag.model;

import java.util.List;

public interface EmbeddingService {

    EmbeddingVector embedText(String text);

    default List<EmbeddingVector> embedBatch(List<String> texts) {
        return texts.stream()
                .map(this::embedText)
                .toList();
    }
}
