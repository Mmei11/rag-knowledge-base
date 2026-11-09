package com.example.ragkb.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RetrievalMetricsTests {

    @Test
    void shouldScorePerfectRanking() {
        boolean[] relevant = {true, true, false};

        assertThat(RetrievalMetrics.hitRate(relevant, 1)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.recall(relevant, 2, 1)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.precision(relevant, 1)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.reciprocalRank(relevant, 3)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.ndcg(relevant, 2, 3)).isEqualTo(1.0);
    }

    @Test
    void shouldScoreRelevantHitAtThirdPosition() {
        boolean[] relevant = {false, false, true};

        assertThat(RetrievalMetrics.hitRate(relevant, 2)).isZero();
        assertThat(RetrievalMetrics.hitRate(relevant, 3)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.recall(relevant, 1, 3)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.precision(relevant, 3)).isEqualTo(1.0 / 3.0);
        assertThat(RetrievalMetrics.reciprocalRank(relevant, 3)).isEqualTo(1.0 / 3.0);
        assertThat(RetrievalMetrics.ndcg(relevant, 1, 3)).isEqualTo(0.5);
    }

    @Test
    void shouldAccountForMultipleRelevantChunks() {
        boolean[] relevant = {false, true, false, true};

        assertThat(RetrievalMetrics.recall(relevant, 2, 2)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.recall(relevant, 2, 4)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.ndcg(relevant, 2, 2))
                .isCloseTo(0.3869, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(RetrievalMetrics.reciprocalRank(relevant, 4)).isEqualTo(0.5);
    }

    @Test
    void shouldReturnZeroWhenNothingWasRetrieved() {
        boolean[] relevant = {};

        assertThat(RetrievalMetrics.hitRate(relevant, 5)).isZero();
        assertThat(RetrievalMetrics.recall(relevant, 2, 5)).isZero();
        assertThat(RetrievalMetrics.precision(relevant, 5)).isZero();
        assertThat(RetrievalMetrics.reciprocalRank(relevant, 5)).isZero();
        assertThat(RetrievalMetrics.ndcg(relevant, 2, 5)).isZero();
    }

    @Test
    void shouldCountOnlyTopKItems() {
        boolean[] relevant = {false, false, false, true};

        assertThat(RetrievalMetrics.hitRate(relevant, 3)).isZero();
        assertThat(RetrievalMetrics.hitRate(relevant, 4)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.recall(relevant, 1, 3)).isZero();
        assertThat(RetrievalMetrics.recall(relevant, 1, 4)).isEqualTo(1.0);
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThatThrownBy(() -> RetrievalMetrics.hitRate(new boolean[] {true}, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetrievalMetrics.recall(new boolean[] {true}, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetrievalMetrics.ndcg(new boolean[] {true}, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
