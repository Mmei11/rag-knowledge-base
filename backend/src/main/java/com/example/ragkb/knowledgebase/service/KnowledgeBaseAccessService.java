package com.example.ragkb.knowledgebase.service;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseAccess;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseAccessRole;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMember;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMemberRole;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseMemberRepository;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.security.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseAccessService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseMemberRepository knowledgeBaseMemberRepository;

    public KnowledgeBaseAccessService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseMemberRepository knowledgeBaseMemberRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseMemberRepository = knowledgeBaseMemberRepository;
    }

    public KnowledgeBaseAccess requireVisible(CurrentUser currentUser, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> notFound());

        if (knowledgeBase.getUserId().equals(currentUser.id())) {
            return new KnowledgeBaseAccess(knowledgeBase, KnowledgeBaseAccessRole.OWNER);
        }

        KnowledgeBaseMember member = knowledgeBaseMemberRepository
                .findByKnowledgeBaseIdAndUserId(knowledgeBaseId, currentUser.id())
                .orElseThrow(() -> notFound());
        return new KnowledgeBaseAccess(knowledgeBase, toAccessRole(member.getRole()));
    }

    public KnowledgeBaseAccess requireWritable(CurrentUser currentUser, Long knowledgeBaseId) {
        KnowledgeBaseAccess access = requireVisible(currentUser, knowledgeBaseId);
        if (!access.canWrite()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限修改该知识库");
        }
        return access;
    }

    public KnowledgeBaseAccess requireOwner(CurrentUser currentUser, Long knowledgeBaseId) {
        KnowledgeBaseAccess access = requireVisible(currentUser, knowledgeBaseId);
        if (!access.canManageMembers()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有知识库所有者可以执行此操作");
        }
        return access;
    }

    private KnowledgeBaseAccessRole toAccessRole(KnowledgeBaseMemberRole role) {
        return switch (role) {
            case EDITOR -> KnowledgeBaseAccessRole.EDITOR;
            case VIEWER -> KnowledgeBaseAccessRole.VIEWER;
        };
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库不存在");
    }
}
