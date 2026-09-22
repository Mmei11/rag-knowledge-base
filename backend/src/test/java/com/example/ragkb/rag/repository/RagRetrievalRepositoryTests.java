package com.example.ragkb.rag.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.model.EmbeddingVector;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RagRetrievalRepositoryTests {

    @Test
    void shouldQuerySimilarChunks() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        RagRetrievalRepository repository = new RagRetrievalRepository(jdbcTemplate);
        RetrievedChunkResponse item = new RetrievedChunkResponse(200L, 100L, 0, "Spring Boot notes", 0.87);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of(item));

        List<RetrievedChunkResponse> items = repository.searchSimilarChunks(
                10L,
                new EmbeddingVector(List.of(0.1, -0.2)),
                3);

        assertThat(items).containsExactly(item);
        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                eq("[0.10000000,-0.20000000]"),
                eq(10L),
                eq("[0.10000000,-0.20000000]"),
                eq(3));
    }

    @Test
    void shouldMapRow() throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(200L);
        when(rs.getLong("document_id")).thenReturn(100L);
        when(rs.getInt("chunk_index")).thenReturn(0);
        when(rs.getString("content")).thenReturn("Spring Boot notes");
        when(rs.getDouble("similarity")).thenReturn(0.87);
        when(rs.getString("file_name")).thenReturn("spring-boot.pdf");
        when(rs.getObject("page_number", Integer.class)).thenReturn(3);
        when(rs.getObject("pdf_page_number", Integer.class)).thenReturn(5);

        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        RagRetrievalRepository repository = new RagRetrievalRepository(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<RetrievedChunkResponse> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<RetrievedChunkResponse> items = repository.searchSimilarChunks(
                10L,
                new EmbeddingVector(List.of(0.1, -0.2)),
                3);

        assertThat(items.getFirst().chunkId()).isEqualTo(200L);
        assertThat(items.getFirst().documentId()).isEqualTo(100L);
        assertThat(items.getFirst().similarity()).isEqualTo(0.87);
        assertThat(items.getFirst().fileName()).isEqualTo("spring-boot.pdf");
        assertThat(items.getFirst().pageNumber()).isEqualTo(3);
        assertThat(items.getFirst().pdfPageNumber()).isEqualTo(5);
    }
}
