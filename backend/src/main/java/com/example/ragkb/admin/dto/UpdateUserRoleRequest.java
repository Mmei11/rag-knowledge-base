package com.example.ragkb.admin.dto;

import com.example.ragkb.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "用户角色不能为空") UserRole role) {
}
