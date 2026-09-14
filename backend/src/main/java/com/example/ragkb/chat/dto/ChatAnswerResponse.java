package com.example.ragkb.chat.dto;

import java.util.List;

public record ChatAnswerResponse(
        Long sessionId,
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        List<ChatReferenceResponse> references) {

    public ChatAnswerResponse {
        references = List.copyOf(references);
    }
}
