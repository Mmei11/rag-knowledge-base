package com.example.ragkb.eval;

import java.time.LocalDate;
import java.util.List;

public record RetrievalEvalCase(
        String id,
        String question,
        String questionZh,
        String category,
        String difficulty,
        List<String> relevantChunks,
        List<String> documentFileNames,
        LocalDate startDate,
        LocalDate endDate,
        Double similarityThreshold) {

    public RetrievalEvalCase {
        question = question == null ? "" : question.trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("evaluation case question must not be blank");
        }
        id = id == null || id.isBlank() ? question : id.trim();
        questionZh = questionZh == null || questionZh.isBlank() ? null : questionZh.trim();
        category = category == null || category.isBlank() ? "uncategorized" : category.trim();
        difficulty = difficulty == null || difficulty.isBlank() ? "unspecified" : difficulty.trim();
        relevantChunks = relevantChunks == null ? List.of() : List.copyOf(relevantChunks);
        if (relevantChunks.isEmpty()) {
            throw new IllegalArgumentException("evaluation case must define relevantChunks: " + id);
        }
        documentFileNames = documentFileNames == null ? List.of() : List.copyOf(documentFileNames);
    }

    List<ChunkKey> chunkKeys() {
        return relevantChunks.stream().map(ChunkKey::parse).toList();
    }
}
