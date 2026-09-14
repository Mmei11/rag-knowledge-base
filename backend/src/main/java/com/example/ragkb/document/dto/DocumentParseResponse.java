package com.example.ragkb.document.dto;

import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentStatus;

public record DocumentParseResponse(
        Long documentId,
        DocumentStatus status,
        int characterCount,
        int chunkCount,
        String content) {

    public static DocumentParseResponse completed(Document document, String content, int chunkCount) {
        return new DocumentParseResponse(
                document.getId(),
                document.getStatus(),
                content.length(),
                chunkCount,
                content);
    }
}
