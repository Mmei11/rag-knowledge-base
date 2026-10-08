package com.example.ragkb.chat.service;

import com.example.ragkb.chat.dto.ChatAnswerResponse;
import com.example.ragkb.chat.dto.ChatMessageResponse;
import com.example.ragkb.chat.dto.ChatReferenceResponse;
import com.example.ragkb.chat.dto.ChatSessionResponse;
import com.example.ragkb.chat.dto.CreateChatSessionRequest;
import com.example.ragkb.chat.dto.SendChatMessageRequest;
import com.example.ragkb.chat.dto.UpdateChatSessionRequest;
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
import com.example.ragkb.document.entity.DocumentStatus;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.model.ChatModelService;
import com.example.ragkb.rag.prompt.PromptBuilder;
import com.example.ragkb.rag.service.RagRetrievalService;
import com.example.ragkb.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReferenceRepository messageReferenceRepository;
    private final RagRetrievalService ragRetrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatModelService chatModelService;
    private final RateLimitService rateLimitService;

    public ChatService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            MessageReferenceRepository messageReferenceRepository,
            RagRetrievalService ragRetrievalService,
            PromptBuilder promptBuilder,
            ChatModelService chatModelService,
            RateLimitService rateLimitService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
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

    @Transactional
    public ChatSessionResponse renameSession(CurrentUser currentUser, Long sessionId, UpdateChatSessionRequest request) {
        ChatSession session = findSession(currentUser, sessionId);
        session.rename(request.title().trim());
        return ChatSessionResponse.from(session);
    }

    @Transactional
    public void deleteSession(CurrentUser currentUser, Long sessionId) {
        ChatSession session = findSession(currentUser, sessionId);
        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.delete(session);
    }

    @Transactional
    public void clearMessages(CurrentUser currentUser, Long sessionId) {
        ChatSession session = findSession(currentUser, sessionId);
        chatMessageRepository.deleteBySessionId(sessionId);
        session.touch();
    }

    @Transactional(readOnly = true)
    public List<String> suggestQuestions(CurrentUser currentUser, Long sessionId) {
        ChatSession session = findSession(currentUser, sessionId);
        List<String> titles = documentRepository
                .findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(session.getKnowledgeBaseId(), currentUser.id())
                .stream()
                .filter(document -> document.getStatus() == DocumentStatus.COMPLETED)
                .map(document -> document.getFileName().replaceFirst("\\.[^.]+$", ""))
                .distinct()
                .limit(3)
                .toList();
        if (titles.isEmpty()) {
            String name = knowledgeBaseRepository.findByIdAndUserId(session.getKnowledgeBaseId(), currentUser.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Knowledge base not found"))
                    .getName();
            return List.of("“" + name + "”主要包含哪些内容？", "“" + name + "”有哪些核心概念？", "如何开始学习“" + name + "”？");
        }
        List<String> questions = new java.util.ArrayList<>(titles.stream()
                .map(title -> "请概括《" + title + "》的核心内容？")
                .toList());
        if (questions.size() < 3) {
            questions.add("《" + titles.getFirst() + "》有哪些关键要点？");
        }
        if (questions.size() < 3) {
            questions.add("《" + titles.getFirst() + "》在实际场景中如何应用？");
        }
        return questions;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(CurrentUser currentUser, Long sessionId) {
        findSession(currentUser, sessionId);
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (messages.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ChatReferenceResponse>> referencesByMessage = messageReferenceRepository
                .findDetailsByMessageIdIn(messages.stream().map(ChatMessage::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        reference -> reference.getMessageId(),
                        Collectors.mapping(ChatReferenceResponse::from, Collectors.toList())));

        return messages.stream()
                .map(message -> ChatMessageResponse.from(
                        message,
                        referencesByMessage.getOrDefault(message.getId(), List.of())))
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
        List<ChatMessage> history = recentHistory(sessionId);

        ChatMessage userMessage = chatMessageRepository.saveAndFlush(new ChatMessage(
                session.getId(),
                currentUser.id(),
                ChatRole.USER,
                question));

        VectorSearchResponse searchResponse = ragRetrievalService.search(
                currentUser,
                new VectorSearchRequest(
                        session.getKnowledgeBaseId(),
                        searchQuestion(question, history),
                        request.normalizedTopK(),
                        request.similarityThreshold(),
                        request.normalizedDocumentIds(),
                        request.startDate(),
                        request.endDate()));
        List<RetrievedChunkResponse> retrievedChunks = searchResponse.items();

        String prompt = promptBuilder.build(question, retrievedChunks, request.normalizedPromptMode(), history);
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

    @Transactional
    public ChatStreamPreparation prepareStreamingMessage(
            CurrentUser currentUser,
            Long sessionId,
            SendChatMessageRequest request) {
        ChatSession session = findSession(currentUser, sessionId);
        rateLimitService.checkChatLimit(currentUser.id());
        String question = request.question().trim();
        List<ChatMessage> history = recentHistory(sessionId);

        ChatMessage userMessage = chatMessageRepository.saveAndFlush(new ChatMessage(
                session.getId(),
                currentUser.id(),
                ChatRole.USER,
                question));

        VectorSearchResponse searchResponse = ragRetrievalService.search(
                currentUser,
                new VectorSearchRequest(
                        session.getKnowledgeBaseId(),
                        searchQuestion(question, history),
                        request.normalizedTopK(),
                        request.similarityThreshold(),
                        request.normalizedDocumentIds(),
                        request.startDate(),
                        request.endDate()));
        List<RetrievedChunkResponse> retrievedChunks = searchResponse.items();
        String prompt = promptBuilder.build(question, retrievedChunks, request.normalizedPromptMode(), history);

        return new ChatStreamPreparation(
                session.getId(),
                currentUser.id(),
                prompt,
                retrievedChunks,
                ChatMessageResponse.from(userMessage),
                retrievedChunks.stream().map(ChatReferenceResponse::from).toList());
    }

    public String generateStreamingAnswer(
            ChatStreamPreparation preparation,
            Consumer<String> onDelta) {
        StringBuilder answer = new StringBuilder();
        long modelStartedAt = System.nanoTime();
        chatModelService.generateStream(preparation.prompt(), delta -> {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            answer.append(delta);
            onDelta.accept(delta);
        });
        if (answer.isEmpty()) {
            throw new IllegalStateException("模型没有返回回答内容");
        }
        log.info("chat model stream completed, userId={}, sessionId={}, promptLength={}, referenceCount={}, durationMs={}",
                preparation.userId(),
                preparation.sessionId(),
                preparation.prompt().length(),
                preparation.retrievedChunks().size(),
                elapsedMs(modelStartedAt));
        return answer.toString();
    }

    @Transactional
    public ChatAnswerResponse completeStreamingMessage(
            ChatStreamPreparation preparation,
            String answer) {
        ChatSession session = chatSessionRepository
                .findByIdAndUserId(preparation.sessionId(), preparation.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Chat session not found"));
        ChatMessage assistantMessage = chatMessageRepository.saveAndFlush(new ChatMessage(
                session.getId(),
                preparation.userId(),
                ChatRole.ASSISTANT,
                answer));
        saveReferences(assistantMessage.getId(), preparation.retrievedChunks());
        session.touch();

        return new ChatAnswerResponse(
                session.getId(),
                preparation.userMessage(),
                ChatMessageResponse.from(assistantMessage, preparation.references()),
                preparation.references());
    }

    private ChatSession findSession(CurrentUser currentUser, Long sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Chat session not found"));
    }

    private List<ChatMessage> recentHistory(Long sessionId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatMessage> completed = new java.util.ArrayList<>();
        for (int index = 0; index + 1 < messages.size(); index++) {
            ChatMessage user = messages.get(index);
            ChatMessage assistant = messages.get(index + 1);
            if (user.getRole() == ChatRole.USER && assistant.getRole() == ChatRole.ASSISTANT) {
                completed.add(user);
                completed.add(assistant);
                index++;
            }
        }
        return completed.subList(Math.max(0, completed.size() - 6), completed.size());
    }

    private String searchQuestion(String question, List<ChatMessage> history) {
        if (history.isEmpty() || question.length() > 60
                || !question.matches(".*(它|这个|该|上述|前面|刚才|其中|继续|还有|具体|哪些|他们|其|上面|详细|展开|例子|呢).*")) {
            return question;
        }
        String previous = history.get(history.size() - 2).getContent();
        return previous.substring(0, Math.min(previous.length(), 200)) + " " + question;
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
