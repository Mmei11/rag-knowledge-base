package com.example.ragkb.document.service;

import com.example.ragkb.common.text.ChineseTextSegmenter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SearchTextBackfillService {

    private static final Logger log = LoggerFactory.getLogger(SearchTextBackfillService.class);
    private static final long ADVISORY_LOCK_KEY = 918273645L;

    private final JdbcTemplate jdbcTemplate;
    private final ChineseTextSegmenter textSegmenter;
    private final TransactionTemplate transactionTemplate;

    public SearchTextBackfillService(
            JdbcTemplate jdbcTemplate,
            ChineseTextSegmenter textSegmenter,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.textSegmenter = textSegmenter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public BackfillResult backfill(int batchSize, boolean dryRun) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("search_text backfill skipped because another instance holds the lock");
            return new BackfillResult(0, 0, 0, 0, false);
        }

        long startedAt = System.nanoTime();
        try {
            long total = countRemaining();
            long scanned = 0;
            long updated = 0;
            long lastId = 0;
            while (true) {
                List<ChunkRow> rows = jdbcTemplate.query(
                        """
                        SELECT id, content
                        FROM document_chunks
                        WHERE id > ?
                          AND (search_text IS NULL OR BTRIM(search_text) = '')
                        ORDER BY id
                        LIMIT ?
                        """,
                        (rs, rowNum) -> new ChunkRow(rs.getLong("id"), rs.getString("content")),
                        lastId,
                        batchSize);
                if (rows.isEmpty()) {
                    break;
                }
                lastId = rows.get(rows.size() - 1).id();
                scanned += rows.size();
                if (!dryRun) {
                    List<Object[]> updates = rows.stream()
                            .map(row -> new Object[]{
                                    textSegmenter.buildSearchText(row.content()),
                                    row.id()})
                            .toList();
                    transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(
                            "UPDATE document_chunks SET search_text = ? WHERE id = ? "
                                    + "AND (search_text IS NULL OR BTRIM(search_text) = '')",
                            updates));
                }
                updated += rows.size();
                log.info("search_text backfill progress, processed={}, total={}, percent={}%, dryRun={}",
                        scanned, total, percentage(scanned, total), dryRun);
            }
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("search_text backfill completed, scanned={}, updated={}, elapsedMs={}, dryRun={}",
                    scanned, updated, elapsedMs, dryRun);
            return new BackfillResult(scanned, updated, 0, elapsedMs, true);
        } finally {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_unlock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        }
    }

    private long countRemaining() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunks "
                        + "WHERE search_text IS NULL OR BTRIM(search_text) = ''",
                Long.class);
        return count == null ? 0 : count;
    }

    private int percentage(long processed, long total) {
        return total == 0 ? 100 : (int) Math.min(100, processed * 100 / total);
    }

    private record ChunkRow(long id, String content) {
    }

    public record BackfillResult(
            long scanned,
            long updated,
            long skipped,
            long elapsedMs,
            boolean lockAcquired) {
    }
}
