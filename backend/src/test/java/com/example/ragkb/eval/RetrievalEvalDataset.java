package com.example.ragkb.eval;

import java.util.List;

public record RetrievalEvalDataset(
        String name,
        String description,
        Long userId,
        String username,
        Long knowledgeBaseId,
        String knowledgeBaseName,
        Integer topK,
        List<Integer> ks,
        List<RetrievalEvalCase> cases) {

    static final List<Integer> DEFAULT_KS = List.of(1, 3, 5, 10);

    public RetrievalEvalDataset {
        name = name == null || name.isBlank() ? "default" : name.trim();
        username = username == null || username.isBlank() ? null : username.trim();
        knowledgeBaseName = knowledgeBaseName == null || knowledgeBaseName.isBlank()
                ? null
                : knowledgeBaseName.trim();

        List<Integer> configuredKs = ks == null || ks.isEmpty() ? DEFAULT_KS : ks;
        if (configuredKs.stream().anyMatch(k -> k == null || k <= 0)) {
            throw new IllegalArgumentException("ks must contain positive integers");
        }
        ks = configuredKs.stream().distinct().sorted().toList();

        cases = cases == null ? List.of() : List.copyOf(cases);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("dataset must contain at least one case");
        }
        if (username == null && userId == null) {
            throw new IllegalArgumentException("dataset must define username or userId");
        }
        if (knowledgeBaseName == null && knowledgeBaseId == null) {
            throw new IllegalArgumentException("dataset must define knowledgeBaseName or knowledgeBaseId");
        }

        int effectiveTopK = topK == null ? ks.getLast() : Math.max(topK, ks.getLast());
        if (effectiveTopK > 20) {
            throw new IllegalArgumentException("topK must not exceed 20");
        }
        topK = effectiveTopK;
    }

    public int effectiveTopK() {
        return topK;
    }
}
