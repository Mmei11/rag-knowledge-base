package com.example.ragkb.rag.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockRerankServiceTests {

    private final MockRerankService service = new MockRerankService();

    @Test
    void shouldRankByLexicalOverlap() {
        RetrievedChunkResponse irrelevant = chunk(1L, 0, "今天天气很好", 0.9);
        RetrievedChunkResponse relevant = chunk(2L, 1, "食品安全要求食品无毒无害", 0.8);

        List<RetrievedChunkResponse> results = service.rerank("食品安全", List.of(irrelevant, relevant));

        assertThat(results).containsExactly(relevant, irrelevant);
    }

    @Test
    void shouldKeepOriginalOrderWhenQueryIsBlank() {
        RetrievedChunkResponse first = chunk(1L, 0, "first", 0.9);
        RetrievedChunkResponse second = chunk(2L, 1, "second", 0.8);

        assertThat(service.rerank(" ", List.of(first, second))).containsExactly(first, second);
    }

    @Test
    void shouldKeepOriginalOrderForEqualScores() {
        RetrievedChunkResponse first = chunk(1L, 0, "alpha", 0.9);
        RetrievedChunkResponse second = chunk(2L, 1, "beta", 0.8);

        assertThat(service.rerank("zzz", List.of(first, second))).containsExactly(first, second);
    }

    @Test
    void shouldHandleEmptyCandidates() {
        assertThat(service.rerank("query", List.of())).isEmpty();
        assertThat(service.rerank("query", null)).isEmpty();
    }

    private RetrievedChunkResponse chunk(Long id, int index, String content, double similarity) {
        return new RetrievedChunkResponse(id, 100L, index, content, similarity);
    }
}
