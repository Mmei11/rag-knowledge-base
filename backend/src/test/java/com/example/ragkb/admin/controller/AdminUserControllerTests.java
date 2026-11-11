package com.example.ragkb.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ragkb.admin.service.AdminUserService;
import com.example.ragkb.common.exception.GlobalExceptionHandler;
import com.example.ragkb.common.page.PageRequest;
import com.example.ragkb.common.page.PageResponse;
import com.example.ragkb.config.CorsConfig;
import com.example.ragkb.config.SecurityConfig;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.security.JwtAuthenticationFilter;
import com.example.ragkb.security.JwtTokenProvider;
import com.example.ragkb.security.RestAccessDeniedHandler;
import com.example.ragkb.user.dto.UserResponse;
import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminUserController.class)
@Import({
        GlobalExceptionHandler.class,
        CorsConfig.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class
})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class AdminUserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void shouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void shouldRejectNonAdminRequest() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(authentication(authenticationFor(UserRole.USER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void shouldAllowAdminRequest() throws Exception {
        when(adminUserService.search(any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageResponse<UserResponse>(List.of(), 0, 1, 20, 0));

        mockMvc.perform(get("/api/admin/users")
                        .with(authentication(authenticationFor(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void shouldAllowSuperAdminRequest() throws Exception {
        when(adminUserService.search(any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageResponse<UserResponse>(List.of(), 0, 1, 20, 0));

        mockMvc.perform(get("/api/admin/users")
                        .with(authentication(authenticationFor(UserRole.SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private Authentication authenticationFor(UserRole role) {
        CurrentUser principal = new CurrentUser(1L, "alice", role);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
