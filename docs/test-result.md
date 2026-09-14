# Test Result

## Automated Test Command

```powershell
cd D:\MAIN\rag-knowledge-base\backend
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd test
```

## Current Result

```text
Tests run: 104
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## Covered Areas

```text
User registration and login
JWT generation and validation
Security protected routes
Unified API response and exception handling
Knowledge base CRUD and ownership isolation
Document upload, validation, query, delete, and status flow
Text parsing and chunking
Async document processing
Embedding abstraction and mock embedding
pgvector formatting and vector storage
Vector search service and controller
Prompt building
Mock chat model
RAG chat session, message, reference flow
Redis cache invalidation
Redis cache serialization
Redis counter rate limit
```

## Day 27 Additions

Added extra ChatService coverage:

```text
list current user's chat sessions
reject sending a message to another user's session
```

Added API testing documents:

```text
docs/api-test.md
docs/api-requests.http
docs/test-result.md
```

## Manual API Test Checklist

```text
[ ] Health check returns 200
[ ] Register succeeds
[ ] Login returns JWT token
[ ] Create knowledge base succeeds
[ ] List knowledge bases returns created item
[ ] Upload document returns PENDING
[ ] Document status becomes COMPLETED
[ ] Vector search returns retrieved chunks
[ ] Create chat session succeeds
[ ] Send chat message returns user message, assistant message, and references
[ ] Missing token returns 40100
[ ] Invalid request returns 40000
[ ] Other user's resource returns 40400
[ ] Too many chat requests return 42900
```

## Notes

Controller and service tests run without depending on a live frontend.

Repository tests for pgvector-related behavior require the configured PostgreSQL test context to be available in the local environment.
