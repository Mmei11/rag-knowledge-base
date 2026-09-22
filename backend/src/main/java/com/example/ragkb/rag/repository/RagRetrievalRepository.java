package com.example.ragkb.rag.repository;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.model.PgVectorFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
              AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
            ORDER BY c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private static final String KEYWORD_SEARCH_SQL = """
            SELECT c.id, c.document_id, c.chunk_index, c.content, d.file_name, c.page_number, c.pdf_page_number,
                   1 - (c.embedding <=> CAST(? AS vector)) AS similarity,
                   ts_rank_cd(
                       to_tsvector('simple', c.content),
                       websearch_to_tsquery('simple', ?)
                   ) + (%s) AS keyword_score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE c.knowledge_base_id = ?
              AND c.embedding IS NOT NULL
              AND (
                  to_tsvector('simple', c.content) @@ websearch_to_tsquery('simple', ?)
                  OR (%s)
              )
            ORDER BY keyword_score DESC, c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public RagRetrievalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RetrievedChunkResponse> searchSimilarChunks(
            Long knowledgeBaseId,
            EmbeddingVector queryEmbedding,
            int candidateLimit,
            double similarityThreshold) {
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
                similarityThreshold,
                vector,
                candidateLimit);
    }

    public List<RetrievedChunkResponse> searchKeywordChunks(
            Long knowledgeBaseId,
            EmbeddingVector queryEmbedding,
            List<String> keywords,
            int candidateLimit) {
        if (keywords.isEmpty()) {
            return List.of();
        }

        String vector = PgVectorFormatter.format(queryEmbedding);
        String keywordQuery = String.join(" ", keywords);
        String likeScore = String.join(" + ", keywords.stream()
                .map(keyword -> "CASE WHEN LOWER(c.content) LIKE ? ESCAPE '!' THEN 1 ELSE 0 END")
                .toList());
        String likeCondition = String.join(" OR ", keywords.stream()
                .map(keyword -> "LOWER(c.content) LIKE ? ESCAPE '!'")
                .toList());
        String sql = KEYWORD_SEARCH_SQL.formatted(likeScore, likeCondition);

        List<Object> parameters = new ArrayList<>();
        parameters.add(vector);
        parameters.add(keywordQuery);
        keywords.forEach(keyword -> parameters.add(toLikePattern(keyword)));
        parameters.add(knowledgeBaseId);
        parameters.add(keywordQuery);
        keywords.forEach(keyword -> parameters.add(toLikePattern(keyword)));
        parameters.add(vector);
        parameters.add(candidateLimit);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new RetrievedChunkResponse(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("similarity"),
                        rs.getString("file_name"),
                        rs.getObject("page_number", Integer.class),
                        rs.getObject("pdf_page_number", Integer.class)),
                parameters.toArray());
    }

    private String toLikePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
