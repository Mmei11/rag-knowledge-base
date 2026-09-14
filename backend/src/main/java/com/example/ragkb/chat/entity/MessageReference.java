package com.example.ragkb.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "message_references")
public class MessageReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_id", nullable = false)
    private Long chunkId;

    @Column(nullable = false)
    private double similarity;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MessageReference() {
    }

    public MessageReference(Long messageId, Long documentId, Long chunkId, double similarity) {
        this.messageId = messageId;
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.similarity = similarity;
    }

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public double getSimilarity() {
        return similarity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
