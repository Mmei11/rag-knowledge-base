package com.example.ragkb.document.dto;

import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentStatus;
import java.time.OffsetDateTime;

public record DocumentStatusResponse(
        Long id,
        DocumentStatus status,
        int parseProgress,
        String parseStage,
        String errorMessage,
        OffsetDateTime updatedAt) {

    public static DocumentStatusResponse from(Document document) {
        return new DocumentStatusResponse(
                document.getId(),
                document.getStatus(),
                document.getParseProgress(),
                document.getParseStage(),
                document.getErrorMessage(),
                document.getUpdatedAt());
    }
}
