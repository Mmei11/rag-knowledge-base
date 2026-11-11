package com.example.ragkb.knowledgebase.dto;

import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddKnowledgeBaseMemberRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotNull(message = "成员角色不能为空") KnowledgeBaseMemberRole role) {
}
