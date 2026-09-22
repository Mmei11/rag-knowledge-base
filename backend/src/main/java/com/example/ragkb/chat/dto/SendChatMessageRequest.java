package com.example.ragkb.chat.dto;

import com.example.ragkb.rag.prompt.PromptMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 1000, message = "question must not exceed 1000 characters")
        String question,

        @Min(value = 1, message = "topK must be greater than or equal to 1")
        @Max(value = 20, message = "topK must not exceed 20")
        Integer topK,

        @DecimalMin(value = "-1.0", message = "similarityThreshold must not be less than -1")
        @DecimalMax(value = "1.0", message = "similarityThreshold must not be greater than 1")
        Double similarityThreshold,

        PromptMode promptMode) {

    public SendChatMessageRequest(String question, Integer topK) {
        this(question, topK, null, null);
    }

    public SendChatMessageRequest(String question, Integer topK, PromptMode promptMode) {
        this(question, topK, null, promptMode);
    }

    public int normalizedTopK() {
        return topK == null ? 5 : topK;
    }

    public PromptMode normalizedPromptMode() {
        return promptMode == null ? PromptMode.KB_FIRST : promptMode;
    }
}
