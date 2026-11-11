package com.example.ragkb.document.repository;

import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.entity.DocumentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(Long knowledgeBaseId, Long userId);

    List<Document> findByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId);

    Optional<Document> findByIdAndUserId(Long id, Long userId);

    List<Document> findByStatusIn(List<DocumentStatus> statuses);

    long countByIdInAndKnowledgeBaseIdAndUserId(List<Long> ids, Long knowledgeBaseId, Long userId);

    long countByIdInAndKnowledgeBaseId(List<Long> ids, Long knowledgeBaseId);
}
