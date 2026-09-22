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
            SELECT c.id, c.document_id, c.chunk_index, c.content, d.file_name, c.page_number, c.pdf_page_number,
                   1 - (c.embedding <=> CAST(? AS vector)) AS similarity
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE c.knowledge_base_id = ?
              AND c.embedding IS NOT NULL
            ORDER BY c.embedding <=> CAST(? AS vector)
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
                        rs.getDouble("similarity"),
                        rs.getString("file_name"),
                        rs.getObject("page_number", Integer.class),
                        rs.getObject("pdf_page_number", Integer.class)),
                vector,
                knowledgeBaseId,
                vector,
                topK);
    }
}
