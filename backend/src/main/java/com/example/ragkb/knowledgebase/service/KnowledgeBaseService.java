package com.example.ragkb.knowledgebase.service;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.admin.service.OperationLogService;
import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.cache.CacheNames;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.knowledgebase.dto.CreateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseAccess;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseResponse;
import com.example.ragkb.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseAccessRole;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMember;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMemberRole;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseMemberRepository;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseMemberRepository knowledgeBaseMemberRepository;
    private final KnowledgeBaseAccessService accessService;
    private final CacheInvalidationService cacheInvalidationService;
    private final OperationLogService operationLogService;

    public KnowledgeBaseService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseMemberRepository knowledgeBaseMemberRepository,
            KnowledgeBaseAccessService accessService,
            CacheInvalidationService cacheInvalidationService,
            OperationLogService operationLogService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseMemberRepository = knowledgeBaseMemberRepository;
        this.accessService = accessService;
        this.cacheInvalidationService = cacheInvalidationService;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public KnowledgeBaseResponse create(CurrentUser currentUser, CreateKnowledgeBaseRequest request) {
        String name = normalizeName(request.name());
        String description = normalizeDescription(request.description());

        if (knowledgeBaseRepository.existsByUserIdAndName(currentUser.id(), name)) {
            throw duplicatedName();
        }

        KnowledgeBase knowledgeBase = new KnowledgeBase(currentUser.id(), name, description);

        try {
            KnowledgeBase saved = knowledgeBaseRepository.saveAndFlush(knowledgeBase);
            operationLogService.record(
                    currentUser.id(),
                    OperationAction.KB_CREATE,
                    OperationResourceType.KNOWLEDGE_BASE,
                    saved.getId());
            cacheInvalidationService.evictKnowledgeBaseList(currentUser.id());
            return KnowledgeBaseResponse.from(saved, KnowledgeBaseAccessRole.OWNER);
        } catch (DataIntegrityViolationException ex) {
            throw duplicatedName();
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.KNOWLEDGE_BASE_LIST,
            key = "T(com.example.ragkb.common.cache.CacheKeys).user(#currentUser.id())")
    public List<KnowledgeBaseResponse> listMine(CurrentUser currentUser) {
        Map<Long, KnowledgeBaseMemberRole> memberRoles =
                knowledgeBaseMemberRepository.findByUserId(currentUser.id()).stream()
                        .collect(Collectors.toMap(
                                KnowledgeBaseMember::getKnowledgeBaseId,
                                KnowledgeBaseMember::getRole));
        return knowledgeBaseRepository.findAccessibleByUserId(currentUser.id()).stream()
                .map(knowledgeBase -> {
                    KnowledgeBaseAccessRole role = knowledgeBase.getUserId().equals(currentUser.id())
                            ? KnowledgeBaseAccessRole.OWNER
                            : toAccessRole(memberRoles.get(knowledgeBase.getId()));
                    return role == null ? null : KnowledgeBaseResponse.from(knowledgeBase, role);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.KNOWLEDGE_BASE_DETAIL,
            key = "T(com.example.ragkb.common.cache.CacheKeys).userResource(#currentUser.id(), #id)")
    public KnowledgeBaseResponse getMine(CurrentUser currentUser, Long id) {
        KnowledgeBaseAccess access = accessService.requireVisible(currentUser, id);
        return KnowledgeBaseResponse.from(access.knowledgeBase(), access.role());
    }

    @Transactional
    public KnowledgeBaseResponse update(CurrentUser currentUser, Long id, UpdateKnowledgeBaseRequest request) {
        KnowledgeBaseAccess access = accessService.requireOwner(currentUser, id);
        KnowledgeBase knowledgeBase = access.knowledgeBase();
        String name = normalizeName(request.name());
        String description = normalizeDescription(request.description());

        if (knowledgeBaseRepository.existsByUserIdAndNameAndIdNot(currentUser.id(), name, id)) {
            throw duplicatedName();
        }

        knowledgeBase.update(name, description);

        try {
            KnowledgeBase saved = knowledgeBaseRepository.saveAndFlush(knowledgeBase);
            operationLogService.record(
                    currentUser.id(),
                    OperationAction.KB_UPDATE,
                    OperationResourceType.KNOWLEDGE_BASE,
                    saved.getId());
            cacheInvalidationService.evictAllKnowledgeBaseCaches();
            return KnowledgeBaseResponse.from(saved, KnowledgeBaseAccessRole.OWNER);
        } catch (DataIntegrityViolationException ex) {
            throw duplicatedName();
        }
    }

    @Transactional
    public void delete(CurrentUser currentUser, Long id) {
        KnowledgeBaseAccess access = accessService.requireOwner(currentUser, id);
        knowledgeBaseRepository.delete(access.knowledgeBase());
        operationLogService.record(
                currentUser.id(),
                OperationAction.KB_DELETE,
                OperationResourceType.KNOWLEDGE_BASE,
                id);
        cacheInvalidationService.evictAllKnowledgeBaseCaches();
        cacheInvalidationService.evictAllDocumentCaches();
    }

    private KnowledgeBaseAccessRole toAccessRole(KnowledgeBaseMemberRole role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case EDITOR -> KnowledgeBaseAccessRole.EDITOR;
            case VIEWER -> KnowledgeBaseAccessRole.VIEWER;
        };
    }

    private BusinessException duplicatedName() {
        return new BusinessException(ErrorCode.RESOURCE_CONFLICT, "知识库名称已存在");
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
