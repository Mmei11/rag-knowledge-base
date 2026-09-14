# Day 25: Redis Cache

## Goal

Add Redis-backed caching for high-frequency read APIs, and clear related cache entries when data changes.

## What Changed

### 1. Cache Names

File:

```text
backend/src/main/java/com/example/ragkb/common/cache/CacheNames.java
```

This class centralizes cache names:

```text
knowledge-base:list
knowledge-base:detail
document:list
document:detail
document:status
```

Using constants avoids typo bugs across annotations and manual cache eviction code.

### 2. Cache Keys

File:

```text
backend/src/main/java/com/example/ragkb/common/cache/CacheKeys.java
```

This class centralizes Redis cache key format:

```text
user:{userId}
user:{userId}:resource:{resourceId}
user:{userId}:knowledge-base:{knowledgeBaseId}:documents
```

The key always includes `userId` when the data belongs to one user. This keeps user data isolated in cache.

### 3. Redis Cache Configuration

File:

```text
backend/src/main/java/com/example/ragkb/config/RedisConfig.java
```

This configuration enables Spring Cache and uses Redis as the cache storage.

Important parts:

```text
@EnableCaching
RedisCacheManager
StringRedisSerializer for keys
GenericJackson2JsonRedisSerializer for values
JavaTimeModule for OffsetDateTime
TTL for each cache
```

Current TTL strategy:

```text
knowledge-base:list     5 minutes
knowledge-base:detail   10 minutes
document:list           3 minutes
document:detail         5 minutes
document:status         30 seconds
```

Document status has a shorter TTL because the frontend polls it frequently while async parsing is running.

### 4. Cache Invalidation

File:

```text
backend/src/main/java/com/example/ragkb/common/cache/CacheInvalidationService.java
```

This service deletes cache entries after write operations.

Examples:

```text
Create knowledge base -> clear knowledge-base list cache
Update knowledge base -> clear list cache and detail cache
Upload document -> clear document list and knowledge-base count cache
Delete document -> clear document caches and knowledge-base count cache
Parse document -> clear document status/detail/list cache
```

### 5. Cached Read Methods

Files:

```text
backend/src/main/java/com/example/ragkb/knowledgebase/service/KnowledgeBaseService.java
backend/src/main/java/com/example/ragkb/document/service/DocumentService.java
```

Read methods now use `@Cacheable`:

```text
KnowledgeBaseService.listMine
KnowledgeBaseService.getMine
DocumentService.listByKnowledgeBase
DocumentService.getMine
DocumentService.getStatus
```

Workflow:

```text
First request:  Backend -> PostgreSQL -> Redis -> Response
Next request:   Backend -> Redis -> Response
Data changes:   Backend -> PostgreSQL -> Evict Redis cache
```

### 6. Tests

Updated service tests to verify cache invalidation happens after write operations.

Added:

```text
backend/src/test/java/com/example/ragkb/common/cache/CacheInvalidationServiceTests.java
```

Verification:

```text
mvn test
Tests run: 98, Failures: 0, Errors: 0, Skipped: 0
```

## Key Concepts

### Cache Aside

The application checks the cache first. If cache misses, it reads from the database and writes the result to cache.

### Cache Invalidation

When data changes, old cache entries must be removed. Otherwise users may see stale data.

### TTL

TTL means time to live. It controls how long a cache entry can stay in Redis before expiring automatically.

### User Isolation

Cache keys include `userId` so Alice's cached knowledge bases cannot be reused by Bob.

## Files To Read In Order

```text
1. CacheNames.java
2. CacheKeys.java
3. RedisConfig.java
4. CacheInvalidationService.java
5. KnowledgeBaseService.java
6. DocumentService.java
7. DocumentParseService.java
8. CacheInvalidationServiceTests.java
```
