package com.example.ragkb.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import org.junit.jupiter.api.Test;

class ChunkKeyTests {

    @Test
    void shouldMatchByFileNameAndChunkIndex() {
        ChunkKey key = ChunkKey.parse("食品安全法.pdf#12");

        assertThat(key.matches(chunk("食品安全法.pdf", 12))).isTrue();
        assertThat(key.matches(chunk("食品安全法.pdf", 11))).isFalse();
        assertThat(key.matches(chunk("other.pdf", 12))).isFalse();
        assertThat(key.canonical()).isEqualTo("食品安全法.pdf#12");
    }

    @Test
    void shouldMatchByChunkId() {
        ChunkKey key = ChunkKey.parse("chunkId:42");

        assertThat(key.matches(new RetrievedChunkResponse(42L, 1L, 0, "text", 0.9, "any.pdf", null, null)))
                .isTrue();
        assertThat(key.matches(new RetrievedChunkResponse(43L, 1L, 0, "text", 0.9, "any.pdf", null, null)))
                .isFalse();
        assertThat(key.canonical()).isEqualTo("chunkId:42");
    }

    @Test
    void shouldRejectMalformedLabels() {
        assertThatThrownBy(() -> ChunkKey.parse(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChunkKey.parse("no-separator"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChunkKey.parse("file.pdf#abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChunkKey.parse("chunkId:abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RetrievedChunkResponse chunk(String fileName, int chunkIndex) {
        return new RetrievedChunkResponse(1L, 1L, chunkIndex, "text", 0.9, fileName, null, null);
    }
}
