package com.example.ragkb.admin.dto;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import java.time.OffsetDateTime;

public record OperationLogResponse(
        Long id,
        Long userId,
        String username,
        OperationAction action,
        OperationResourceType resourceType,
        Long resourceId,
        String ip,
        OffsetDateTime createdAt) {
}
