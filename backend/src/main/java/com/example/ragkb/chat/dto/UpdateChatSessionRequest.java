package com.example.ragkb.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChatSessionRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title) {
}
