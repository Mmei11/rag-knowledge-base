package com.example.ragkb.knowledgebase.service;

import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.cache.CacheNames;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.knowledgebase.dto.CreateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseResponse;
import com.example.ragkb.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.security.CurrentUser;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public KnowledgeBaseService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            CacheInvalidationService cacheInvalidationService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.cacheInvalidationService = cacheInvalidationService;
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
            KnowledgeBaseResponse response = KnowledgeBaseResponse.from(knowledgeBaseRepository.saveAndFlush(knowledgeBase));
            cacheInvalidationService.evictKnowledgeBaseList(currentUser.id());
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw duplicatedName();
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.KNOWLEDGE_BASE_LIST,
            key = "T(com.example.ragkb.common.cache.CacheKeys).user(#currentUser.id())")
    public List<KnowledgeBaseResponse> listMine(CurrentUser currentUser) {
        return knowledgeBaseRepository.findByUserIdOrderByUpdatedAtDesc(currentUser.id())
                .stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.KNOWLEDGE_BASE_DETAIL,
            key = "T(com.example.ragkb.common.cache.CacheKeys).userResource(#currentUser.id(), #id)")
    public KnowledgeBaseResponse getMine(CurrentUser currentUser, Long id) {
        return KnowledgeBaseResponse.from(findMine(currentUser, id));
    }

    @Transactional
    public KnowledgeBaseResponse update(CurrentUser currentUser, Long id, UpdateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = findMine(currentUser, id);
        String name = normalizeName(request.name());
        String description = normalizeDescription(request.description());

        if (knowledgeBaseRepository.existsByUserIdAndNameAndIdNot(currentUser.id(), name, id)) {
            throw duplicatedName();
        }

        knowledgeBase.update(name, description);

        try {
            KnowledgeBaseResponse response = KnowledgeBaseResponse.from(knowledgeBaseRepository.saveAndFlush(knowledgeBase));
            cacheInvalidationService.evictKnowledgeBaseCaches(currentUser.id(), id);
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw duplicatedName();
        }
    }

    @Transactional
    public void delete(CurrentUser currentUser, Long id) {
        KnowledgeBase knowledgeBase = findMine(currentUser, id);
        knowledgeBaseRepository.delete(knowledgeBase);
        cacheInvalidationService.evictKnowledgeBaseCaches(currentUser.id(), id);
    }

    private KnowledgeBase findMine(CurrentUser currentUser, Long id) {
        return knowledgeBaseRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库不存在"));
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
