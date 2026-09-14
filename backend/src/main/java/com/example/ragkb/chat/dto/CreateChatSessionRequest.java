package com.example.ragkb.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(
        @NotNull(message = "knowledgeBaseId must not be null")
        Long knowledgeBaseId,

        @Size(max = 200, message = "title must not exceed 200 characters")
        String title) {

    public String normalizedTitle() {
        if (title == null || title.isBlank()) {
            return "New Chat";
        }
        return title.trim();
    }
}
