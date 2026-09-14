package com.example.ragkb.document.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTests {

    @Test
    void shouldSplitLongTextWithOverlap() {
        TextChunker chunker = new TextChunker(10, 3);

        List<TextChunk> chunks = chunker.chunk("0123456789abcdefghij");

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).index()).isEqualTo(0);
        assertThat(chunks.get(0).content()).isEqualTo("0123456789");
        assertThat(chunks.get(1).index()).isEqualTo(1);
        assertThat(chunks.get(1).content()).isEqualTo("789abcdefg");
        assertThat(chunks.get(2).index()).isEqualTo(2);
        assertThat(chunks.get(2).content()).isEqualTo("efghij");
    }

    @Test
    void shouldPreferSentenceBoundary() {
        TextChunker chunker = new TextChunker(12, 2);

        List<TextChunk> chunks = chunker.chunk("第一句话。第二句话。第三句话。");

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).content()).endsWith("。");
    }

    @Test
    void shouldReturnEmptyListForBlankText() {
        TextChunker chunker = new TextChunker();

        List<TextChunk> chunks = chunker.chunk("   ");

        assertThat(chunks).isEmpty();
    }
}
