package com.example.ragkb.document.task;

import com.example.ragkb.document.service.DocumentParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DocumentProcessTask {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessTask.class);

    private final DocumentParseService documentParseService;

    public DocumentProcessTask(DocumentParseService documentParseService) {
        this.documentParseService = documentParseService;
    }

    @Async("documentTaskExecutor")
    public void process(Long documentId) {
        try {
            documentParseService.process(documentId);
        } catch (RuntimeException ex) {
            log.warn("Document process task failed, documentId={}", documentId, ex);
        }
    }
}
