package com.example.ragkb.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "parse_progress", nullable = false)
    private int parseProgress;

    @Column(name = "parse_stage", length = 100)
    private String parseStage = "等待解析";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Document() {
    }

    public Document(
            Long knowledgeBaseId,
            Long userId,
            String fileName,
            String fileType,
            long fileSize,
            String filePath) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.userId = userId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.filePath = filePath;
    }

    public void markProcessing() {
        status = DocumentStatus.PROCESSING;
        errorMessage = null;
        parseProgress = 0;
        parseStage = "准备解析";
    }

    public void markPending() {
        status = DocumentStatus.PENDING;
        errorMessage = null;
        parseProgress = 0;
        parseStage = "等待解析";
    }

    public void markCompleted() {
        status = DocumentStatus.COMPLETED;
        errorMessage = null;
        parseProgress = 100;
        parseStage = "解析完成";
    }

    public void markFailed(String errorMessage) {
        status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
        parseStage = "解析失败";
    }

    public void updateParseProgress(int progress, String stage) {
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("parse progress must be between 0 and 100");
        }
        parseProgress = progress;
        parseStage = stage;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFilePath() {
        return filePath;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getParseProgress() {
        return parseProgress;
    }

    public String getParseStage() {
        return parseStage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
