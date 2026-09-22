package com.example.ragkb.rag.dto;

public record RetrievedChunkResponse(
        Long chunkId,
        Long documentId,
        int chunkIndex,
        String content,
        double similarity,
        String fileName,
        Integer pageNumber,
        Integer pdfPageNumber) {

    public RetrievedChunkResponse(
            Long chunkId,
            Long documentId,
            int chunkIndex,
            String content,
            double similarity) {
        this(chunkId, documentId, chunkIndex, content, similarity, null, null, null);
    }

    public RetrievedChunkResponse(
            Long chunkId,
            Long documentId,
            int chunkIndex,
            String content,
            double similarity,
            String fileName,
            Integer pageNumber) {
        this(chunkId, documentId, chunkIndex, content, similarity, fileName, pageNumber, null);
    }
}
