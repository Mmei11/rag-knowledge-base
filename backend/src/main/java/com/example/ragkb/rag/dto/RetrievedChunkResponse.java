package com.example.ragkb.rag.dto;

public record RetrievedChunkResponse(
        Long chunkId,
        Long documentId,
        int chunkIndex,
        String content,
        double similarity) {
}
