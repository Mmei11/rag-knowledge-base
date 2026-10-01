package com.example.ragkb.chat.service;

import com.example.ragkb.chat.dto.ChatAnswerResponse;
import com.example.ragkb.chat.dto.ChatStreamDeltaResponse;
import com.example.ragkb.chat.dto.ChatStreamErrorResponse;
import com.example.ragkb.chat.dto.SendChatMessageRequest;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.security.CurrentUser;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatStreamingService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamingService.class);

    private final ChatService chatService;
    private final Executor chatStreamExecutor;

    public ChatStreamingService(
            ChatService chatService,
            @Qualifier("chatStreamExecutor") Executor chatStreamExecutor) {
        this.chatService = chatService;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    public SseEmitter streamMessage(
            CurrentUser currentUser,
            Long sessionId,
            SendChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean disconnected = new AtomicBoolean(false);
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onTimeout(() -> {
            disconnected.set(true);
            emitter.complete();
        });

        chatStreamExecutor.execute(() -> stream(
                emitter,
                disconnected,
                currentUser,
                sessionId,
                request));
        return emitter;
    }

    private void stream(
            SseEmitter emitter,
            AtomicBoolean disconnected,
            CurrentUser currentUser,
            Long sessionId,
            SendChatMessageRequest request) {
        try {
            ChatStreamPreparation preparation = chatService.prepareStreamingMessage(
                    currentUser,
                    sessionId,
                    request);
            send(emitter, disconnected, "user", preparation.userMessage());

            String answer = chatService.generateStreamingAnswer(
                    preparation,
                    delta -> send(emitter, disconnected, "delta", new ChatStreamDeltaResponse(delta)));
            if (disconnected.get()) {
                return;
            }

            ChatAnswerResponse completed = chatService.completeStreamingMessage(preparation, answer);
            send(emitter, disconnected, "complete", completed);
            emitter.complete();
        } catch (ClientDisconnectedException exception) {
            log.debug("chat stream client disconnected, userId={}, sessionId={}", currentUser.id(), sessionId);
        } catch (BusinessException exception) {
            sendError(emitter, disconnected, exception.getMessage());
        } catch (Exception exception) {
            if (disconnected.get()) {
                log.debug("chat stream stopped after client disconnected, userId={}, sessionId={}",
                        currentUser.id(), sessionId);
                return;
            }
            log.warn("chat stream failed, userId={}, sessionId={}", currentUser.id(), sessionId, exception);
            sendError(emitter, disconnected, "生成回答失败，请稍后重试");
        }
    }

    private void sendError(SseEmitter emitter, AtomicBoolean disconnected, String message) {
        try {
            send(emitter, disconnected, "error", new ChatStreamErrorResponse(message));
            emitter.complete();
        } catch (ClientDisconnectedException exception) {
            log.debug("unable to send chat stream error because client disconnected");
        }
    }

    private void send(SseEmitter emitter, AtomicBoolean disconnected, String eventName, Object data) {
        if (disconnected.get()) {
            throw new ClientDisconnectedException();
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException exception) {
            disconnected.set(true);
            throw new ClientDisconnectedException(exception);
        }
    }

    private static final class ClientDisconnectedException extends RuntimeException {

        private ClientDisconnectedException() {
        }

        private ClientDisconnectedException(Exception cause) {
            super(cause);
        }
    }
}
