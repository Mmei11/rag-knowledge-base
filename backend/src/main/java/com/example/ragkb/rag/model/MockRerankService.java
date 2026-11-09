package com.example.ragkb.rag.model;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Deterministic lexical-overlap reranker for local pipeline tests only.
 */
@Service
@ConditionalOnProperty(prefix = "app.rerank", name = "provider", havingValue = "mock")
public class MockRerankService implements RerankService {

    @Override
    public List<RetrievedChunkResponse> rerank(String query, List<RetrievedChunkResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return candidates;
        }

        List<ScoredChunk> scored = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            RetrievedChunkResponse candidate = candidates.get(index);
            scored.add(new ScoredChunk(index, score(normalizedQuery, candidate.content()), candidate));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score)
                .reversed()
                .thenComparingInt(ScoredChunk::index));
        return scored.stream().map(ScoredChunk::chunk).toList();
    }

    private double score(String normalizedQuery, String content) {
        if (content == null || content.isBlank()) {
            return 0.0;
        }
        String normalizedContent = normalize(content);
        int matches = 0;
        for (int index = 0; index < normalizedQuery.length(); index++) {
            if (normalizedContent.indexOf(normalizedQuery.charAt(index)) >= 0) {
                matches++;
            }
        }
        return (double) matches / normalizedQuery.length();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record ScoredChunk(int index, double score, RetrievedChunkResponse chunk) {
    }
}
