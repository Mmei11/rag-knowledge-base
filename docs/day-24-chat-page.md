# Day 24 - Chat Page

## Goal

Add the frontend intelligent Q&A page and connect it to the backend chat API.

## What Changed

### API Types

File:

```text
frontend/src/types/api.ts
```

Added:

```text
ChatRole
CreateChatSessionRequest
ChatSessionResponse
SendChatMessageRequest
ChatReferenceResponse
ChatMessageResponse
ChatAnswerResponse
```

### HTTP API

File:

```text
frontend/src/api/http.ts
```

Added:

```text
chatApi.createSession()
chatApi.listSessions()
chatApi.listMessages()
chatApi.sendMessage()
```

### Route

Files:

```text
frontend/src/App.tsx
frontend/src/layout/AppLayout.tsx
```

Added route:

```text
/chat
```

Added navigation item:

```text
智能问答
```

### Page

File:

```text
frontend/src/pages/ChatPage.tsx
```

Features:

```text
knowledge base selection
chat session list
create chat session
load session messages
question input
answer loading state
message stream display
answer references
click reference to preview chunk content
```

### Styles

File:

```text
frontend/src/App.css
```

Added styles for:

```text
chat page grid
chat sidebar
session list
message stream
message bubbles
question form
reference buttons
reference preview panel
responsive layout
```

## Backend API Used

| Frontend Method | Backend API |
| --- | --- |
| `chatApi.createSession()` | `POST /api/chat/sessions` |
| `chatApi.listSessions()` | `GET /api/chat/sessions` |
| `chatApi.listMessages()` | `GET /api/chat/sessions/{sessionId}/messages` |
| `chatApi.sendMessage()` | `POST /api/chat/sessions/{sessionId}/messages` |

## Chat Flow

```text
Open /chat
  -> load knowledge bases
  -> load chat sessions
  -> select or create session
  -> load messages
  -> submit question
  -> backend retrieves chunks
  -> backend builds prompt
  -> backend calls chat model
  -> frontend receives user message, assistant message, references
  -> message list updates
  -> click reference
  -> preview chunk content
```

## Important Frontend State

```text
knowledgeBases
sessions
messages
selectedKnowledgeBaseId
selectedSessionId
sessionTitle
question
activeReference
loading
answering
error
message
```

## Current Limitation

The backend still uses `MockChatModelService`, so answers are simulated. The frontend flow is ready for a real chat model later.

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
