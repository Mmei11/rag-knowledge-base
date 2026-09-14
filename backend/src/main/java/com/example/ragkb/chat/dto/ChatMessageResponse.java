package com.example.ragkb.chat.dto;

import com.example.ragkb.chat.entity.ChatMessage;
import com.example.ragkb.chat.entity.ChatRole;
import java.time.OffsetDateTime;
import java.util.List;

public record ChatMessageResponse(
        Long id,
        Long sessionId,
        ChatRole role,
        String content,
        OffsetDateTime createdAt,
        List<ChatReferenceResponse> references) {

    public ChatMessageResponse {
        references = List.copyOf(references);
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return from(message, List.of());
    }

    public static ChatMessageResponse from(ChatMessage message, List<ChatReferenceResponse> references) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                references);
    }
}
