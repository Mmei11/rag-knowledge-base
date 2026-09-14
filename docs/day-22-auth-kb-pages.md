# Day 22 - Auth And Knowledge Base Pages

## Goal

Complete the frontend pages for authentication and knowledge base management.

## Pages

| Page | File | Purpose |
| --- | --- | --- |
| Login/Register | `frontend/src/pages/AuthPage.tsx` | User registration and login |
| Knowledge Bases | `frontend/src/pages/KnowledgeBasePage.tsx` | List, create, update, and delete knowledge bases |

## API Mapping

| Frontend Method | Backend API |
| --- | --- |
| `authApi.register()` | `POST /api/auth/register` |
| `authApi.login()` | `POST /api/auth/login` |
| `knowledgeBaseApi.list()` | `GET /api/knowledge-bases` |
| `knowledgeBaseApi.create()` | `POST /api/knowledge-bases` |
| `knowledgeBaseApi.update()` | `PUT /api/knowledge-bases/{id}` |
| `knowledgeBaseApi.remove()` | `DELETE /api/knowledge-bases/{id}` |

## Form Validation

Day 22 added client-side validation before sending requests.

### Auth Form

Rules:

```text
username: 3-50 characters, letters/numbers/underscore only
password: 8-72 characters
nickname: max 100 characters
```

HTML input attributes:

```text
required
minLength
maxLength
pattern
```

Manual validation function:

```text
validateAuthForm()
```

### Knowledge Base Form

Rules:

```text
name: required, max 100 characters
description: max 1000 characters
```

Manual validation function:

```text
validateKnowledgeBaseForm()
```

## Key Frontend Ideas

### Controlled Inputs

The input value comes from React state:

```text
value={form.name}
```

When the user types, state is updated:

```text
onChange={event => setForm(...)}
```

This makes the page state the single source of truth.

### Submit Handling

Forms use:

```text
onSubmit={handleCreate}
```

Inside submit handlers:

```text
event.preventDefault()
```

This prevents the browser from refreshing the page.

### Status Message

Both pages use:

```text
StatusMessage
```

It shows:

```text
error -> red message
message -> green message
empty -> renders nothing
```

## Verification

```text
npm run lint
npm run build
```

Result:

```text
lint passed
build passed
```
