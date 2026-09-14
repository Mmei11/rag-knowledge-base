# Day 10 - Frontend Auth and Knowledge Base UI

## Goal

Connect the React frontend to the Spring Boot backend.

After Day 10, the browser can complete:

```text
Register -> Login -> Create/List/View/Update/Delete knowledge base -> Logout
```

## Files Added

```text
frontend/src/api/http.ts
frontend/src/types/api.ts
```

## Files Modified

```text
frontend/src/App.tsx
frontend/src/App.css
frontend/src/index.css
```

## Frontend Structure

```text
src/
├── api/
│   └── http.ts
├── types/
│   └── api.ts
├── App.tsx
├── App.css
├── index.css
└── main.tsx
```

## Main Features

### Authentication

The frontend supports:

```text
Register
Login
Persist token in localStorage
Restore login after refresh
Logout
Clear session on 401
```

Session storage key:

```text
ragkb.session
```

### Knowledge Base Management

The frontend supports:

```text
Create knowledge base
List my knowledge bases
View selected knowledge base
Edit selected knowledge base
Delete selected knowledge base
```

All knowledge base requests include:

```http
Authorization: Bearer <token>
```

## API Client

`frontend/src/api/http.ts` provides:

```text
authApi.register()
authApi.login()
knowledgeBaseApi.list()
knowledgeBaseApi.create()
knowledgeBaseApi.update()
knowledgeBaseApi.remove()
```

Default backend URL:

```text
http://localhost:8080
```

Override with:

```text
VITE_API_BASE_URL
```

## TypeScript Types

`frontend/src/types/api.ts` defines frontend types matching backend DTOs:

```text
ApiResponse<T>
UserResponse
LoginResponse
RegisterRequest
LoginRequest
KnowledgeBaseResponse
CreateKnowledgeBaseRequest
UpdateKnowledgeBaseRequest
```

## Run

Start backend:

```powershell
cd D:\MAIN\rag-knowledge-base\backend
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
```

Start frontend:

```powershell
cd D:\MAIN\rag-knowledge-base\frontend
npm.cmd run dev -- --host 127.0.0.1
```

Open:

```text
http://127.0.0.1:5173/
```

## Verification

Frontend build:

```powershell
npm.cmd run build
```

Frontend lint:

```powershell
npm.cmd run lint
```

Current result:

```text
build passed
lint passed
```

## Running Services

Current frontend:

```text
http://127.0.0.1:5173/
```

Current backend:

```text
http://127.0.0.1:8080/
```

Backend health:

```text
GET http://127.0.0.1:8080/actuator/health -> 200
```

## Day 10 Key Points

```text
React useState manages form and page state.
useEffect loads knowledge bases after login.
fetch calls backend APIs.
localStorage persists token and user.
Authorization header carries JWT.
401 response clears the frontend session.
DTO types keep frontend and backend contracts aligned.
```
