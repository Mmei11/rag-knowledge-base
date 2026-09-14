package com.example.ragkb.document.service;

import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.document.chunk.TextChunk;
import com.example.ragkb.document.chunk.TextChunker;
import com.example.ragkb.document.dto.DocumentParseResponse;
import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentChunk;
import com.example.ragkb.document.parser.DocumentParser;
import com.example.ragkb.document.parser.DocumentParserFactory;
import com.example.ragkb.document.repository.DocumentChunkEmbeddingRepository;
import com.example.ragkb.document.repository.DocumentChunkRepository;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.rag.model.EmbeddingService;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.security.CurrentUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentParseService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository;
    private final DocumentParserFactory parserFactory;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final CacheInvalidationService cacheInvalidationService;

    public DocumentParseService(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository,
            DocumentParserFactory parserFactory,
            TextChunker textChunker,
            EmbeddingService embeddingService,
            CacheInvalidationService cacheInvalidationService) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentChunkEmbeddingRepository = documentChunkEmbeddingRepository;
        this.parserFactory = parserFactory;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public DocumentParseResponse parse(CurrentUser currentUser, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在"));
        return parse(document);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public DocumentParseResponse process(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在"));
        return parse(document);
    }

    private DocumentParseResponse parse(Document document) {
        document.markProcessing();
        evictDocumentCaches(document);

        try {
            Path filePath = Path.of(document.getFilePath());
            if (!Files.isRegularFile(filePath)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档文件不存在");
            }

            DocumentParser parser = parserFactory.getParser(document.getFileType());
            String content = normalize(parser.parse(filePath));
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文档内容为空");
            }

            List<TextChunk> chunks = textChunker.chunk(content);
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文档内容为空");
            }

            documentChunkRepository.deleteByDocumentId(document.getId());
            List<DocumentChunk> savedChunks = documentChunkRepository.saveAllAndFlush(chunks.stream()
                    .map(chunk -> new DocumentChunk(
                            document.getId(),
                            document.getKnowledgeBaseId(),
                            chunk.index(),
                            chunk.content(),
                            chunk.tokenCount(),
                            null))
                    .toList());

            List<EmbeddingVector> embeddings = embeddingService.embedBatch(savedChunks.stream()
                    .map(DocumentChunk::getContent)
                    .toList());
            documentChunkEmbeddingRepository.updateEmbeddings(savedChunks, embeddings);

            document.markCompleted();
            evictDocumentCaches(document);
            return DocumentParseResponse.completed(document, content, chunks.size());
        } catch (BusinessException ex) {
            document.markFailed(ex.getMessage());
            evictDocumentCaches(document);
            throw ex;
        } catch (RuntimeException ex) {
            evictDocumentCaches(document);
            document.markFailed("文档解析失败");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文档解析失败");
        }
    }

    private void evictDocumentCaches(Document document) {
        cacheInvalidationService.evictDocumentCaches(
                document.getUserId(),
                document.getKnowledgeBaseId(),
                document.getId());
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
