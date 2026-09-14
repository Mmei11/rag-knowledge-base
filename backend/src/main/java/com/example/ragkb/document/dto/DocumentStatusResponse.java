package com.example.ragkb.document.dto;

import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentStatus;
import java.time.OffsetDateTime;

public record DocumentStatusResponse(
        Long id,
        DocumentStatus status,
        String errorMessage,
        OffsetDateTime updatedAt) {

    public static DocumentStatusResponse from(Document document) {
        return new DocumentStatusResponse(
                document.getId(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getUpdatedAt());
    }
}
