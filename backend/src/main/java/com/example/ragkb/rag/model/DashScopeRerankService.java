package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.QwenProperties;
import com.example.ragkb.rag.config.RerankProperties;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "app.rerank", name = "provider", havingValue = "dashscope")
public class DashScopeRerankService implements RerankService {

    private static final Logger log = LoggerFactory.getLogger(DashScopeRerankService.class);

    private final RerankProperties properties;
    private final QwenProperties qwenProperties;
    private final RestClient restClient;

    public DashScopeRerankService(
            RerankProperties properties,
            QwenProperties qwenProperties,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.qwenProperties = qwenProperties;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(properties.getTimeoutMs()))
                .build();
    }

    @Override
    public List<RetrievedChunkResponse> rerank(String query, List<RetrievedChunkResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return candidates;
        }

        long startedAt = System.nanoTime();
        try {
            RerankRequest request = buildRequest(query, candidates);
            JsonNode response = restClient.post()
                    .uri(properties.getEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.resolveApiKey(qwenProperties))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            List<Integer> rankedIndices = parseRankedIndices(response, candidates.size());
            List<RetrievedChunkResponse> reranked = applyRanking(candidates, rankedIndices);
            log.info("rerank completed, provider=dashscope, model={}, candidateCount={}, returnedCount={}, durationMs={}, totalTokens={}",
                    properties.getModel(),
                    candidates.size(),
                    rankedIndices.size(),
                    elapsedMs(startedAt),
                    totalTokens(response));
            return reranked;
        } catch (Exception exception) {
            log.warn("rerank failed, falling back to fused order, provider=dashscope, model={}, candidateCount={}, durationMs={}, error={}",
                    properties.getModel(),
                    candidates.size(),
                    elapsedMs(startedAt),
                    exception.getMessage());
            return candidates;
        }
    }

    RerankRequest buildRequest(String query, List<RetrievedChunkResponse> candidates) {
        List<String> documents = candidates.stream()
                .map(candidate -> truncate(candidate.content(), properties.getMaxDocumentChars()))
                .toList();
        return new RerankRequest(
                properties.getModel(),
                new RerankInput(query, documents),
                new RerankParameters(candidates.size(), false));
    }

    static List<Integer> parseRankedIndices(JsonNode response, int candidateCount) {
        if (response == null) {
            throw new IllegalStateException("rerank response is empty");
        }
        JsonNode results = response.path("output").path("results");
        if (!results.isArray() || results.size() == 0) {
            throw new IllegalStateException("rerank response contains no results");
        }

        Map<Integer, Double> bestScores = new LinkedHashMap<>();
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            double score = result.path("relevance_score").asDouble(Double.NaN);
            if (index < 0 || index >= candidateCount || Double.isNaN(score)) {
                throw new IllegalStateException("rerank response contains invalid result");
            }
            bestScores.merge(index, score, Math::max);
        }
        if (bestScores.isEmpty()) {
            throw new IllegalStateException("rerank response contains no valid results");
        }

        return bestScores.entrySet().stream()
                .sorted((left, right) -> {
                    int scoreComparison = Double.compare(right.getValue(), left.getValue());
                    if (scoreComparison != 0) {
                        return scoreComparison;
                    }
                    return Integer.compare(left.getKey(), right.getKey());
                })
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<RetrievedChunkResponse> applyRanking(
            List<RetrievedChunkResponse> candidates, List<Integer> rankedIndices) {
        List<RetrievedChunkResponse> reranked = new ArrayList<>(candidates.size());
        boolean[] used = new boolean[candidates.size()];
        for (int index : rankedIndices) {
            if (!used[index]) {
                reranked.add(candidates.get(index));
                used[index] = true;
            }
        }
        for (int index = 0; index < candidates.size(); index++) {
            if (!used[index]) {
                reranked.add(candidates.get(index));
            }
        }
        return reranked;
    }

    private static JdkClientHttpRequestFactory requestFactory(int timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }

    private static String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        return content.length() <= maxChars ? content : content.substring(0, maxChars);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static int totalTokens(JsonNode response) {
        return response == null ? 0 : response.path("usage").path("total_tokens").asInt(0);
    }

    record RerankRequest(String model, RerankInput input, RerankParameters parameters) {
    }

    record RerankInput(String query, List<String> documents) {
    }

    record RerankParameters(
            @JsonProperty("top_n") int topN,
            @JsonProperty("return_documents") boolean returnDocuments) {
    }
}
