package com.example.ragkb.document.service;

import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentStatus;
import com.example.ragkb.document.repository.DocumentRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProgressService {

    private final DocumentRepository documentRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public DocumentProgressService(
            DocumentRepository documentRepository,
            CacheInvalidationService cacheInvalidationService) {
        this.documentRepository = documentRepository;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(Long documentId) {
        update(documentId, document -> document.markProcessing());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void report(Long documentId, int progress, String stage) {
        update(documentId, document -> {
            if (document.getStatus() != DocumentStatus.PROCESSING
                    || progress < document.getParseProgress()
                    || (document.getParseProgress() == progress
                    && Objects.equals(document.getParseStage(), stage))) {
                return;
            }
            document.updateParseProgress(progress, stage);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long documentId) {
        update(documentId, Document::markCompleted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long documentId, String errorMessage) {
        update(documentId, document -> document.markFailed(errorMessage));
    }

    private void update(Long documentId, java.util.function.Consumer<Document> change) {
        documentRepository.findById(documentId).ifPresent(document -> {
            change.accept(document);
            cacheInvalidationService.evictDocumentCaches(
                    document.getUserId(),
                    document.getKnowledgeBaseId(),
                    document.getId());
        });
    }
}
