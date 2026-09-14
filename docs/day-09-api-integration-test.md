# Day 09 - API Integration Test

## Goal

Run the real backend service and verify the full HTTP flow:

```text
Register -> Login -> Get JWT -> Create/List/Get/Update/Delete knowledge base
```

Day 9 focuses on real integration rather than adding new business code.

## Services

Start PostgreSQL and Redis:

```powershell
docker compose up -d
```

Check status:

```powershell
docker compose ps
```

Expected services:

```text
rag-kb-postgres   healthy   localhost:5432
rag-kb-redis      healthy   localhost:6379
```

## Backend

Start backend:

```powershell
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
```

Health check:

```http
GET http://localhost:8080/actuator/health
```

Expected:

```text
HTTP 200
```

## API Flow

### 1. Register

```http
POST http://localhost:8080/api/auth/register
Content-Type: application/json
```

```json
{
  "username": "day9_20260729161354",
  "password": "Password123",
  "nickname": "Day9 Test User"
}
```

Expected:

```text
code = 0
```

### 2. Login

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json
```

```json
{
  "username": "day9_20260729161354",
  "password": "Password123"
}
```

Expected:

```text
code = 0
tokenType = Bearer
token is returned
```

### 3. Access Knowledge Bases Without Token

```http
GET http://localhost:8080/api/knowledge-bases
```

Expected:

```text
HTTP 401
code = 40100
```

### 4. Create Knowledge Base

```http
POST http://localhost:8080/api/knowledge-bases
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "Day9 Knowledge Base 20260729161354",
  "description": "Created during Day 9 API integration test"
}
```

Expected:

```text
code = 0
documentCount = 0
```

### 5. List Knowledge Bases

```http
GET http://localhost:8080/api/knowledge-bases
Authorization: Bearer <token>
```

Expected:

```text
The created knowledge base appears in the list.
```

### 6. Get Knowledge Base Detail

```http
GET http://localhost:8080/api/knowledge-bases/{id}
Authorization: Bearer <token>
```

Expected:

```text
The created knowledge base detail is returned.
```

### 7. Update Knowledge Base

```http
PUT http://localhost:8080/api/knowledge-bases/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "Day9 Updated Knowledge Base 20260729161354",
  "description": "Updated during Day 9 API integration test"
}
```

Expected:

```text
Updated name and description are returned.
```

### 8. Delete Knowledge Base

```http
DELETE http://localhost:8080/api/knowledge-bases/{id}
Authorization: Bearer <token>
```

Expected:

```text
code = 0
```

### 9. Get Deleted Knowledge Base

```http
GET http://localhost:8080/api/knowledge-bases/{id}
Authorization: Bearer <token>
```

Expected:

```text
HTTP 404
message = 知识库不存在
```

## Actual Result

```text
username                       day9_20260729161354
registerCode                   0
loginCode                      0
tokenType                      Bearer
tokenLength                    201
unauthenticatedListStatus      401
createdKnowledgeBaseId         1
createdName                    Day9 Knowledge Base 20260729161354
listCount                      1
detailName                     Day9 Knowledge Base 20260729161354
updatedName                    Day9 Updated Knowledge Base 20260729161354
deleteCode                     0
getAfterDeleteStatus           404
```

## Database Verification

Query result:

```text
users:
id = 2
username = day9_20260729161354
nickname = Day9 Test User
password_is_hashed = true

knowledge_bases:
0 rows after delete
```

## Troubleshooting Note

If the backend start command looks stuck, first check:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
```

In this run, the backend had already started successfully while the process output was still attached.

## What Day 9 Proved

```text
PostgreSQL and Redis can run through Docker Compose.
Spring Boot can connect to PostgreSQL and Redis.
Registration writes real data to users.
Password hashes are not stored as plain text.
Login returns a usable JWT.
/api/** protection returns 401 without token.
JWT allows access to protected knowledge base APIs.
Knowledge base CRUD works through real HTTP requests.
Delete really removes the knowledge base row.
```
