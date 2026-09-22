package com.example.ragkb.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
        Integer topK,

        @DecimalMin(value = "-1.0", message = "相似度阈值不能小于 -1")
        @DecimalMax(value = "1.0", message = "相似度阈值不能大于 1")
        Double similarityThreshold) {

    public VectorSearchRequest(Long knowledgeBaseId, String question, Integer topK) {
        this(knowledgeBaseId, question, topK, null);
    }

    public int normalizedTopK() {
        return topK == null ? 5 : topK;
    }
}
