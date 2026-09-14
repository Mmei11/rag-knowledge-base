package com.example.ragkb.document.dto;

import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentStatus;
import java.time.OffsetDateTime;

public record DocumentResponse(
        Long id,
        Long knowledgeBaseId,
        Long userId,
        String fileName,
        String fileType,
        long fileSize,
        DocumentStatus status,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getUserId(),
                document.getFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
