package com.example.ragkb.knowledgebase.dto;

import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateKnowledgeBaseMemberRoleRequest(
        @NotNull(message = "成员角色不能为空") KnowledgeBaseMemberRole role) {
}
