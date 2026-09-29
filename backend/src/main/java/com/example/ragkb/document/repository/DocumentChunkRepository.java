package com.example.ragkb.document.repository;

import com.example.ragkb.document.entity.DocumentChunk;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    long countByDocumentId(Long documentId);

    @Transactional
    long deleteByDocumentId(Long documentId);
}
