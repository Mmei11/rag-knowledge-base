# Day 08 - Knowledge Base CRUD

## Goal

Build the first protected business resource: knowledge bases.

After Day 8, logged-in users can create, list, view, update, and delete their own knowledge bases.

## Files Added

```text
backend/src/main/java/com/example/ragkb/knowledgebase/
├── controller/KnowledgeBaseController.java
├── dto/CreateKnowledgeBaseRequest.java
├── dto/KnowledgeBaseResponse.java
├── dto/UpdateKnowledgeBaseRequest.java
├── entity/KnowledgeBase.java
├── repository/KnowledgeBaseRepository.java
└── service/KnowledgeBaseService.java
```

Test files:

```text
backend/src/test/java/com/example/ragkb/knowledgebase/
├── controller/KnowledgeBaseControllerTests.java
└── service/KnowledgeBaseServiceTests.java
```

## API List

```http
POST   /api/knowledge-bases
GET    /api/knowledge-bases
GET    /api/knowledge-bases/{id}
PUT    /api/knowledge-bases/{id}
DELETE /api/knowledge-bases/{id}
```

All of these APIs require:

```http
Authorization: Bearer <token>
```

## Request Flow

```text
HTTP request with JWT
        |
        v
JwtAuthenticationFilter
        |
        v
SecurityContext stores CurrentUser
        |
        v
KnowledgeBaseController
        |
        v
KnowledgeBaseService
        |
        v
KnowledgeBaseRepository
        |
        v
knowledge_bases table
```

## Main Responsibilities

| File | Responsibility |
| --- | --- |
| `KnowledgeBase.java` | Maps Java object fields to the `knowledge_bases` table. |
| `KnowledgeBaseRepository.java` | Provides database queries for knowledge bases. |
| `CreateKnowledgeBaseRequest.java` | Defines the create request body. |
| `UpdateKnowledgeBaseRequest.java` | Defines the update request body. |
| `KnowledgeBaseResponse.java` | Defines safe response data. |
| `KnowledgeBaseService.java` | Implements ownership checks and CRUD business logic. |
| `KnowledgeBaseController.java` | Exposes REST APIs and reads `CurrentUser` from authentication. |

## Ownership Rule

Knowledge bases are private user resources.

The service never loads a knowledge base only by `id`. It uses:

```java
findByIdAndUserId(id, currentUser.id())
```

This ensures:

```text
User A cannot view, update, or delete User B's knowledge base.
```

## Duplicate Name Rule

One user cannot create two knowledge bases with the same name:

```text
same user_id + same name -> conflict
```

Different users can use the same knowledge base name.

## Verification

Run backend tests:

```powershell
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd test
```

## Suggested Reading Order

1. `CreateKnowledgeBaseRequest.java`
2. `UpdateKnowledgeBaseRequest.java`
3. `KnowledgeBaseResponse.java`
4. `KnowledgeBase.java`
5. `KnowledgeBaseRepository.java`
6. `KnowledgeBaseService.java`
7. `KnowledgeBaseController.java`
8. `KnowledgeBaseServiceTests.java`
9. `KnowledgeBaseControllerTests.java`
