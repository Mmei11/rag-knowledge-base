package com.example.ragkb.user.dto;

import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.entity.UserStatus;
import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String username,
        String nickname,
        UserRole role,
        UserStatus status,
        OffsetDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
