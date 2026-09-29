package com.example.ragkb.document.task;

import com.example.ragkb.document.entity.DocumentStatus;
import com.example.ragkb.document.repository.DocumentRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentProcessingRecovery {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingRecovery.class);

    private final DocumentRepository documentRepository;
    private final DocumentProcessTask documentProcessTask;

    public DocumentProcessingRecovery(
            DocumentRepository documentRepository,
            DocumentProcessTask documentProcessTask) {
        this.documentRepository = documentRepository;
        this.documentProcessTask = documentProcessTask;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedDocuments() {
        List<Long> documentIds = documentRepository
                .findByStatusIn(List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING))
                .stream()
                .map(document -> document.getId())
                .toList();
        if (documentIds.isEmpty()) {
            return;
        }

        log.info("Resuming {} pending document processing task(s)", documentIds.size());
        documentIds.forEach(documentProcessTask::process);
    }
}
