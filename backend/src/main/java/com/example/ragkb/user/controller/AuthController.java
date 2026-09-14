package com.example.ragkb.user.controller;

import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.user.dto.LoginRequest;
import com.example.ragkb.user.dto.LoginResponse;
import com.example.ragkb.user.dto.RegisterRequest;
import com.example.ragkb.user.dto.UserResponse;
import com.example.ragkb.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
