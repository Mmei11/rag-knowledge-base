package com.example.ragkb.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.chat.dto.ChatAnswerResponse;
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
import com.example.ragkb.common.ratelimit.RateLimitService;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.model.ChatModelService;
import com.example.ragkb.rag.prompt.PromptBuilder;
import com.example.ragkb.rag.prompt.PromptMode;
import com.example.ragkb.rag.service.RagRetrievalService;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.UserRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MessageReferenceRepository messageReferenceRepository;

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ChatModelService chatModelService;

    @Mock
    private RateLimitService rateLimitService;

    @Test
    void shouldCreateSession() {
        ChatService service = service();
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase()));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            fillSession(session, 100L);
            return session;
        });

        ChatSessionResponse response = service.createSession(
                currentUser,
                new CreateChatSessionRequest(10L, " Java Q&A "));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.knowledgeBaseId()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("Java Q&A");
    }

    @Test
    void shouldRejectOtherUsersKnowledgeBaseWhenCreateSession() {
        ChatService service = service();
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSession(
                currentUser,
                new CreateChatSessionRequest(10L, "Java Q&A")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Knowledge base not found");
    }

    @Test
    void shouldListCurrentUserSessions() {
        ChatService service = service();
        ChatSession session = session();
        when(chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(session));

        List<ChatSessionResponse> responses = service.listSessions(currentUser);

        assertThat(responses).singleElement()
                .satisfies(response -> {
                    assertThat(response.id()).isEqualTo(100L);
                    assertThat(response.knowledgeBaseId()).isEqualTo(10L);
                    assertThat(response.title()).isEqualTo("Java Q&A");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendMessageAndSaveAnswerReferences() {
        ChatService service = service();
        ChatSession session = session();
        RetrievedChunkResponse chunk = new RetrievedChunkResponse(
                200L,
                300L,
                0,
                "Spring Boot supports dependency injection.",
                0.91);
        when(chatSessionRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            Long id = message.getRole() == ChatRole.USER ? 500L : 501L;
            fillMessage(message, id);
            return message;
        });
        when(ragRetrievalService.search(any(CurrentUser.class), any(VectorSearchRequest.class)))
                .thenReturn(new VectorSearchResponse(10L, "What is DI?", 5, List.of(chunk)));
        when(promptBuilder.build("What is DI?", List.of(chunk), PromptMode.KB_FIRST)).thenReturn("prompt");
        when(chatModelService.generate("prompt")).thenReturn("DI is a way to provide dependencies.");

        ChatAnswerResponse response = service.sendMessage(
                currentUser,
                100L,
                new SendChatMessageRequest(" What is DI? ", null));

        assertThat(response.sessionId()).isEqualTo(100L);
        assertThat(response.userMessage().id()).isEqualTo(500L);
        assertThat(response.userMessage().role()).isEqualTo(ChatRole.USER);
        assertThat(response.userMessage().content()).isEqualTo("What is DI?");
        assertThat(response.assistantMessage().id()).isEqualTo(501L);
        assertThat(response.assistantMessage().role()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(response.references()).hasSize(1);
        assertThat(response.references().getFirst().chunkId()).isEqualTo(200L);

        ArgumentCaptor<List<MessageReference>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageReferenceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getMessageId()).isEqualTo(501L);
        assertThat(captor.getValue().getFirst().getDocumentId()).isEqualTo(300L);
        assertThat(captor.getValue().getFirst().getChunkId()).isEqualTo(200L);
        verify(rateLimitService).checkChatLimit(1L);
    }

    @Test
    void shouldRejectOtherUsersSessionWhenSendMessage() {
        ChatService service = service();
        when(chatSessionRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(
                currentUser,
                100L,
                new SendChatMessageRequest("What is DI?", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Chat session not found");
    }

    @Test
    void shouldListMessagesInSession() {
        ChatService service = service();
        ChatSession session = session();
        ChatMessage message = new ChatMessage(100L, 1L, ChatRole.USER, "Hello");
        fillMessage(message, 500L);
        when(chatSessionRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message));

        assertThat(service.listMessages(currentUser, 100L))
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.id()).isEqualTo(500L);
                    assertThat(response.content()).isEqualTo("Hello");
                    assertThat(response.references()).isEmpty();
                });
    }

    private ChatService service() {
        return new ChatService(
                knowledgeBaseRepository,
                chatSessionRepository,
                chatMessageRepository,
                messageReferenceRepository,
                ragRetrievalService,
                promptBuilder,
                chatModelService,
                rateLimitService);
    }

    private KnowledgeBase knowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(1L, "Java Notes", null);
        ReflectionTestUtils.setField(knowledgeBase, "id", 10L);
        return knowledgeBase;
    }

    private ChatSession session() {
        ChatSession session = new ChatSession(1L, 10L, "Java Q&A");
        fillSession(session, 100L);
        return session;
    }

    private void fillSession(ChatSession session, Long id) {
        ReflectionTestUtils.setField(session, "id", id);
        ReflectionTestUtils.setField(session, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(session, "updatedAt", OffsetDateTime.now());
    }

    private void fillMessage(ChatMessage message, Long id) {
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "createdAt", OffsetDateTime.now());
    }
}
