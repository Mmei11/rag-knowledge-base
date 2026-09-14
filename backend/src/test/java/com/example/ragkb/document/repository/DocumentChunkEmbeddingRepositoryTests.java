package com.example.ragkb.document.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import com.example.ragkb.document.entity.DocumentChunk;
import com.example.ragkb.rag.config.EmbeddingProperties;
import com.example.ragkb.rag.model.EmbeddingVector;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentChunkEmbeddingRepositoryTests {

    @Test
    void shouldBatchUpdateEmbeddings() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        DocumentChunkEmbeddingRepository repository = new DocumentChunkEmbeddingRepository(jdbcTemplate, properties(2));
        DocumentChunk chunk = chunk(100L);

        repository.updateEmbeddings(List.of(chunk), List.of(new EmbeddingVector(List.of(0.1, -0.2))));

        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());

        PreparedStatement ps = org.mockito.Mockito.mock(PreparedStatement.class);
        BatchPreparedStatementSetter setter = setterCaptor.getValue();
        setter.setValues(ps, 0);

        verify(ps).setString(1, "[0.10000000,-0.20000000]");
        verify(ps).setLong(2, 100L);
    }

    @Test
    void shouldRejectMismatchedDimension() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        DocumentChunkEmbeddingRepository repository = new DocumentChunkEmbeddingRepository(jdbcTemplate, properties(3));

        assertThatThrownBy(() -> repository.updateEmbeddings(
                List.of(chunk(100L)),
                List.of(new EmbeddingVector(List.of(0.1, -0.2)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding dimension does not match configured dimension");
    }

    private DocumentChunk chunk(Long id) {
        DocumentChunk chunk = new DocumentChunk(10L, 1L, 0, "Spring Boot notes", 3, null);
        ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }

    private EmbeddingProperties properties(int dimension) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(dimension);
        return properties;
    }
}
