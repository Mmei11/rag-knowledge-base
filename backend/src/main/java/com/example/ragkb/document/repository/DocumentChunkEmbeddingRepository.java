package com.example.ragkb.document.repository;

import com.example.ragkb.document.entity.DocumentChunk;
import com.example.ragkb.rag.config.EmbeddingProperties;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.model.PgVectorFormatter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentChunkEmbeddingRepository {

    private static final String UPDATE_EMBEDDING_SQL = """
            UPDATE document_chunks
            SET embedding = CAST(? AS vector)
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingProperties properties;

    public DocumentChunkEmbeddingRepository(
            JdbcTemplate jdbcTemplate,
            EmbeddingProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public void updateEmbeddings(List<DocumentChunk> chunks, List<EmbeddingVector> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks and embeddings size must match");
        }
        for (int i = 0; i < chunks.size(); i++) {
            validate(chunks.get(i), embeddings.get(i));
        }

        jdbcTemplate.batchUpdate(UPDATE_EMBEDDING_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DocumentChunk chunk = chunks.get(i);
                EmbeddingVector embedding = embeddings.get(i);

                ps.setString(1, PgVectorFormatter.format(embedding));
                ps.setLong(2, chunk.getId());
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    private void validate(DocumentChunk chunk, EmbeddingVector embedding) {
        if (chunk.getId() == null) {
            throw new IllegalArgumentException("document chunk id must not be null");
        }
        if (embedding.dimension() != properties.getDimension()) {
            throw new IllegalArgumentException("embedding dimension does not match configured dimension");
        }
    }
}
