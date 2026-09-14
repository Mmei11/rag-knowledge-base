# Day 26: Rate Limit, Logging, Error Handling

## Goal

Improve stability and observability for the RAG chat workflow.

## What Changed

### 1. Chat Rate Limit

Files:

```text
backend/src/main/java/com/example/ragkb/common/ratelimit/ChatRateLimitProperties.java
backend/src/main/java/com/example/ragkb/common/ratelimit/RateLimitService.java
backend/src/main/java/com/example/ragkb/chat/service/ChatService.java
```

The chat answer API now checks a Redis counter before running the RAG workflow.

Default rule:

```text
20 chat requests / 60 seconds / user
```

Configuration:

```yaml
app:
  rate-limit:
    chat:
      enabled: ${CHAT_RATE_LIMIT_ENABLED:true}
      limit: ${CHAT_RATE_LIMIT_LIMIT:20}
      window-seconds: ${CHAT_RATE_LIMIT_WINDOW_SECONDS:60}
```

Redis key example:

```text
ragkb:rate-limit:chat:user:1
```

Workflow:

```text
User sends question
-> Redis INCR user counter
-> First request sets key expiration
-> If count > limit, throw RATE_LIMITED
-> Otherwise continue RAG workflow
```

### 2. 429 Error Code

File:

```text
backend/src/main/java/com/example/ragkb/common/exception/ErrorCode.java
```

Added:

```text
RATE_LIMITED -> 42900 -> HTTP 429 Too Many Requests
```

### 3. Request Logging

File:

```text
backend/src/main/java/com/example/ragkb/common/logging/RequestLogAspect.java
```

This aspect logs every controller request:

```text
HTTP method
URI
controller handler
durationMs
success/failure
exception class on failure
```

It does not log:

```text
Authorization header
JWT token
request body
prompt content
API key
```

### 4. Vector Search Timing

File:

```text
backend/src/main/java/com/example/ragkb/rag/service/RagRetrievalService.java
```

The retrieval service now logs:

```text
userId
knowledgeBaseId
topK
resultCount
embeddingMs
searchMs
```

### 5. Chat Model Timing

File:

```text
backend/src/main/java/com/example/ragkb/chat/service/ChatService.java
```

The chat service now logs:

```text
userId
sessionId
promptLength
referenceCount
durationMs
```

Only prompt length is logged, not the prompt text.

### 6. Tests

Added:

```text
backend/src/test/java/com/example/ragkb/common/ratelimit/RateLimitServiceTests.java
```

Updated:

```text
backend/src/test/java/com/example/ragkb/chat/service/ChatServiceTests.java
```

Test coverage verifies:

```text
first chat request sets Redis TTL
request above limit is rejected
disabled rate limit skips Redis
ChatService calls RateLimitService before completing chat flow
```

Verification:

```text
mvn test
Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
```

## Files To Read In Order

```text
1. application.yml
2. ErrorCode.java
3. ChatRateLimitProperties.java
4. RateLimitService.java
5. ChatService.java
6. RagRetrievalService.java
7. RequestLogAspect.java
8. RateLimitServiceTests.java
9. ChatServiceTests.java
```

## Key Concepts

### Redis Counter Rate Limit

Use Redis `INCR` to count how many requests a user has made in a time window.

### TTL

The first request creates a key and sets an expiration time. When the key expires, the user's request count resets.

### Observability

Logs help answer operational questions:

```text
Which request is slow?
Is embedding slow?
Is vector search slow?
Is model generation slow?
Which user triggered too many requests?
```

### Sensitive Data Protection

Logs should avoid secrets and large private content. This project logs metadata and timing, not tokens, API keys, request bodies, or full prompts.
