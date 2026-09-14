package com.example.ragkb.chat.controller;

import com.example.ragkb.chat.dto.ChatAnswerResponse;
import com.example.ragkb.chat.dto.ChatMessageResponse;
import com.example.ragkb.chat.dto.ChatSessionResponse;
import com.example.ragkb.chat.dto.CreateChatSessionRequest;
import com.example.ragkb.chat.dto.SendChatMessageRequest;
import com.example.ragkb.chat.service.ChatService;
import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            Authentication authentication,
            @Valid @RequestBody CreateChatSessionRequest request) {
        return ApiResponse.success(chatService.createSession(currentUser(authentication), request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions(Authentication authentication) {
        return ApiResponse.success(chatService.listSessions(currentUser(authentication)));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(
            Authentication authentication,
            @PathVariable Long sessionId) {
        return ApiResponse.success(chatService.listMessages(currentUser(authentication), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatAnswerResponse> sendMessage(
            Authentication authentication,
            @PathVariable Long sessionId,
            @Valid @RequestBody SendChatMessageRequest request) {
        return ApiResponse.success(chatService.sendMessage(currentUser(authentication), sessionId, request));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
