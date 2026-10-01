package com.example.ragkb.chat.service;

import com.example.ragkb.chat.dto.ChatMessageResponse;
import com.example.ragkb.chat.dto.ChatReferenceResponse;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.List;

record ChatStreamPreparation(
        Long sessionId,
        Long userId,
        String prompt,
        List<RetrievedChunkResponse> retrievedChunks,
        ChatMessageResponse userMessage,
        List<ChatReferenceResponse> references) {

    ChatStreamPreparation {
        retrievedChunks = List.copyOf(retrievedChunks);
        references = List.copyOf(references);
    }
}
