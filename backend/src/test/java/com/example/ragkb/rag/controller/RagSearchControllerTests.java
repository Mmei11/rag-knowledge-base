package com.example.ragkb.rag.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ragkb.common.exception.GlobalExceptionHandler;
import com.example.ragkb.config.CorsConfig;
import com.example.ragkb.config.SecurityConfig;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.service.RagRetrievalService;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.security.JwtAuthenticationFilter;
import com.example.ragkb.security.RestAccessDeniedHandler;
import com.example.ragkb.security.JwtTokenProvider;
import com.example.ragkb.user.repository.UserRepository;
import com.example.ragkb.user.entity.UserRole;
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

@WebMvcTest(controllers = RagSearchController.class)
@Import({GlobalExceptionHandler.class, CorsConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class RagSearchControllerTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagRetrievalService ragRetrievalService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void shouldSearchVector() throws Exception {
        when(ragRetrievalService.search(eq(currentUser), any()))
                .thenReturn(new VectorSearchResponse(
                        10L,
                        "Spring Boot",
                        3,
                        List.of(new RetrievedChunkResponse(
                                200L,
                                100L,
                                0,
                                "Spring Boot notes",
                                0.87,
                                "spring-boot.pdf",
                                3,
                                5))));

        mockMvc.perform(post("/api/search/vector")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": 10,
                                  "question": "Spring Boot",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(10))
                .andExpect(jsonPath("$.data.question").value("Spring Boot"))
                .andExpect(jsonPath("$.data.items[0].chunkId").value(200))
                .andExpect(jsonPath("$.data.items[0].similarity").value(0.87))
                .andExpect(jsonPath("$.data.items[0].fileName").value("spring-boot.pdf"))
                .andExpect(jsonPath("$.data.items[0].pageNumber").value(3))
                .andExpect(jsonPath("$.data.items[0].pdfPageNumber").value(5));
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/search/vector")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": 10,
                                  "question": " ",
                                  "topK": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.question").exists())
                .andExpect(jsonPath("$.data.topK").exists());
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": 10,
                                  "question": "Spring Boot"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    private UsernamePasswordAuthenticationToken currentUserAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
