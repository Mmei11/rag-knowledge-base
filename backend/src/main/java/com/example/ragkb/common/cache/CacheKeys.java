package com.example.ragkb.common.cache;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String user(Long userId) {
        return "user:" + userId;
    }

    public static String userResource(Long userId, Long resourceId) {
        return "user:" + userId + ":resource:" + resourceId;
    }

    public static String knowledgeBaseDocuments(Long userId, Long knowledgeBaseId) {
        return "user:" + userId + ":knowledge-base:" + knowledgeBaseId + ":documents";
    }
}
