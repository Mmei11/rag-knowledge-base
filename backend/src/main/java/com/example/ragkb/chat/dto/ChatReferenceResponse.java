package com.example.ragkb.chat.dto;

import com.example.ragkb.chat.repository.MessageReferenceDetail;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;

public record ChatReferenceResponse(
        Long documentId,
        Long chunkId,
        int chunkIndex,
        String content,
        double similarity,
        String fileName,
        Integer pageNumber,
        Integer pdfPageNumber) {

    public ChatReferenceResponse(
            Long documentId,
            Long chunkId,
            int chunkIndex,
            String content,
            double similarity) {
        this(documentId, chunkId, chunkIndex, content, similarity, null, null, null);
    }

    public ChatReferenceResponse(
            Long documentId,
            Long chunkId,
            int chunkIndex,
            String content,
            double similarity,
            String fileName,
            Integer pageNumber) {
        this(documentId, chunkId, chunkIndex, content, similarity, fileName, pageNumber, null);
    }

    public static ChatReferenceResponse from(RetrievedChunkResponse chunk) {
        return new ChatReferenceResponse(
                chunk.documentId(),
                chunk.chunkId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.similarity(),
                chunk.fileName(),
                chunk.pageNumber(),
                chunk.pdfPageNumber());
    }

    public static ChatReferenceResponse from(MessageReferenceDetail reference) {
        return new ChatReferenceResponse(
                reference.getDocumentId(),
                reference.getChunkId(),
                reference.getChunkIndex(),
                reference.getContent(),
                reference.getSimilarity(),
                reference.getFileName(),
                reference.getPageNumber(),
                reference.getPdfPageNumber());
    }
}
