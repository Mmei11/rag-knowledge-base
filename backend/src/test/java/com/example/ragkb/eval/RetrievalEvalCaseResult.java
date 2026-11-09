package com.example.ragkb.eval;

import java.util.List;
import java.util.Map;

public record RetrievalEvalCaseResult(
        String id,
        String question,
        String questionZh,
        int retrievedCount,
        int totalRelevant,
        int matchedRelevant,
        Map<Integer, Double> hitRate,
        Map<Integer, Double> recall,
        Map<Integer, Double> precision,
        Map<Integer, Double> ndcg,
        double mrr,
        long latencyMs,
        List<String> retrievedChunks,
        List<String> matchedChunks) {
}
