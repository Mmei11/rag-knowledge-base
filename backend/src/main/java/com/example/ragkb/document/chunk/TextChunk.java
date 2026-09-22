package com.example.ragkb.document.chunk;

public record TextChunk(
        int index,
        String content,
        int tokenCount,
        Integer pageNumber,
        Integer pdfPageNumber) {

    public TextChunk(int index, String content, int tokenCount) {
        this(index, content, tokenCount, null, null);
    }

    public TextChunk(int index, String content, int tokenCount, Integer pageNumber) {
        this(index, content, tokenCount, pageNumber, pageNumber);
    }
}
