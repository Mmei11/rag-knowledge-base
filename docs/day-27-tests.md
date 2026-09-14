# Day 27: Unit Tests and API Tests

## Goal

Strengthen confidence in the main backend workflow and prepare manual API test material for demonstration.

## What Changed

### 1. Extra Chat Service Tests

File:

```text
backend/src/test/java/com/example/ragkb/chat/service/ChatServiceTests.java
```

Added coverage:

```text
shouldListCurrentUserSessions
shouldRejectOtherUsersSessionWhenSendMessage
```

These tests protect two important behaviors:

```text
Only current user's sessions are listed.
Users cannot send messages to sessions that do not belong to them.
```

### 2. API Test Guide

File:

```text
docs/api-test.md
```

This document describes how to manually test the main API workflow:

```text
register
login
create knowledge base
upload document
poll document status
vector search
create chat session
send chat message
rate limit
negative cases
```

### 3. Runnable HTTP Requests

File:

```text
docs/api-requests.http
```

This file contains request examples that can be used with Cursor or VS Code REST Client.

### 4. Test Result Summary

File:

```text
docs/test-result.md
```

This records the current automated test result and the covered areas.

## Verification

Run:

```powershell
cd D:\MAIN\rag-knowledge-base\backend
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd test
```

Expected:

```text
BUILD SUCCESS
```

## Key Learning Points

### Unit Test

Tests one class or one small module, usually with mocks.

Example:

```text
ChatServiceTests
RateLimitServiceTests
TextChunkerTests
```

### Controller Test

Tests HTTP layer behavior with MockMvc.

Example:

```text
AuthControllerTests
KnowledgeBaseControllerTests
DocumentControllerTests
ChatControllerTests
```

### Repository Test

Tests database-related behavior.

Example:

```text
RagRetrievalRepositoryTests
DocumentChunkEmbeddingRepositoryTests
```

### Manual API Test

Tests the application like a real user or frontend would use it.

Example:

```text
Login -> Create KB -> Upload Doc -> Ask Question
```

## Interview Value

For a resume project, tests show that the project is not only a demo page. It has:

```text
business rule protection
permission isolation tests
RAG core workflow tests
Redis cache and rate limit tests
manual API verification material
```
