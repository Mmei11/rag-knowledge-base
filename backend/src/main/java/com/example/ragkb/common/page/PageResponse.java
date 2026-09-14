package com.example.ragkb.common.page;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        long totalElements,
        int page,
        int size,
        int totalPages) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> from(Page<T> pageData) {
        return new PageResponse<>(
                pageData.getContent(),
                pageData.getTotalElements(),
                pageData.getNumber() + 1,
                pageData.getSize(),
                pageData.getTotalPages());
    }
}
