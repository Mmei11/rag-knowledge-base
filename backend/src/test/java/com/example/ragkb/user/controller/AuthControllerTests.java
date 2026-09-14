package com.example.ragkb.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ragkb.common.exception.GlobalExceptionHandler;
import com.example.ragkb.config.CorsConfig;
import com.example.ragkb.config.SecurityConfig;
import com.example.ragkb.security.JwtAuthenticationFilter;
import com.example.ragkb.security.JwtTokenProvider;
import com.example.ragkb.user.dto.LoginRequest;
import com.example.ragkb.user.dto.LoginResponse;
import com.example.ragkb.user.dto.RegisterRequest;
import com.example.ragkb.user.dto.UserResponse;
import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.entity.UserStatus;
import com.example.ragkb.user.service.AuthService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, CorsConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void shouldRegisterUser() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(
                        1L,
                        "alice",
                        "Alice",
                        UserRole.USER,
                        UserStatus.ENABLED,
                        OffsetDateTime.parse("2026-07-27T10:00:00+08:00")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Password123",
                                  "nickname": "Alice"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectInvalidRegisterRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "a!",
                                  "password": "123",
                                  "nickname": "Alice"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数错误"))
                .andExpect(jsonPath("$.data.username").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    void shouldLoginUser() throws Exception {
        UserResponse user = new UserResponse(
                1L,
                "alice",
                "Alice",
                UserRole.USER,
                UserStatus.ENABLED,
                OffsetDateTime.parse("2026-07-27T10:00:00+08:00"));
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(LoginResponse.bearer("jwt-token", 86400L, user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(86400))
                .andExpect(jsonPath("$.data.user.username").value("alice"));
    }
}
