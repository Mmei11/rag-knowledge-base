package com.example.ragkb.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VectorSearchRequest(
        @NotNull(message = "知识库 ID 不能为空")
        Long knowledgeBaseId,

        @NotBlank(message = "问题不能为空")
        @Size(max = 1000, message = "问题不能超过 1000 个字符")
        String question,

        @Min(value = 1, message = "topK 必须大于等于 1")
        @Max(value = 20, message = "topK 不能超过 20")
        Integer topK) {

    public int normalizedTopK() {
        return topK == null ? 5 : topK;
    }
}
