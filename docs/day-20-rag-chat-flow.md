# Day 20 - RAG Chat Flow

## Goal

Build the first complete RAG question-answering flow:

1. Create a chat session under a knowledge base.
2. Save the user's question as a chat message.
3. Retrieve similar document chunks from pgvector.
4. Build a prompt from the question and retrieved chunks.
5. Call the chat model service.
6. Save the assistant answer.
7. Save the chunk references used by the answer.
8. Return the answer with citation-like references.

## New API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/chat/sessions` | Create a chat session |
| `GET` | `/api/chat/sessions` | List current user's chat sessions |
| `GET` | `/api/chat/sessions/{sessionId}/messages` | List messages in one session |
| `POST` | `/api/chat/sessions/{sessionId}/messages` | Ask a question in one session |

## New Backend Package

`com.example.ragkb.chat`

| Folder | Role |
| --- | --- |
| `entity` | Maps `chat_sessions`, `chat_messages`, `message_references` |
| `repository` | Provides database access for chat tables |
| `dto` | Defines request and response objects |
| `service` | Orchestrates the RAG chat flow |
| `controller` | Exposes chat REST APIs |

## Main Flow

`ChatController.sendMessage`

calls:

`ChatService.sendMessage`

then:

1. Load the chat session and verify it belongs to the current user.
2. Save a `USER` message.
3. Call `RagRetrievalService.search` to get related chunks.
4. Call `PromptBuilder.build` to assemble the LLM prompt.
5. Call `ChatModelService.generate` to get the answer.
6. Save an `ASSISTANT` message.
7. Save `message_references` for retrieved chunks.
8. Return `ChatAnswerResponse`.

## Important Design Point

Day 18 only solved retrieval:

`question -> embedding -> similar chunks`

Day 19 only solved prompt and model abstraction:

`question + chunks -> prompt -> answer`

Day 20 connects them into a product-level chat feature:

`session -> user message -> retrieval -> prompt -> model -> assistant message -> references`

## Current Limitation

The chat model is still `MockChatModelService`, so the answer is simulated. This is intentional: it lets us verify the whole backend flow before introducing a real LLM API key, network failures, cost, and streaming responses.
