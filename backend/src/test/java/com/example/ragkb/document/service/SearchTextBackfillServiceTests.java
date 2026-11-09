package com.example.ragkb.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.common.text.ChineseTextSegmenter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

class SearchTextBackfillServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ChineseTextSegmenter segmenter = mock(ChineseTextSegmenter.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SearchTextBackfillService service =
            new SearchTextBackfillService(jdbcTemplate, segmenter, transactionManager);

    @Test
    void shouldSkipWhenAdvisoryLockIsNotAcquired() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(false);

        SearchTextBackfillService.BackfillResult result = service.backfill(500, false);

        assertThat(result.lockAcquired()).isFalse();
        assertThat(result.scanned()).isZero();
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(), any());
    }

    @Test
    void shouldReturnZeroWhenNothingToBackfill() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(true, true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());

        SearchTextBackfillService.BackfillResult result = service.backfill(500, false);

        assertThat(result.lockAcquired()).isTrue();
        assertThat(result.scanned()).isZero();
        assertThat(result.updated()).isZero();
    }
}
