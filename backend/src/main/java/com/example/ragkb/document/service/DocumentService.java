package com.example.ragkb.document.service;

import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.cache.CacheNames;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.document.config.DocumentStorageProperties;
import com.example.ragkb.document.dto.DocumentResponse;
import com.example.ragkb.document.dto.DocumentStatusResponse;
import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.security.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentStorageProperties properties;
    private final CacheInvalidationService cacheInvalidationService;

    public DocumentService(
            DocumentRepository documentRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentStorageProperties properties,
            CacheInvalidationService cacheInvalidationService) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.properties = properties;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional
    public DocumentResponse upload(CurrentUser currentUser, Long knowledgeBaseId, MultipartFile file) {
        KnowledgeBase knowledgeBase = ensureKnowledgeBaseMine(currentUser, knowledgeBaseId);

        FileMetadata metadata = validate(file);
        Path storedPath = storeFile(knowledgeBaseId, metadata.extension(), file);

        Document document = new Document(
                knowledgeBase.getId(),
                currentUser.id(),
                metadata.originalFileName(),
                metadata.extension(),
                file.getSize(),
                storedPath.toString());

        knowledgeBase.increaseDocumentCount();
        DocumentResponse response = DocumentResponse.from(documentRepository.saveAndFlush(document));
        cacheInvalidationService.evictKnowledgeBaseCaches(currentUser.id(), knowledgeBaseId);
        cacheInvalidationService.evictDocumentList(currentUser.id(), knowledgeBaseId);
        return response;
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.DOCUMENT_LIST,
            key = "T(com.example.ragkb.common.cache.CacheKeys).knowledgeBaseDocuments(#currentUser.id(), #knowledgeBaseId)")
    public List<DocumentResponse> listByKnowledgeBase(CurrentUser currentUser, Long knowledgeBaseId) {
        ensureKnowledgeBaseMine(currentUser, knowledgeBaseId);
        return documentRepository.findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(knowledgeBaseId, currentUser.id())
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.DOCUMENT_DETAIL,
            key = "T(com.example.ragkb.common.cache.CacheKeys).userResource(#currentUser.id(), #documentId)")
    public DocumentResponse getMine(CurrentUser currentUser, Long documentId) {
        return DocumentResponse.from(findDocumentMine(currentUser, documentId));
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.DOCUMENT_STATUS,
            key = "T(com.example.ragkb.common.cache.CacheKeys).userResource(#currentUser.id(), #documentId)")
    public DocumentStatusResponse getStatus(CurrentUser currentUser, Long documentId) {
        return DocumentStatusResponse.from(findDocumentMine(currentUser, documentId));
    }

    @Transactional
    public DocumentStatusResponse reprocess(CurrentUser currentUser, Long documentId) {
        Document document = findDocumentMine(currentUser, documentId);
        document.markPending();
        cacheInvalidationService.evictDocumentCaches(currentUser.id(), document.getKnowledgeBaseId(), documentId);
        return DocumentStatusResponse.from(document);
    }

    @Transactional
    public void delete(CurrentUser currentUser, Long documentId) {
        Document document = findDocumentMine(currentUser, documentId);
        KnowledgeBase knowledgeBase = ensureKnowledgeBaseMine(currentUser, document.getKnowledgeBaseId());

        documentRepository.delete(document);
        knowledgeBase.decreaseDocumentCount();
        deleteStoredFile(document.getFilePath());
        cacheInvalidationService.evictKnowledgeBaseCaches(currentUser.id(), document.getKnowledgeBaseId());
        cacheInvalidationService.evictDocumentCaches(currentUser.id(), document.getKnowledgeBaseId(), documentId);
    }

    private KnowledgeBase ensureKnowledgeBaseMine(CurrentUser currentUser, Long knowledgeBaseId) {
        return knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库不存在"));
    }

    private Document findDocumentMine(CurrentUser currentUser, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在"));
    }

    private FileMetadata validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "上传文件不能为空");
        }

        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "上传文件不能超过 10MB");
        }

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()).trim();
        if (originalFileName.isBlank() || originalFileName.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文件名不合法");
        }

        String extension = extensionOf(originalFileName);
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "仅支持 PDF、TXT、Markdown 文件");
        }

        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.isBlank()
                && !properties.getAllowedContentTypes().contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文件 MIME Type 不支持");
        }

        return new FileMetadata(originalFileName, extension);
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文件扩展名不能为空");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private Path storeFile(Long knowledgeBaseId, String extension, MultipartFile file) {
        Path rootDir = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        Path knowledgeBaseDir = rootDir.resolve(String.valueOf(knowledgeBaseId)).normalize();
        Path target = knowledgeBaseDir.resolve(UUID.randomUUID() + "." + extension).normalize();

        if (!target.startsWith(rootDir)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文件保存路径不合法");
        }

        try {
            Files.createDirectories(knowledgeBaseDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    private void deleteStoredFile(String filePath) {
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件删除失败");
        }
    }

    private record FileMetadata(String originalFileName, String extension) {
    }
}
