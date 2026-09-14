package com.example.ragkb.common.api;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return error(code, message, null);
    }
}
