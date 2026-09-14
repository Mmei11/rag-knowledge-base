package com.example.ragkb.security;

import com.example.ragkb.user.entity.UserRole;

public record CurrentUser(
        Long id,
        String username,
        UserRole role) {
}
