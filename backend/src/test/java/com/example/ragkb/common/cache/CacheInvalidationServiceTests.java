package com.example.ragkb.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class CacheInvalidationServiceTests {

    private final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            CacheNames.KNOWLEDGE_BASE_LIST,
            CacheNames.KNOWLEDGE_BASE_DETAIL,
            CacheNames.DOCUMENT_LIST,
            CacheNames.DOCUMENT_DETAIL,
            CacheNames.DOCUMENT_STATUS);

    private final CacheInvalidationService service = new CacheInvalidationService(cacheManager);

    @Test
    void shouldEvictKnowledgeBaseCaches() {
        cache(CacheNames.KNOWLEDGE_BASE_LIST).put(CacheKeys.user(1L), "list");
        cache(CacheNames.KNOWLEDGE_BASE_DETAIL).put(CacheKeys.userResource(1L, 10L), "detail");

        service.evictKnowledgeBaseCaches(1L, 10L);

        assertThat(cache(CacheNames.KNOWLEDGE_BASE_LIST).get(CacheKeys.user(1L))).isNull();
        assertThat(cache(CacheNames.KNOWLEDGE_BASE_DETAIL).get(CacheKeys.userResource(1L, 10L))).isNull();
    }

    @Test
    void shouldEvictDocumentCaches() {
        cache(CacheNames.DOCUMENT_LIST).put(CacheKeys.knowledgeBaseDocuments(1L, 10L), "list");
        cache(CacheNames.DOCUMENT_DETAIL).put(CacheKeys.userResource(1L, 100L), "detail");
        cache(CacheNames.DOCUMENT_STATUS).put(CacheKeys.userResource(1L, 100L), "status");

        service.evictDocumentCaches(1L, 10L, 100L);

        assertThat(cache(CacheNames.DOCUMENT_LIST).get(CacheKeys.knowledgeBaseDocuments(1L, 10L))).isNull();
        assertThat(cache(CacheNames.DOCUMENT_DETAIL).get(CacheKeys.userResource(1L, 100L))).isNull();
        assertThat(cache(CacheNames.DOCUMENT_STATUS).get(CacheKeys.userResource(1L, 100L))).isNull();
    }

    private Cache cache(String name) {
        return cacheManager.getCache(name);
    }
}
