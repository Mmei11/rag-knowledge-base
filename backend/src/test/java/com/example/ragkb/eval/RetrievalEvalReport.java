package com.example.ragkb.eval;

import java.util.List;
import java.util.Map;

public record RetrievalEvalReport(
        String dataset,
        String description,
        String username,
        Long userId,
        String knowledgeBase,
        Long knowledgeBaseId,
        String embeddingProvider,
        String embeddingModel,
        int topK,
        List<Integer> ks,
        double defaultSimilarityThreshold,
        int caseCount,
        Map<Integer, Double> hitRate,
        Map<Integer, Double> recall,
        Map<Integer, Double> precision,
        Map<Integer, Double> ndcg,
        double mrr,
        long averageLatencyMs,
        List<RetrievalEvalCaseResult> cases) {
}
