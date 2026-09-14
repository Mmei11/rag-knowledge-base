package com.example.ragkb.knowledgebase.controller;

import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.knowledgebase.dto.CreateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseResponse;
import com.example.ragkb.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.service.KnowledgeBaseService;
import com.example.ragkb.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseService.create(currentUser(authentication), request));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> listMine(Authentication authentication) {
        return ApiResponse.success(knowledgeBaseService.listMine(currentUser(authentication)));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> getMine(
            Authentication authentication,
            @PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.getMine(currentUser(authentication), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseService.update(currentUser(authentication), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable Long id) {
        knowledgeBaseService.delete(currentUser(authentication), id);
        return ApiResponse.success();
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
