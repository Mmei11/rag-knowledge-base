package com.example.ragkb.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.config.CorsConfig;
import com.example.ragkb.config.SecurityConfig;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTests.ProtectedController.class)
@Import({
        SecurityConfigTests.ProtectedController.class,
        SecurityConfig.class,
        CorsConfig.class,
        JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class,
        JwtTokenProvider.class
})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:5173",
        "app.jwt.secret=test-secret",
        "app.jwt.expiration-seconds=86400"
})
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void shouldRejectProtectedRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/protected/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    @Test
    void shouldAllowProtectedRequestWithToken() throws Exception {
        User user = new User("alice", "encoded-password", "Alice");
        ReflectionTestUtils.setField(user, "id", 1L);
        String token = jwtTokenProvider.generateToken(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/protected/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("alice"));
    }

    @Test
    void shouldRejectDisabledUserWithValidToken() throws Exception {
        User user = new User("alice", "encoded-password", "Alice");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.disable();
        String token = jwtTokenProvider.generateToken(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/protected/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    @RequestMapping("/api/protected")
    static class ProtectedController {

        @GetMapping("/me")
        ApiResponse<String> me(org.springframework.security.core.Authentication authentication) {
            CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
            return ApiResponse.success(currentUser.username());
        }
    }
}
