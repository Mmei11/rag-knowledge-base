package com.example.ragkb.knowledgebase.dto;

import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseAccessRole;

public record KnowledgeBaseAccess(
        KnowledgeBase knowledgeBase,
        KnowledgeBaseAccessRole role) {

    public boolean canWrite() {
        return role == KnowledgeBaseAccessRole.OWNER || role == KnowledgeBaseAccessRole.EDITOR;
    }

    public boolean canManageMembers() {
        return role == KnowledgeBaseAccessRole.OWNER;
    }
}
