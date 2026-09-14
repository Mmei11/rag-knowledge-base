package com.example.ragkb.user.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserResponse user) {

    public static LoginResponse bearer(String token, long expiresIn, UserResponse user) {
        return new LoginResponse(token, "Bearer", expiresIn, user);
    }
}
