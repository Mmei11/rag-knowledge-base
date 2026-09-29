package com.example.ragkb.rag.model;

import java.util.List;
import java.util.function.Consumer;

public interface EmbeddingService {

    EmbeddingVector embedText(String text);

    default List<EmbeddingVector> embedBatch(List<String> texts) {
        return texts.stream()
                .map(this::embedText)
                .toList();
    }

    default List<EmbeddingVector> embedBatch(
            List<String> texts,
            Consumer<EmbeddingBatchProgress> progressListener) {
        List<EmbeddingVector> embeddings = embedBatch(texts);
        progressListener.accept(new EmbeddingBatchProgress(texts.size(), texts.size(), 0));
        return embeddings;
    }
}
