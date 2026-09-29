package com.example.ragkb.document.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ragkb.common.exception.GlobalExceptionHandler;
import com.example.ragkb.config.CorsConfig;
import com.example.ragkb.config.SecurityConfig;
import com.example.ragkb.document.dto.DocumentParseResponse;
import com.example.ragkb.document.dto.DocumentResponse;
import com.example.ragkb.document.dto.DocumentStatusResponse;
import com.example.ragkb.document.entity.DocumentStatus;
import com.example.ragkb.document.service.DocumentParseService;
import com.example.ragkb.document.service.DocumentService;
import com.example.ragkb.document.task.DocumentProcessTask;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DocumentController.class)
@Import({GlobalExceptionHandler.class, CorsConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class DocumentControllerTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private DocumentParseService documentParseService;

    @MockitoBean
    private DocumentProcessTask documentProcessTask;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void shouldUploadDocument() throws Exception {
        when(documentService.upload(eq(currentUser), eq(10L), any()))
                .thenReturn(response());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Spring Boot notes".getBytes());

        mockMvc.perform(multipart("/api/knowledge-bases/10/documents")
                        .file(file)
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(10))
                .andExpect(jsonPath("$.data.fileName").value("notes.txt"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(documentProcessTask).process(100L);
    }

    @Test
    void shouldRejectUnauthenticatedUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Spring Boot notes".getBytes());

        mockMvc.perform(multipart("/api/knowledge-bases/10/documents")
                        .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void shouldListDocuments() throws Exception {
        when(documentService.listByKnowledgeBase(currentUser, 10L))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/knowledge-bases/10/documents")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].fileName").value("notes.txt"));
    }

    @Test
    void shouldGetDocument() throws Exception {
        when(documentService.getMine(currentUser, 100L)).thenReturn(response());

        mockMvc.perform(get("/api/documents/100")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void shouldGetDocumentStatus() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T20:00:00+08:00");
        when(documentService.getStatus(currentUser, 100L))
                .thenReturn(new DocumentStatusResponse(100L, DocumentStatus.PENDING, 0, "等待解析", null, now));

        mockMvc.perform(get("/api/documents/100/status")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.parseProgress").value(0));
    }

    @Test
    void shouldDeleteDocument() throws Exception {
        mockMvc.perform(delete("/api/documents/100")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(documentService).delete(currentUser, 100L);
    }

    @Test
    void shouldParseDocument() throws Exception {
        when(documentParseService.parse(currentUser, 100L))
                .thenReturn(new DocumentParseResponse(
                        100L,
                        DocumentStatus.COMPLETED,
                        17,
                        1,
                        "Spring Boot notes"));

        mockMvc.perform(post("/api/documents/100/parse")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(100))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.chunkCount").value(1))
                .andExpect(jsonPath("$.data.content").value("Spring Boot notes"));
    }

    @Test
    void shouldReprocessDocument() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T20:00:00+08:00");
        when(documentService.reprocess(currentUser, 100L))
                .thenReturn(new DocumentStatusResponse(100L, DocumentStatus.PENDING, 0, "等待解析", null, now));

        mockMvc.perform(post("/api/documents/100/reprocess")
                        .with(authentication(currentUserAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(documentProcessTask).process(100L);
    }

    private UsernamePasswordAuthenticationToken currentUserAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private DocumentResponse response() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T20:00:00+08:00");
        return new DocumentResponse(
                100L,
                10L,
                1L,
                "notes.txt",
                "txt",
                17L,
                DocumentStatus.PENDING,
                0,
                "等待解析",
                null,
                now,
                now);
    }
}
