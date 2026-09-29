package com.example.ragkb.rag.model;

/**
 * Snapshot emitted after an embedding request batch completes.
 */
public record EmbeddingBatchProgress(int completedItems, int totalItems, long elapsedMillis) {

    public EmbeddingBatchProgress {
        if (completedItems < 0 || totalItems < 0 || completedItems > totalItems || elapsedMillis < 0) {
            throw new IllegalArgumentException("embedding batch progress is invalid");
        }
    }
}
