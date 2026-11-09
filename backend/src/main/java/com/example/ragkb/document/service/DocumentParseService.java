package com.example.ragkb.document.service;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.common.text.ChineseTextSegmenter;
import com.example.ragkb.document.chunk.TextChunk;
import com.example.ragkb.document.chunk.TextChunker;
import com.example.ragkb.document.dto.DocumentParseResponse;
import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentChunk;
import com.example.ragkb.document.parser.DocumentParser;
import com.example.ragkb.document.parser.DocumentParserFactory;
import com.example.ragkb.document.parser.ParsedDocument;
import com.example.ragkb.document.parser.ParsedPage;
import com.example.ragkb.document.repository.DocumentChunkEmbeddingRepository;
import com.example.ragkb.document.repository.DocumentChunkRepository;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.rag.model.EmbeddingService;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.security.CurrentUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentParseService {
    private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository;
    private final DocumentParserFactory parserFactory;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final DocumentProgressService documentProgressService;
    private final ChineseTextSegmenter textSegmenter;

    public DocumentParseService(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository,
            DocumentParserFactory parserFactory,
            TextChunker textChunker,
            EmbeddingService embeddingService,
            DocumentProgressService documentProgressService,
            ChineseTextSegmenter textSegmenter) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentChunkEmbeddingRepository = documentChunkEmbeddingRepository;
        this.parserFactory = parserFactory;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.documentProgressService = documentProgressService;
        this.textSegmenter = textSegmenter;
    }

    public DocumentParseResponse parse(CurrentUser currentUser, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在"));
        return parse(document);
    }

    public DocumentParseResponse process(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在"));
        return parse(document);
    }

    private DocumentParseResponse parse(Document document) {
        documentProgressService.start(document.getId());

        try {
            Path filePath = Path.of(document.getFilePath());
            if (!Files.isRegularFile(filePath)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档文件不存在");
            }

            DocumentParser parser = parserFactory.getParser(document.getFileType());
            ParsedDocument parsedDocument = parser.parseDocument(
                    filePath,
                    parseProgress -> reportProgress(
                            document,
                            5 + parseProgress * 25 / 100,
                            "正在提取文本"));
            reportProgress(document, 30, "正在切分文本");
            String content = normalize(parsedDocument.content());
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文档内容为空");
            }

            List<TextChunk> chunks = chunk(document, parsedDocument);
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文档内容为空");
            }

            documentChunkRepository.deleteByDocumentId(document.getId());
            reportProgress(document, 48, "正在写入文本片段");
            List<DocumentChunk> savedChunks = documentChunkRepository.saveAllAndFlush(chunks.stream()
                    .map(chunk -> new DocumentChunk(
                            document.getId(),
                            document.getKnowledgeBaseId(),
                            chunk.index(),
                            chunk.content(),
                            chunk.tokenCount(),
                            chunk.pageNumber(),
                            chunk.pdfPageNumber(),
                            textSegmenter.buildSearchText(chunk.content())))
                    .toList());

            reportProgress(document, 55, "正在生成向量");
            embedAndStore(document, savedChunks);

            documentProgressService.complete(document.getId());
            document.markCompleted();
            return DocumentParseResponse.completed(document, content, chunks.size());
        } catch (BusinessException ex) {
            documentProgressService.fail(document.getId(), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Document parsing failed, documentId={}, stage=vectorization", document.getId(), ex);
            documentProgressService.fail(document.getId(), "文档解析失败");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文档解析失败");
        }
    }

    private void reportProgress(Document document, int progress, String stage) {
        documentProgressService.report(document.getId(), progress, stage);
    }

    private List<TextChunk> chunk(Document document, ParsedDocument parsedDocument) {
        List<TextChunk> chunks = new java.util.ArrayList<>();
        int nextIndex = 0;
        int totalPages = parsedDocument.pages().size();
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            ParsedPage page = parsedDocument.pages().get(pageIndex);
            List<TextChunk> pageChunks = textChunker.chunk(
                    page.content(),
                    nextIndex,
                    page.pageNumber(),
                    page.pdfPageNumber());
            chunks.addAll(pageChunks);
            nextIndex += pageChunks.size();
            reportProgress(document, 30 + (pageIndex + 1) * 15 / totalPages, "正在切分文本");
        }
        return List.copyOf(chunks);
    }

    private void embedAndStore(Document document, List<DocumentChunk> chunks) {
        List<EmbeddingVector> embeddings = embeddingService.embedBatch(
                chunks.stream().map(DocumentChunk::getContent).toList(),
                progress -> reportEmbeddingProgress(document, progress));
        reportProgress(document, 99, "正在保存向量");
        documentChunkEmbeddingRepository.updateEmbeddings(chunks, embeddings);
    }

    private void reportEmbeddingProgress(
            Document document,
            com.example.ragkb.rag.model.EmbeddingBatchProgress progress) {
        int completion = 55 + progress.completedItems() * 44 / progress.totalItems();
        String stage = "正在生成向量（已完成 %d/%d 个片段%s）".formatted(
                progress.completedItems(),
                progress.totalItems(),
                formatEstimatedRemainingTime(progress));
        reportProgress(document, completion, stage);
    }

    private String formatEstimatedRemainingTime(
            com.example.ragkb.rag.model.EmbeddingBatchProgress progress) {
        if (progress.completedItems() == 0 || progress.completedItems() == progress.totalItems()) {
            return "";
        }
        long remainingSeconds = Math.max(1, Math.round(
                (double) progress.elapsedMillis() / progress.completedItems()
                        * (progress.totalItems() - progress.completedItems()) / 1000));
        if (remainingSeconds < 60) {
            return "，预计剩余 %d 秒".formatted(remainingSeconds);
        }
        return "，预计剩余 %d 分 %d 秒".formatted(remainingSeconds / 60, remainingSeconds % 60);
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .trim();
    }
}
