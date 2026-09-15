package com.example.ragkb.chat.service;

import com.example.ragkb.chat.dto.ChatAnswerResponse;
import com.example.ragkb.chat.dto.ChatMessageResponse;
import com.example.ragkb.chat.dto.ChatReferenceResponse;
import com.example.ragkb.chat.dto.ChatSessionResponse;
import com.example.ragkb.chat.dto.CreateChatSessionRequest;
import com.example.ragkb.chat.dto.SendChatMessageRequest;
import com.example.ragkb.chat.entity.ChatMessage;
import com.example.ragkb.chat.entity.ChatRole;
import com.example.ragkb.chat.entity.ChatSession;
import com.example.ragkb.chat.entity.MessageReference;
import com.example.ragkb.chat.repository.ChatMessageRepository;
import com.example.ragkb.chat.repository.ChatSessionRepository;
import com.example.ragkb.chat.repository.MessageReferenceRepository;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.common.ratelimit.RateLimitService;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.model.ChatModelService;
import com.example.ragkb.rag.prompt.PromptBuilder;
import com.example.ragkb.rag.service.RagRetrievalService;
import com.example.ragkb.security.CurrentUser;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReferenceRepository messageReferenceRepository;
    private final RagRetrievalService ragRetrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatModelService chatModelService;
    private final RateLimitService rateLimitService;

    public ChatService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            MessageReferenceRepository messageReferenceRepository,
            RagRetrievalService ragRetrievalService,
            PromptBuilder promptBuilder,
            ChatModelService chatModelService,
            RateLimitService rateLimitService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messageReferenceRepository = messageReferenceRepository;
        this.ragRetrievalService = ragRetrievalService;
        this.promptBuilder = promptBuilder;
        this.chatModelService = chatModelService;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public ChatSessionResponse createSession(CurrentUser currentUser, CreateChatSessionRequest request) {
        knowledgeBaseRepository.findByIdAndUserId(request.knowledgeBaseId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Knowledge base not found"));

        ChatSession session = new ChatSession(
                currentUser.id(),
                request.knowledgeBaseId(),
                request.normalizedTitle());
        return ChatSessionResponse.from(chatSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(CurrentUser currentUser) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(currentUser.id())
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(CurrentUser currentUser, Long sessionId) {
        findSession(currentUser, sessionId);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional
    public ChatAnswerResponse sendMessage(
            CurrentUser currentUser,
            Long sessionId,
            SendChatMessageRequest request) {
        ChatSession session = findSession(currentUser, sessionId);
        rateLimitService.checkChatLimit(currentUser.id());
        String question = request.question().trim();

        ChatMessage userMessage = chatMessageRepository.saveAndFlush(new ChatMessage(
                session.getId(),
                currentUser.id(),
                ChatRole.USER,
                question));

        VectorSearchResponse searchResponse = ragRetrievalService.search(
                currentUser,
                new VectorSearchRequest(session.getKnowledgeBaseId(), question, request.normalizedTopK()));
        List<RetrievedChunkResponse> retrievedChunks = searchResponse.items();

        String prompt = promptBuilder.build(question, retrievedChunks, request.normalizedPromptMode());
        long modelStartedAt = System.nanoTime();
        String answer = chatModelService.generate(prompt);
        log.info("chat model completed, userId={}, sessionId={}, promptLength={}, referenceCount={}, durationMs={}",
                currentUser.id(),
                sessionId,
                prompt.length(),
                retrievedChunks.size(),
                elapsedMs(modelStartedAt));
        ChatMessage assistantMessage = chatMessageRepository.saveAndFlush(new ChatMessage(
                session.getId(),
                currentUser.id(),
                ChatRole.ASSISTANT,
                answer));

        List<ChatReferenceResponse> references = retrievedChunks.stream()
                .map(ChatReferenceResponse::from)
                .toList();
        saveReferences(assistantMessage.getId(), retrievedChunks);
        session.touch();

        return new ChatAnswerResponse(
                session.getId(),
                ChatMessageResponse.from(userMessage),
                ChatMessageResponse.from(assistantMessage, references),
                references);
    }

    private ChatSession findSession(CurrentUser currentUser, Long sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Chat session not found"));
    }

    private void saveReferences(Long messageId, List<RetrievedChunkResponse> chunks) {
        List<MessageReference> references = chunks.stream()
                .map(chunk -> new MessageReference(
                        messageId,
                        chunk.documentId(),
                        chunk.chunkId(),
                        chunk.similarity()))
                .toList();
        messageReferenceRepository.saveAll(references);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
