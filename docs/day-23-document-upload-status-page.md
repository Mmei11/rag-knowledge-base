# Day 23 - Document Upload And Status Page

## Goal

Add frontend support for document upload, document list, status display, polling, reprocess, and delete.

## What Changed

### API Types

File:

```text
frontend/src/types/api.ts
```

Added:

```text
DocumentStatus
DocumentResponse
DocumentStatusResponse
```

### HTTP API

File:

```text
frontend/src/api/http.ts
```

Added:

```text
documentApi.listByKnowledgeBase()
documentApi.upload()
documentApi.getStatus()
documentApi.reprocess()
documentApi.remove()
```

Upload uses `FormData` and supports upload progress through Axios.

### Document Panel

File:

```text
frontend/src/components/DocumentPanel.tsx
```

Features:

```text
file selection
file extension validation
file size validation
upload progress
document list
document status badge
status polling every 3 seconds
reprocess failed document
delete document
```

### Knowledge Base Detail Integration

File:

```text
frontend/src/pages/KnowledgeBasePage.tsx
```

The document panel is shown in the selected knowledge base detail area:

```text
KnowledgeBasePage
  -> selectedKnowledgeBase
  -> DocumentPanel
```

### Styles

File:

```text
frontend/src/App.css
```

Added styles for:

```text
document section
upload form
upload progress bar
document list item
document status badge
document action buttons
```

## Backend API Used

| Frontend Method | Backend API |
| --- | --- |
| `documentApi.upload()` | `POST /api/knowledge-bases/{knowledgeBaseId}/documents` |
| `documentApi.listByKnowledgeBase()` | `GET /api/knowledge-bases/{knowledgeBaseId}/documents` |
| `documentApi.getStatus()` | `GET /api/documents/{documentId}/status` |
| `documentApi.reprocess()` | `POST /api/documents/{documentId}/reprocess` |
| `documentApi.remove()` | `DELETE /api/documents/{documentId}` |

## Status Flow

After upload:

```text
PENDING
  -> PROCESSING
  -> COMPLETED
```

If parsing fails:

```text
FAILED
```

The frontend polls active documents:

```text
PENDING or PROCESSING
  -> GET /api/documents/{documentId}/status every 3 seconds
```

## File Validation

Allowed extensions:

```text
pdf
txt
md
markdown
```

Max size:

```text
10MB
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
