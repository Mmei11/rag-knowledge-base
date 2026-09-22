package com.example.ragkb.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "pdf_page_number")
    private Integer pdfPageNumber;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DocumentChunk() {
    }

    public DocumentChunk(
            Long documentId,
            Long knowledgeBaseId,
            int chunkIndex,
            String content,
            int tokenCount,
            Integer pageNumber) {
        this(documentId, knowledgeBaseId, chunkIndex, content, tokenCount, pageNumber, null);
    }

    public DocumentChunk(
            Long documentId,
            Long knowledgeBaseId,
            int chunkIndex,
            String content,
            int tokenCount,
            Integer pageNumber,
            Integer pdfPageNumber) {
        this.documentId = documentId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.pageNumber = pageNumber;
        this.pdfPageNumber = pdfPageNumber;
    }

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public Integer getPdfPageNumber() {
        return pdfPageNumber;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
