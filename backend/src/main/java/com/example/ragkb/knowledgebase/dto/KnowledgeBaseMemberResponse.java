package com.example.ragkb.knowledgebase.dto;

import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMember;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMemberRole;
import com.example.ragkb.user.entity.User;
import java.time.OffsetDateTime;

public record KnowledgeBaseMemberResponse(
        Long userId,
        String username,
        String nickname,
        KnowledgeBaseMemberRole role,
        OffsetDateTime createdAt) {

    public static KnowledgeBaseMemberResponse from(KnowledgeBaseMember member, User user) {
        return new KnowledgeBaseMemberResponse(
                member.getUserId(),
                user.getUsername(),
                user.getNickname(),
                member.getRole(),
                member.getCreatedAt());
    }
}
