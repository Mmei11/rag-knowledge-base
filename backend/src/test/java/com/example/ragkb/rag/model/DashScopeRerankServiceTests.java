package com.example.ragkb.rag.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ragkb.rag.config.QwenProperties;
import com.example.ragkb.rag.config.RerankProperties;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class DashScopeRerankServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseAndSortRankedIndices() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"output":{"results":[
                  {"index":2,"relevance_score":0.9},
                  {"index":0,"relevance_score":0.7},
                  {"index":1,"relevance_score":0.3}
                ]}}
                """);

        assertThat(DashScopeRerankService.parseRankedIndices(response, 3))
                .containsExactly(2, 0, 1);
    }

    @Test
    void shouldKeepHighestScoreForDuplicateIndex() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"output":{"results":[
                  {"index":0,"relevance_score":0.2},
                  {"index":0,"relevance_score":0.8},
                  {"index":1,"relevance_score":0.5}
                ]}}
                """);

        assertThat(DashScopeRerankService.parseRankedIndices(response, 2))
                .containsExactly(0, 1);
    }

    @Test
    void shouldRejectInvalidResults() throws Exception {
        JsonNode missingResults = objectMapper.readTree("{\"output\":{}}");
        JsonNode outOfRange = objectMapper.readTree("""
                {"output":{"results":[{"index":5,"relevance_score":0.9}]}}
                """);
        JsonNode missingScore = objectMapper.readTree("""
                {"output":{"results":[{"index":0}]}}
                """);

        assertThatThrownBy(() -> DashScopeRerankService.parseRankedIndices(null, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DashScopeRerankService.parseRankedIndices(missingResults, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DashScopeRerankService.parseRankedIndices(outOfRange, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DashScopeRerankService.parseRankedIndices(missingScore, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldBuildRequestWithTruncatedDocuments() {
        RerankProperties properties = new RerankProperties();
        properties.setModel("qwen3-rerank");
        properties.setMaxDocumentChars(100);
        DashScopeRerankService service = service(properties);

        DashScopeRerankService.RerankRequest request = service.buildRequest(
                "query",
                List.of(chunk(1L, 0, "a".repeat(150), 0.9), chunk(2L, 1, "abc", 0.8)));

        assertThat(request.model()).isEqualTo("qwen3-rerank");
        assertThat(request.input().query()).isEqualTo("query");
        assertThat(request.input().documents()).containsExactly("a".repeat(100), "abc");
        assertThat(request.parameters().topN()).isEqualTo(2);
        assertThat(request.parameters().returnDocuments()).isFalse();
    }

    @Test
    void shouldShortCircuitEmptyCandidates() {
        DashScopeRerankService service = service(new RerankProperties());

        assertThat(service.rerank("query", List.of())).isEmpty();
        assertThat(service.rerank("query", null)).isEmpty();
    }

    @Test
    void shouldFailOpenWhenEndpointIsUnavailable() {
        RerankProperties properties = new RerankProperties();
        properties.setEndpoint("http://127.0.0.1:1/rerank");
        properties.setTimeoutMs(500);
        QwenProperties qwenProperties = new QwenProperties();
        qwenProperties.setApiKey("test-key");
        DashScopeRerankService service = new DashScopeRerankService(
                properties, qwenProperties, RestClient.builder());

        RetrievedChunkResponse first = chunk(1L, 0, "first", 0.9);
        RetrievedChunkResponse second = chunk(2L, 1, "second", 0.8);

        assertThat(service.rerank("query", List.of(first, second)))
                .containsExactly(first, second);
    }

    private DashScopeRerankService service(RerankProperties properties) {
        QwenProperties qwenProperties = new QwenProperties();
        qwenProperties.setApiKey("test-key");
        return new DashScopeRerankService(properties, qwenProperties, RestClient.builder());
    }

    private RetrievedChunkResponse chunk(Long id, int index, String content, double similarity) {
        return new RetrievedChunkResponse(id, 100L, index, content, similarity);
    }
}
