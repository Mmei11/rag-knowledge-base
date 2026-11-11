package com.example.ragkb.knowledgebase.dto;

import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseAccessRole;
import java.time.OffsetDateTime;

public record KnowledgeBaseResponse(
        Long id,
        Long userId,
        String name,
        String description,
        int documentCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        KnowledgeBaseAccessRole role) {

    public KnowledgeBaseResponse(
            Long id,
            Long userId,
            String name,
            String description,
            int documentCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this(id, userId, name, description, documentCount, createdAt, updatedAt,
                KnowledgeBaseAccessRole.OWNER);
    }

    public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        return from(knowledgeBase, KnowledgeBaseAccessRole.OWNER);
    }

    public static KnowledgeBaseResponse from(
            KnowledgeBase knowledgeBase,
            KnowledgeBaseAccessRole role) {
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getUserId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getDocumentCount(),
                knowledgeBase.getCreatedAt(),
                knowledgeBase.getUpdatedAt(),
                role);
    }
}
