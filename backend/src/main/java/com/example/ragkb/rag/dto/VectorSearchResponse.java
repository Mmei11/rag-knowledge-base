package com.example.ragkb.rag.dto;

import java.util.List;

public record VectorSearchResponse(
        Long knowledgeBaseId,
        String question,
        int topK,
        List<RetrievedChunkResponse> items) {

    public VectorSearchResponse {
        items = List.copyOf(items);
    }
}
