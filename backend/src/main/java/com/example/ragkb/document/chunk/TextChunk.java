package com.example.ragkb.document.chunk;

public record TextChunk(
        int index,
        String content,
        int tokenCount) {
}
