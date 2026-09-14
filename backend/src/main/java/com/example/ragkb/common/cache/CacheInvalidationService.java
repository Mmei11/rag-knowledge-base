package com.example.ragkb.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class CacheInvalidationService {

    private final CacheManager cacheManager;

    public CacheInvalidationService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictKnowledgeBaseList(Long userId) {
        evict(CacheNames.KNOWLEDGE_BASE_LIST, CacheKeys.user(userId));
    }

    public void evictKnowledgeBase(Long userId, Long knowledgeBaseId) {
        evict(CacheNames.KNOWLEDGE_BASE_DETAIL, CacheKeys.userResource(userId, knowledgeBaseId));
    }

    public void evictKnowledgeBaseCaches(Long userId, Long knowledgeBaseId) {
        evictKnowledgeBaseList(userId);
        evictKnowledgeBase(userId, knowledgeBaseId);
    }

    public void evictDocumentList(Long userId, Long knowledgeBaseId) {
        evict(CacheNames.DOCUMENT_LIST, CacheKeys.knowledgeBaseDocuments(userId, knowledgeBaseId));
    }

    public void evictDocument(Long userId, Long documentId) {
        String key = CacheKeys.userResource(userId, documentId);
        evict(CacheNames.DOCUMENT_DETAIL, key);
        evict(CacheNames.DOCUMENT_STATUS, key);
    }

    public void evictDocumentCaches(Long userId, Long knowledgeBaseId, Long documentId) {
        evictDocumentList(userId, knowledgeBaseId);
        evictDocument(userId, documentId);
    }

    private void evict(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
