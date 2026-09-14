# Day 21 - Frontend Foundation

## Goal

Turn the frontend from a single-page component into a maintainable React application structure.

## What Changed

Day 21 added these frontend foundations:

1. React Router for page routing.
2. Axios-based HTTP client.
3. Centralized login session management.
4. Automatic token attachment for authenticated requests.
5. Automatic 401 handling and redirect to login.
6. Basic authenticated layout with top navigation.
7. Page-level split for login/register and knowledge base management.

## New Dependencies

```text
react-router-dom
axios
```

## Main File Structure

```text
src/
  api/
    http.ts
  auth/
    AuthContext.tsx
    auth-context.ts
    session.ts
    useAuth.ts
  components/
    ProtectedRoute.tsx
    StatusMessage.tsx
  layout/
    AppLayout.tsx
  pages/
    AuthPage.tsx
    KnowledgeBasePage.tsx
  utils/
    date.ts
  App.tsx
  main.tsx
```

## Request Flow

Authenticated API request:

```text
Page component
  -> api/http.ts
  -> Axios client
  -> read token from AuthContext/session storage
  -> add Authorization: Bearer <token>
  -> backend API
```

If backend returns 401:

```text
api/http.ts
  -> unauthorizedHandler
  -> AuthContext.signOut()
  -> clear localStorage
  -> redirect to /login
```

## Route Flow

```text
/
  -> redirect by login status

/login
  -> AuthPage

/knowledge-bases
  -> ProtectedRoute
  -> AppLayout
  -> KnowledgeBasePage
```

## Key Design

`App.tsx` no longer contains page business logic. It only declares routes.

Page logic now lives in:

```text
AuthPage.tsx
KnowledgeBasePage.tsx
```

Shared state lives in:

```text
AuthContext.tsx
session.ts
```

Shared utilities live in:

```text
http.ts
date.ts
StatusMessage.tsx
```

This makes later Day22-Day24 work easier, because document pages and chat pages can be added as new routes instead of making `App.tsx` larger.

## Verification

```text
npm run build
```

Result:

```text
✓ built successfully
```
