package com.example.ragkb.eval;

/**
 * Ranking metrics for binary relevance labels.
 *
 * <p>{@code relevant[i]} means the chunk at rank {@code i + 1} is relevant.
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    public static double hitRate(boolean[] relevant, int k) {
        return relevantInTop(relevant, k) > 0 ? 1.0 : 0.0;
    }

    public static double recall(boolean[] relevant, int totalRelevant, int k) {
        if (totalRelevant <= 0) {
            throw new IllegalArgumentException("totalRelevant must be positive");
        }
        return (double) relevantInTop(relevant, k) / totalRelevant;
    }

    public static double precision(boolean[] relevant, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        return (double) relevantInTop(relevant, k) / k;
    }

    public static double reciprocalRank(boolean[] relevant, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        int limit = Math.min(k, relevant.length);
        for (int index = 0; index < limit; index++) {
            if (relevant[index]) {
                return 1.0 / (index + 1);
            }
        }
        return 0.0;
    }

    public static double ndcg(boolean[] relevant, int totalRelevant, int k) {
        if (totalRelevant <= 0) {
            throw new IllegalArgumentException("totalRelevant must be positive");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }

        double dcg = 0.0;
        int limit = Math.min(k, relevant.length);
        for (int index = 0; index < limit; index++) {
            if (relevant[index]) {
                dcg += 1.0 / log2(index + 2);
            }
        }

        double idcg = 0.0;
        int idealHits = Math.min(k, totalRelevant);
        for (int index = 0; index < idealHits; index++) {
            idcg += 1.0 / log2(index + 2);
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    private static int relevantInTop(boolean[] relevant, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        int limit = Math.min(k, relevant.length);
        int count = 0;
        for (int index = 0; index < limit; index++) {
            if (relevant[index]) {
                count++;
            }
        }
        return count;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }
}
