package com.example.ragkb.rag.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PgVectorFormatterTests {

    @Test
    void shouldFormatVectorForPgvector() {
        EmbeddingVector vector = new EmbeddingVector(List.of(0.1, -0.25, 1.0));

        String formatted = PgVectorFormatter.format(vector);

        assertThat(formatted).isEqualTo("[0.10000000,-0.25000000,1.00000000]");
    }

    @Test
    void shouldRejectInvalidValue() {
        EmbeddingVector vector = new EmbeddingVector(List.of(0.1, Double.NaN));

        assertThatThrownBy(() -> PgVectorFormatter.format(vector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding vector contains invalid value");
    }
}
