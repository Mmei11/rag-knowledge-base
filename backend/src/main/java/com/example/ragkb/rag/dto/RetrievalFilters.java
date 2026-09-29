package com.example.ragkb.rag.dto;

import java.time.LocalDate;
import java.util.List;

public record RetrievalFilters(
        List<Long> documentIds,
        LocalDate startDate,
        LocalDate endDate) {

    public RetrievalFilters {
        documentIds = documentIds == null ? List.of() : documentIds.stream().distinct().toList();
    }

    public boolean hasDocumentIds() {
        return !documentIds.isEmpty();
    }
}
