package com.example.ragkb.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.document.chunk.TextChunker;
import com.example.ragkb.document.dto.DocumentParseResponse;
import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentChunk;
import com.example.ragkb.document.entity.DocumentStatus;
import com.example.ragkb.document.parser.DocumentParserFactory;
import com.example.ragkb.document.parser.MarkdownDocumentParser;
import com.example.ragkb.document.parser.PdfDocumentParser;
import com.example.ragkb.document.parser.TextDocumentParser;
import com.example.ragkb.document.repository.DocumentChunkEmbeddingRepository;
import com.example.ragkb.document.repository.DocumentChunkRepository;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.rag.model.EmbeddingService;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.UserRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentParseServiceTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @TempDir
    private Path tempDir;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private DocumentProgressService documentProgressService;

    @Test
    void shouldParseTextDocumentAndMarkCompleted() throws Exception {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Spring Boot notes");
        Document document = document(100L, 1L, "txt", file);
        DocumentParseService service = service();
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(document));
        when(documentChunkRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> withIds(invocation.getArgument(0)));
        when(embeddingService.embedBatch(anyList(), any()))
                .thenReturn(List.of(new EmbeddingVector(List.of(0.1, 0.2))));

        DocumentParseResponse response = service.parse(currentUser, 100L);

        assertThat(response.status()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(response.content()).isEqualTo("Spring Boot notes");
        assertThat(response.chunkCount()).isEqualTo(1);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(document.getErrorMessage()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> chunksCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(documentChunkRepository).deleteByDocumentId(100L);
        verify(documentChunkRepository).saveAllAndFlush(chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(1);
        assertThat(chunksCaptor.getValue().get(0).getChunkIndex()).isZero();
        assertThat(chunksCaptor.getValue().get(0).getContent()).isEqualTo("Spring Boot notes");
        verify(embeddingService).embedBatch(eq(List.of("Spring Boot notes")), any());
        verify(documentChunkEmbeddingRepository).updateEmbeddings(anyList(), anyList());
        verify(documentProgressService).start(100L);
        verify(documentProgressService).complete(100L);
    }

    @Test
    void shouldProcessDocumentById() throws Exception {
        Path file = tempDir.resolve("process.txt");
        Files.writeString(file, "Async process notes");
        Document document = document(100L, 1L, "txt", file);
        DocumentParseService service = service();
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(documentChunkRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> withIds(invocation.getArgument(0)));
        when(embeddingService.embedBatch(anyList(), any()))
                .thenReturn(List.of(new EmbeddingVector(List.of(0.1, 0.2))));

        DocumentParseResponse response = service.process(100L);

        assertThat(response.status()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(response.chunkCount()).isEqualTo(1);
        verify(documentChunkRepository).deleteByDocumentId(100L);
        verify(documentProgressService).start(100L);
        verify(documentProgressService).complete(100L);
    }

    @Test
    void shouldStorePdfPageNumberOnEachChunk() throws Exception {
        Path file = tempDir.resolve("pages.pdf");
        try (PDDocument pdf = new PDDocument()) {
            addPdfPage(pdf, "First page notes");
            addPdfPage(pdf, "Second page notes");
            pdf.save(file.toFile());
        }
        Document document = document(100L, 1L, "pdf", file);
        DocumentParseService service = service();
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(document));
        when(documentChunkRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> withIds(invocation.getArgument(0)));
        when(embeddingService.embedBatch(anyList(), any()))
                .thenReturn(List.of(
                        new EmbeddingVector(List.of(0.1, 0.2)),
                        new EmbeddingVector(List.of(0.3, 0.4))));

        DocumentParseResponse response = service.parse(currentUser, 100L);

        assertThat(response.status()).isEqualTo(DocumentStatus.COMPLETED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> chunksCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(documentChunkRepository).saveAllAndFlush(chunksCaptor.capture());
        assertThat(chunksCaptor.getValue())
                .extracting(
                        DocumentChunk::getChunkIndex,
                        DocumentChunk::getPageNumber,
                        DocumentChunk::getPdfPageNumber)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, 1, 1),
                        org.assertj.core.groups.Tuple.tuple(1, 2, 2));
    }

    @Test
    void shouldMarkFailedWhenContentIsBlank() throws Exception {
        Path file = tempDir.resolve("blank.txt");
        Files.writeString(file, "   ");
        Document document = document(100L, 1L, "txt", file);
        DocumentParseService service = service();
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.parse(currentUser, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档内容为空");
        verify(documentProgressService).fail(100L, "文档内容为空");
    }

    @Test
    void shouldRejectOtherUsersDocument() {
        DocumentParseService service = service();
        when(documentRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.parse(currentUser, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档不存在");
    }

    private DocumentParseService service() {
        DocumentParserFactory parserFactory = new DocumentParserFactory(List.of(
                new TextDocumentParser(),
                new MarkdownDocumentParser(),
                new PdfDocumentParser()));
        return new DocumentParseService(
                documentRepository,
                documentChunkRepository,
                documentChunkEmbeddingRepository,
                parserFactory,
                new TextChunker(),
                embeddingService,
                documentProgressService);
    }

    private List<DocumentChunk> withIds(List<DocumentChunk> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            ReflectionTestUtils.setField(chunks.get(i), "id", (long) i + 1);
        }
        return chunks;
    }

    private Document document(Long id, Long userId, String fileType, Path filePath) {
        Document document = new Document(
                10L,
                userId,
                filePath.getFileName().toString(),
                fileType,
                17L,
                filePath.toString());
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private void addPdfPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(50, 700);
            contentStream.showText(text);
            contentStream.endText();
        }
    }
}
