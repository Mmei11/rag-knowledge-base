package com.example.ragkb.eval;

import java.util.Map;

public record RetrievalEvalGroupSummary(
        String group,
        int caseCount,
        Map<Integer, Double> hitRate,
        Map<Integer, Double> recall,
        Map<Integer, Double> ndcg,
        double mrr) {
}
