package com.example.ragkb.admin.dto;

import com.example.ragkb.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "用户状态不能为空") UserStatus status) {
}
