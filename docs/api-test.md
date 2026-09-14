# API Test Guide

This guide covers the main API workflow for the RAG knowledge base platform.

## Prerequisites

Start infrastructure:

```powershell
docker compose up -d postgres redis
```

Start backend:

```powershell
cd D:\MAIN\rag-knowledge-base\backend
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
```

Backend base URL:

```text
http://localhost:8080
```

Health check:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/actuator/health
```

Expected result:

```text
HTTP 200
```

## Main Workflow

### 1. Register

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "alice",
  "password": "Password123",
  "nickname": "Alice"
}
```

Expected:

```text
HTTP 200
code = 0
```

### 2. Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "alice",
  "password": "Password123"
}
```

Expected:

```text
HTTP 200
code = 0
data.token exists
```

Use the token in later requests:

```text
Authorization: Bearer {token}
```

### 3. Create Knowledge Base

```http
POST /api/knowledge-bases
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Java Notes",
  "description": "Spring Boot and RAG learning notes"
}
```

Expected:

```text
HTTP 200
data.id exists
```

### 4. List Knowledge Bases

```http
GET /api/knowledge-bases
Authorization: Bearer {token}
```

Expected:

```text
HTTP 200
data is an array
```

### 5. Upload Document

```http
POST /api/knowledge-bases/{knowledgeBaseId}/documents
Authorization: Bearer {token}
Content-Type: multipart/form-data

file=@sample.txt
```

Expected:

```text
HTTP 200
data.status = PENDING
```

### 6. Poll Document Status

```http
GET /api/documents/{documentId}/status
Authorization: Bearer {token}
```

Expected final status:

```text
COMPLETED
```

Possible statuses:

```text
PENDING
PROCESSING
COMPLETED
FAILED
```

### 7. Vector Search

```http
POST /api/search/vector
Authorization: Bearer {token}
Content-Type: application/json

{
  "knowledgeBaseId": 1,
  "question": "What is Spring Boot?",
  "topK": 5
}
```

Expected:

```text
HTTP 200
data.items is an array
```

### 8. Create Chat Session

```http
POST /api/chat/sessions
Authorization: Bearer {token}
Content-Type: application/json

{
  "knowledgeBaseId": 1,
  "title": "Java Q&A"
}
```

Expected:

```text
HTTP 200
data.id exists
```

### 9. Send Chat Message

```http
POST /api/chat/sessions/{sessionId}/messages
Authorization: Bearer {token}
Content-Type: application/json

{
  "question": "What is Spring Boot?",
  "topK": 5
}
```

Expected:

```text
HTTP 200
data.userMessage.role = USER
data.assistantMessage.role = ASSISTANT
data.references is an array
```

### 10. Verify Rate Limit

Send more than 20 chat requests within 60 seconds using the same user.

Expected:

```text
HTTP 429
code = 42900
message indicates the request is rate limited
```

## Negative Cases

### Missing Token

```http
GET /api/knowledge-bases
```

Expected:

```text
HTTP 401
code = 40100
```

### Invalid Register Request

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "a!",
  "password": "123"
}
```

Expected:

```text
HTTP 400
code = 40000
data.username exists
data.password exists
```

### Other User Resource Isolation

Use user B's token to access user A's knowledge base, document, or chat session.

Expected:

```text
HTTP 404
code = 40400
```

The API intentionally returns "not found" instead of "belongs to another user" to avoid leaking resource existence.

## Tools

Recommended tools:

```text
Cursor REST Client / VS Code REST Client
Apifox
Postman
DBeaver for PostgreSQL inspection
Redis Insight for Redis inspection
```

The companion file `docs/api-requests.http` contains runnable request examples.
