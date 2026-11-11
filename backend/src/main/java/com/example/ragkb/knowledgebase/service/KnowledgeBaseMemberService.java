package com.example.ragkb.knowledgebase.service;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.admin.service.OperationLogService;
import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.knowledgebase.dto.AddKnowledgeBaseMemberRequest;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseAccess;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseMemberResponse;
import com.example.ragkb.knowledgebase.dto.UpdateKnowledgeBaseMemberRoleRequest;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMember;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseMemberRepository;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.entity.UserStatus;
import com.example.ragkb.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseMemberService {

    private final KnowledgeBaseAccessService accessService;
    private final KnowledgeBaseMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OperationLogService operationLogService;
    private final CacheInvalidationService cacheInvalidationService;

    public KnowledgeBaseMemberService(
            KnowledgeBaseAccessService accessService,
            KnowledgeBaseMemberRepository memberRepository,
            UserRepository userRepository,
            OperationLogService operationLogService,
            CacheInvalidationService cacheInvalidationService) {
        this.accessService = accessService;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.operationLogService = operationLogService;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseMemberResponse> listMembers(CurrentUser currentUser, Long knowledgeBaseId) {
        KnowledgeBaseAccess access = accessService.requireOwner(currentUser, knowledgeBaseId);
        List<KnowledgeBaseMember> members =
                memberRepository.findByKnowledgeBaseIdOrderByCreatedAtAsc(access.knowledgeBase().getId());
        Map<Long, User> users = userRepository.findAllById(
                        members.stream().map(KnowledgeBaseMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream()
                .map(member -> KnowledgeBaseMemberResponse.from(member, users.get(member.getUserId())))
                .toList();
    }

    @Transactional
    public KnowledgeBaseMemberResponse addMember(
            CurrentUser currentUser,
            Long knowledgeBaseId,
            AddKnowledgeBaseMemberRequest request) {
        KnowledgeBaseAccess access = accessService.requireOwner(currentUser, knowledgeBaseId);
        User user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        if (user.getId().equals(access.knowledgeBase().getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不能把知识库所有者添加为成员");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用户已被禁用");
        }
        if (memberRepository.existsByKnowledgeBaseIdAndUserId(knowledgeBaseId, user.getId())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "用户已经是知识库成员");
        }

        KnowledgeBaseMember member = memberRepository.saveAndFlush(
                new KnowledgeBaseMember(knowledgeBaseId, user.getId(), request.role()));
        operationLogService.record(
                currentUser.id(),
                OperationAction.KB_MEMBER_ADD,
                OperationResourceType.KNOWLEDGE_BASE,
                knowledgeBaseId);
        cacheInvalidationService.evictAllKnowledgeBaseCaches();
        cacheInvalidationService.evictAllDocumentCaches();
        return KnowledgeBaseMemberResponse.from(member, user);
    }

    @Transactional
    public KnowledgeBaseMemberResponse changeRole(
            CurrentUser currentUser,
            Long knowledgeBaseId,
            Long memberUserId,
            UpdateKnowledgeBaseMemberRoleRequest request) {
        accessService.requireOwner(currentUser, knowledgeBaseId);
        KnowledgeBaseMember member = findMember(knowledgeBaseId, memberUserId);
        member.changeRole(request.role());
        memberRepository.saveAndFlush(member);
        User user = userRepository.findById(memberUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        operationLogService.record(
                currentUser.id(),
                OperationAction.KB_MEMBER_ROLE_CHANGE,
                OperationResourceType.KNOWLEDGE_BASE,
                knowledgeBaseId);
        cacheInvalidationService.evictAllKnowledgeBaseCaches();
        cacheInvalidationService.evictAllDocumentCaches();
        return KnowledgeBaseMemberResponse.from(member, user);
    }

    @Transactional
    public void removeMember(CurrentUser currentUser, Long knowledgeBaseId, Long memberUserId) {
        accessService.requireOwner(currentUser, knowledgeBaseId);
        long deleted = memberRepository.deleteByKnowledgeBaseIdAndUserId(knowledgeBaseId, memberUserId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库成员不存在");
        }
        operationLogService.record(
                currentUser.id(),
                OperationAction.KB_MEMBER_REMOVE,
                OperationResourceType.KNOWLEDGE_BASE,
                knowledgeBaseId);
        cacheInvalidationService.evictAllKnowledgeBaseCaches();
        cacheInvalidationService.evictAllDocumentCaches();
    }

    private KnowledgeBaseMember findMember(Long knowledgeBaseId, Long memberUserId) {
        return memberRepository.findByKnowledgeBaseIdAndUserId(knowledgeBaseId, memberUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库成员不存在"));
    }
}
