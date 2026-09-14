package com.example.ragkb.rag.repository;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.model.PgVectorFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RagRetrievalRepository {

    private static final String SEARCH_SQL = """
            SELECT id, document_id, chunk_index, content, 1 - (embedding <=> CAST(? AS vector)) AS similarity
            FROM document_chunks
            WHERE knowledge_base_id = ?
              AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public RagRetrievalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RetrievedChunkResponse> searchSimilarChunks(
            Long knowledgeBaseId,
            EmbeddingVector queryEmbedding,
            int topK) {
        String vector = PgVectorFormatter.format(queryEmbedding);

        return jdbcTemplate.query(
                SEARCH_SQL,
                (rs, rowNum) -> new RetrievedChunkResponse(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("similarity")),
                vector,
                knowledgeBaseId,
                vector,
                topK);
    }
}
