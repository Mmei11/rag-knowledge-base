package com.example.ragkb.admin.entity;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "operation_logs")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private OperationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 50)
    private OperationResourceType resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected OperationLog() {
    }

    public OperationLog(
            Long userId,
            OperationAction action,
            OperationResourceType resourceType,
            Long resourceId,
            String ip) {
        this.userId = userId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.ip = ip;
    }

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OperationAction getAction() {
        return action;
    }

    public OperationResourceType getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public String getIp() {
        return ip;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
