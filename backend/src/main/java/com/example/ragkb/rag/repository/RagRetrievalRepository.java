package com.example.ragkb.rag.repository;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.RetrievalFilters;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.model.PgVectorFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
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
            %s
            ORDER BY c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private static final String KEYWORD_SEARCH_SQL = """
            SELECT c.id, c.document_id, c.chunk_index, c.content, d.file_name, c.page_number, c.pdf_page_number,
                   1 - (c.embedding <=> CAST(? AS vector)) AS similarity,
                   COALESCE(ts_rank_cd(
                       to_tsvector('simple', c.search_text),
                       plainto_tsquery('simple', ?)
                   ), 0) + (%s) AS keyword_score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE c.knowledge_base_id = ?
              AND c.embedding IS NOT NULL
              AND (
                  to_tsvector('simple', c.search_text) @@ plainto_tsquery('simple', ?)
                  OR (%s)
              )
            %s
            ORDER BY keyword_score DESC, c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private static final Pattern SAFE_TERM = Pattern.compile("[\\p{IsHan}a-z0-9]+");

    private final JdbcTemplate jdbcTemplate;

    public RagRetrievalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RetrievedChunkResponse> searchSimilarChunks(
            Long knowledgeBaseId,
            EmbeddingVector queryEmbedding,
            int candidateLimit,
            double similarityThreshold,
            RetrievalFilters filters) {
        String vector = PgVectorFormatter.format(queryEmbedding);
        String sql = SEARCH_SQL.formatted(filterSql(filters));
        List<Object> parameters = new ArrayList<>();
        parameters.add(vector);
        parameters.add(knowledgeBaseId);
        parameters.add(vector);
        parameters.add(similarityThreshold);
        addFilterParameters(parameters, filters);
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

    public List<RetrievedChunkResponse> searchKeywordChunks(
            Long knowledgeBaseId,
            EmbeddingVector queryEmbedding,
            List<String> keywords,
            int candidateLimit,
            RetrievalFilters filters) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        List<String> safeKeywords = keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .filter(keyword -> SAFE_TERM.matcher(keyword).matches())
                .distinct()
                .toList();
        if (safeKeywords.isEmpty()) {
            return List.of();
        }

        String vector = PgVectorFormatter.format(queryEmbedding);
        String keywordQuery = String.join(" ", safeKeywords);
        String likeScore = String.join(" + ", safeKeywords.stream()
                .map(keyword -> "CASE WHEN LOWER(c.content) LIKE ? ESCAPE '!' THEN 1 ELSE 0 END")
                .toList());
        String likeCondition = String.join(" OR ", safeKeywords.stream()
                .map(keyword -> "LOWER(c.content) LIKE ? ESCAPE '!'")
                .toList());
        String sql = KEYWORD_SEARCH_SQL.formatted(likeScore, likeCondition, filterSql(filters));

        List<Object> parameters = new ArrayList<>();
        parameters.add(vector);
        parameters.add(keywordQuery);
        safeKeywords.forEach(keyword -> parameters.add(toLikePattern(keyword)));
        parameters.add(knowledgeBaseId);
        parameters.add(keywordQuery);
        safeKeywords.forEach(keyword -> parameters.add(toLikePattern(keyword)));
        addFilterParameters(parameters, filters);
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

    private String filterSql(RetrievalFilters filters) {
        StringBuilder sql = new StringBuilder();
        if (filters.hasDocumentIds()) {
            sql.append(" AND c.document_id IN (")
                    .append("?,".repeat(filters.documentIds().size()));
            sql.setLength(sql.length() - 1);
            sql.append(')');
        }
        if (filters.startDate() != null) {
            sql.append(" AND d.created_at >= ?");
        }
        if (filters.endDate() != null) {
            sql.append(" AND d.created_at < ?");
        }
        return sql.toString();
    }

    private void addFilterParameters(List<Object> parameters, RetrievalFilters filters) {
        parameters.addAll(filters.documentIds());
        if (filters.startDate() != null) {
            parameters.add(filters.startDate());
        }
        if (filters.endDate() != null) {
            parameters.add(filters.endDate().plusDays(1));
        }
    }
}
