package com.example.ragkb.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.document.config.DocumentStorageProperties;
import com.example.ragkb.document.dto.DocumentResponse;
import com.example.ragkb.document.dto.DocumentStatusResponse;
import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentStatus;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.UserRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @TempDir
    private Path tempDir;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Test
    void shouldUploadDocumentAndSaveMetadata() {
        KnowledgeBase knowledgeBase = knowledgeBase(10L, 1L);
        DocumentService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                " notes.txt ",
                "text/plain",
                "Spring Boot notes".getBytes());

        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.saveAndFlush(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.upload(currentUser, 10L, file);

        assertThat(response.knowledgeBaseId()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.fileName()).isEqualTo("notes.txt");
        assertThat(response.fileType()).isEqualTo("txt");
        assertThat(response.fileSize()).isEqualTo(file.getSize());
        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(knowledgeBase.getDocumentCount()).isEqualTo(1);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).saveAndFlush(captor.capture());
        assertThat(Files.exists(Path.of(captor.getValue().getFilePath()))).isTrue();
        verify(cacheInvalidationService).evictKnowledgeBaseCaches(1L, 10L);
        verify(cacheInvalidationService).evictDocumentList(1L, 10L);
    }

    @Test
    void shouldRejectOtherUsersKnowledgeBase() {
        DocumentService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "Spring Boot notes".getBytes());
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(currentUser, 10L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不存在");
    }

    @Test
    void shouldRejectUnsupportedExtension() {
        KnowledgeBase knowledgeBase = knowledgeBase(10L, 1L);
        DocumentService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "virus.exe",
                "application/octet-stream",
                "bad".getBytes());
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase));

        assertThatThrownBy(() -> service.upload(currentUser, 10L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅支持 PDF、TXT、Markdown、Word、Excel 文件");
    }

    @Test
    void shouldAcceptWordAndExcelExtensions() {
        DocumentStorageProperties properties = properties();

        assertThat(properties.getAllowedExtensions()).contains("docx", "xlsx");
        assertThat(properties.getAllowedContentTypes()).contains(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    void shouldRejectTooLargeFile() {
        KnowledgeBase knowledgeBase = knowledgeBase(10L, 1L);
        DocumentStorageProperties properties = properties();
        properties.setMaxSizeBytes(3);
        DocumentService service = new DocumentService(
                documentRepository,
                knowledgeBaseRepository,
                properties,
                cacheInvalidationService);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "large".getBytes());
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase));

        assertThatThrownBy(() -> service.upload(currentUser, 10L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传文件不能超过 10MB");
    }

    @Test
    void shouldListDocumentsInKnowledgeBase() {
        KnowledgeBase knowledgeBase = knowledgeBase(10L, 1L);
        Document document = document(100L, 10L, 1L, tempDir.resolve("notes.txt"));
        DocumentService service = service();
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(10L, 1L))
                .thenReturn(List.of(document));

        List<DocumentResponse> responses = service.listByKnowledgeBase(currentUser, 10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(100L);
        assertThat(responses.getFirst().fileName()).isEqualTo("notes.txt");
    }

    @Test
    void shouldGetDocumentDetail() {
        Document document = document(100L, 10L, 1L, tempDir.resolve("notes.txt"));
        DocumentService service = service();
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(document));

        DocumentResponse response = service.getMine(currentUser, 100L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void shouldMarkDocumentPendingWhenReprocess() {
        Document document = document(100L, 10L, 1L, tempDir.resolve("notes.txt"));
        document.markFailed("文档解析失败");
        DocumentService service = service();
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(document));

        DocumentStatusResponse response = service.reprocess(currentUser, 100L);

        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.errorMessage()).isNull();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        verify(cacheInvalidationService).evictDocumentCaches(1L, 10L, 100L);
    }

    @Test
    void shouldRejectOtherUsersDocument() {
        DocumentService service = service();
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(currentUser, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档不存在");
    }

    @Test
    void shouldDeleteDocumentAndStoredFile() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBase(10L, 1L);
        ReflectionTestUtils.setField(knowledgeBase, "documentCount", 1);
        Path filePath = tempDir.resolve("notes.txt");
        Files.writeString(filePath, "Spring Boot notes");
        Document document = document(100L, 10L, 1L, filePath);
        DocumentService service = service();
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase));

        service.delete(currentUser, 100L);

        verify(documentRepository).delete(document);
        assertThat(knowledgeBase.getDocumentCount()).isZero();
        assertThat(Files.exists(filePath)).isFalse();
        verify(cacheInvalidationService).evictKnowledgeBaseCaches(1L, 10L);
        verify(cacheInvalidationService).evictDocumentCaches(1L, 10L, 100L);
    }

    private DocumentService service() {
        return new DocumentService(
                documentRepository,
                knowledgeBaseRepository,
                properties(),
                cacheInvalidationService);
    }

    private DocumentStorageProperties properties() {
        DocumentStorageProperties properties = new DocumentStorageProperties();
        properties.setStorageDir(tempDir.resolve("documents").toString());
        return properties;
    }

    private KnowledgeBase knowledgeBase(Long id, Long userId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase(userId, "Java Notes", null);
        ReflectionTestUtils.setField(knowledgeBase, "id", id);
        return knowledgeBase;
    }

    private Document document(Long id, Long knowledgeBaseId, Long userId, Path filePath) {
        Document document = new Document(
                knowledgeBaseId,
                userId,
                "notes.txt",
                "txt",
                17L,
                filePath.toString());
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
