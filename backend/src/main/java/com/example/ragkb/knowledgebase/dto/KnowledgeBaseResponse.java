package com.example.ragkb.knowledgebase.dto;

import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import java.time.OffsetDateTime;

public record KnowledgeBaseResponse(
        Long id,
        Long userId,
        String name,
        String description,
        int documentCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getUserId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getDocumentCount(),
                knowledgeBase.getCreatedAt(),
                knowledgeBase.getUpdatedAt());
    }
}
