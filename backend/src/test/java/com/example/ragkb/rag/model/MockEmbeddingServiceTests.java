package com.example.ragkb.rag.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ragkb.rag.config.EmbeddingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockEmbeddingServiceTests {

    @Test
    void shouldGenerateConfiguredDimension() {
        MockEmbeddingService service = new MockEmbeddingService(properties(8));

        EmbeddingVector vector = service.embedText("Spring Boot notes");

        assertThat(vector.dimension()).isEqualTo(8);
        assertThat(vector.values()).hasSize(8);
    }

    @Test
    void shouldGenerateDeterministicVectorForSameText() {
        MockEmbeddingService service = new MockEmbeddingService(properties(8));

        EmbeddingVector first = service.embedText("Spring Boot notes");
        EmbeddingVector second = service.embedText("Spring Boot notes");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldGenerateDifferentVectorForDifferentText() {
        MockEmbeddingService service = new MockEmbeddingService(properties(8));

        EmbeddingVector first = service.embedText("Spring Boot notes");
        EmbeddingVector second = service.embedText("PostgreSQL pgvector notes");

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void shouldGenerateBatchEmbeddings() {
        MockEmbeddingService service = new MockEmbeddingService(properties(8));

        List<EmbeddingVector> vectors = service.embedBatch(List.of("first chunk", "second chunk"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).isEqualTo(service.embedText("first chunk"));
        assertThat(vectors.get(1)).isEqualTo(service.embedText("second chunk"));
    }

    @Test
    void shouldRejectInvalidDimension() {
        EmbeddingProperties properties = new EmbeddingProperties();

        assertThatThrownBy(() -> properties.setDimension(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding dimension must be positive");
    }

    private EmbeddingProperties properties(int dimension) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(dimension);
        return properties;
    }
}
