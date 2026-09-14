package com.example.ragkb.knowledgebase.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ragkb.common.exception.GlobalExceptionHandler;
import com.example.ragkb.config.CorsConfig;
import com.example.ragkb.config.SecurityConfig;
import com.example.ragkb.knowledgebase.dto.CreateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseResponse;
import com.example.ragkb.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.service.KnowledgeBaseService;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.security.JwtAuthenticationFilter;
import com.example.ragkb.security.JwtTokenProvider;
import com.example.ragkb.user.entity.UserRole;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = KnowledgeBaseController.class)
@Import({GlobalExceptionHandler.class, CorsConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class KnowledgeBaseControllerTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeBaseService knowledgeBaseService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void shouldCreateKnowledgeBase() throws Exception {
        when(knowledgeBaseService.create(eq(currentUser), any(CreateKnowledgeBaseRequest.class)))
                .thenReturn(response(10L, "Java Notes", "Spring Boot"));

        mockMvc.perform(post("/api/knowledge-bases")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java Notes",
                                  "description": "Spring Boot"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("Java Notes"))
                .andExpect(jsonPath("$.data.documentCount").value(0));
    }

    @Test
    void shouldRejectInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "description": "Spring Boot"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.name").exists());
    }

    @Test
    void shouldListKnowledgeBases() throws Exception {
        when(knowledgeBaseService.listMine(currentUser))
                .thenReturn(List.of(response(10L, "Java Notes", "Spring Boot")));

        mockMvc.perform(get("/api/knowledge-bases")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].name").value("Java Notes"));
    }

    @Test
    void shouldGetKnowledgeBase() throws Exception {
        when(knowledgeBaseService.getMine(currentUser, 10L))
                .thenReturn(response(10L, "Java Notes", "Spring Boot"));

        mockMvc.perform(get("/api/knowledge-bases/10")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("Java Notes"));
    }

    @Test
    void shouldUpdateKnowledgeBase() throws Exception {
        when(knowledgeBaseService.update(eq(currentUser), eq(10L), any(UpdateKnowledgeBaseRequest.class)))
                .thenReturn(response(10L, "Updated Notes", "Updated"));

        mockMvc.perform(put("/api/knowledge-bases/10")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Notes",
                                  "description": "Updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Notes"));
    }

    @Test
    void shouldDeleteKnowledgeBase() throws Exception {
        mockMvc.perform(delete("/api/knowledge-bases/10")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    private UsernamePasswordAuthenticationToken currentUserAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private KnowledgeBaseResponse response(Long id, String name, String description) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-28T10:00:00+08:00");
        return new KnowledgeBaseResponse(id, 1L, name, description, 0, now, now);
    }
}
