package com.example.ragkb.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ragkb.chat.dto.ChatAnswerResponse;
import com.example.ragkb.chat.dto.ChatMessageResponse;
import com.example.ragkb.chat.dto.ChatReferenceResponse;
import com.example.ragkb.chat.dto.ChatSessionResponse;
import com.example.ragkb.chat.entity.ChatRole;
import com.example.ragkb.chat.service.ChatService;
import com.example.ragkb.chat.service.ChatStreamingService;
import com.example.ragkb.common.exception.GlobalExceptionHandler;
import com.example.ragkb.config.CorsConfig;
import com.example.ragkb.config.SecurityConfig;
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

@WebMvcTest(controllers = ChatController.class)
@Import({GlobalExceptionHandler.class, CorsConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class ChatControllerTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private ChatStreamingService chatStreamingService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void shouldCreateSession() throws Exception {
        when(chatService.createSession(eq(currentUser), any()))
                .thenReturn(new ChatSessionResponse(
                        100L,
                        10L,
                        "Java Q&A",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()));

        mockMvc.perform(post("/api/chat/sessions")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": 10,
                                  "title": "Java Q&A"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(10))
                .andExpect(jsonPath("$.data.title").value("Java Q&A"));
    }

    @Test
    void shouldSendMessage() throws Exception {
        ChatReferenceResponse reference = new ChatReferenceResponse(
                300L,
                200L,
                0,
                "Spring Boot notes",
                0.91);
        when(chatService.sendMessage(eq(currentUser), eq(100L), any()))
                .thenReturn(new ChatAnswerResponse(
                        100L,
                        new ChatMessageResponse(500L, 100L, ChatRole.USER, "What is DI?", OffsetDateTime.now(), List.of()),
                        new ChatMessageResponse(501L, 100L, ChatRole.ASSISTANT, "Answer", OffsetDateTime.now(), List.of(reference)),
                        List.of(reference)));

        mockMvc.perform(post("/api/chat/sessions/100/messages")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is DI?",
                                  "promptMode": "KB_FIRST"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(100))
                .andExpect(jsonPath("$.data.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.data.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.references[0].chunkId").value(200))
                .andExpect(jsonPath("$.data.references[0].similarity").value(0.91));
    }

    @Test
    void shouldListSessions() throws Exception {
        when(chatService.listSessions(currentUser))
                .thenReturn(List.of(new ChatSessionResponse(
                        100L,
                        10L,
                        "Java Q&A",
                        OffsetDateTime.now(),
                        OffsetDateTime.now())));

        mockMvc.perform(get("/api/chat/sessions")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].title").value("Java Q&A"));
    }

    @Test
    void shouldRenameAndManageSession() throws Exception {
        when(chatService.renameSession(eq(currentUser), eq(100L), any()))
                .thenReturn(new ChatSessionResponse(100L, 10L, "新标题", OffsetDateTime.now(), OffsetDateTime.now()));
        when(chatService.suggestQuestions(currentUser, 100L)).thenReturn(List.of("推荐问题？"));

        mockMvc.perform(put("/api/chat/sessions/100")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新标题"));
        mockMvc.perform(get("/api/chat/sessions/100/suggestions")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("推荐问题？"));
        mockMvc.perform(delete("/api/chat/sessions/100/messages")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/chat/sessions/100")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidMessage() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/100/messages")
                        .with(authentication(currentUserAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
        mockMvc.perform(get("/api/chat/sessions"))
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
