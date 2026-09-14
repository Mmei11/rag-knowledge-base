package com.example.ragkb.chat.dto;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;

public record ChatReferenceResponse(
        Long documentId,
        Long chunkId,
        int chunkIndex,
        String content,
        double similarity) {

    public static ChatReferenceResponse from(RetrievedChunkResponse chunk) {
        return new ChatReferenceResponse(
                chunk.documentId(),
                chunk.chunkId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.similarity());
    }
}
