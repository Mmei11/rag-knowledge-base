package com.example.ragkb.chat.dto;

import com.example.ragkb.chat.entity.ChatSession;
import java.time.OffsetDateTime;

public record ChatSessionResponse(
        Long id,
        Long knowledgeBaseId,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getKnowledgeBaseId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
