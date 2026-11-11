package com.example.ragkb.document.service;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.admin.service.OperationLogService;
import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.cache.CacheNames;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.document.config.DocumentStorageProperties;
import com.example.ragkb.document.dto.DocumentResponse;
import com.example.ragkb.document.dto.DocumentStatusResponse;
import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseAccess;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.service.KnowledgeBaseAccessService;
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
    private final KnowledgeBaseAccessService knowledgeBaseAccessService;
    private final DocumentStorageProperties properties;
    private final CacheInvalidationService cacheInvalidationService;
    private final OperationLogService operationLogService;

    public DocumentService(
            DocumentRepository documentRepository,
            KnowledgeBaseAccessService knowledgeBaseAccessService,
            DocumentStorageProperties properties,
            CacheInvalidationService cacheInvalidationService,
            OperationLogService operationLogService) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseAccessService = knowledgeBaseAccessService;
        this.properties = properties;
        this.cacheInvalidationService = cacheInvalidationService;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public DocumentResponse upload(CurrentUser currentUser, Long knowledgeBaseId, MultipartFile file) {
        KnowledgeBaseAccess access = knowledgeBaseAccessService.requireWritable(currentUser, knowledgeBaseId);
        KnowledgeBase knowledgeBase = access.knowledgeBase();

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
        Document saved = documentRepository.saveAndFlush(document);
        operationLogService.record(
                currentUser.id(),
                OperationAction.DOCUMENT_UPLOAD,
                OperationResourceType.DOCUMENT,
                saved.getId());
        cacheInvalidationService.evictAllKnowledgeBaseCaches();
        cacheInvalidationService.evictAllDocumentCaches();
        return DocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.DOCUMENT_LIST,
            key = "T(com.example.ragkb.common.cache.CacheKeys).knowledgeBaseDocuments(#currentUser.id(), #knowledgeBaseId)")
    public List<DocumentResponse> listByKnowledgeBase(CurrentUser currentUser, Long knowledgeBaseId) {
        knowledgeBaseAccessService.requireVisible(currentUser, knowledgeBaseId);
        return documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.DOCUMENT_DETAIL,
            key = "T(com.example.ragkb.common.cache.CacheKeys).userResource(#currentUser.id(), #documentId)")
    public DocumentResponse getMine(CurrentUser currentUser, Long documentId) {
        Document document = findDocument(documentId);
        knowledgeBaseAccessService.requireVisible(currentUser, document.getKnowledgeBaseId());
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.DOCUMENT_STATUS,
            key = "T(com.example.ragkb.common.cache.CacheKeys).userResource(#currentUser.id(), #documentId)")
    public DocumentStatusResponse getStatus(CurrentUser currentUser, Long documentId) {
        Document document = findDocument(documentId);
        knowledgeBaseAccessService.requireVisible(currentUser, document.getKnowledgeBaseId());
        return DocumentStatusResponse.from(document);
    }

    @Transactional
    public DocumentStatusResponse reprocess(CurrentUser currentUser, Long documentId) {
        Document document = findDocument(documentId);
        knowledgeBaseAccessService.requireWritable(currentUser, document.getKnowledgeBaseId());
        document.markPending();
        operationLogService.record(
                currentUser.id(),
                OperationAction.DOCUMENT_REPROCESS,
                OperationResourceType.DOCUMENT,
                documentId);
        cacheInvalidationService.evictAllDocumentCaches();
        return DocumentStatusResponse.from(document);
    }

    @Transactional
    public void delete(CurrentUser currentUser, Long documentId) {
        Document document = findDocument(documentId);
        KnowledgeBaseAccess access =
                knowledgeBaseAccessService.requireWritable(currentUser, document.getKnowledgeBaseId());
        KnowledgeBase knowledgeBase = access.knowledgeBase();

        documentRepository.delete(document);
        knowledgeBase.decreaseDocumentCount();
        deleteStoredFile(document.getFilePath());
        operationLogService.record(
                currentUser.id(),
                OperationAction.DOCUMENT_DELETE,
                OperationResourceType.DOCUMENT,
                documentId);
        cacheInvalidationService.evictAllKnowledgeBaseCaches();
        cacheInvalidationService.evictAllDocumentCaches();
    }

    private Document findDocument(Long documentId) {
        return documentRepository.findById(documentId)
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
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "仅支持 PDF、TXT、Markdown、Word、Excel 文件");
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
