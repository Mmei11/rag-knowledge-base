package com.example.ragkb.rag.controller;

import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.service.RagRetrievalService;
import com.example.ragkb.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class RagSearchController {

    private final RagRetrievalService ragRetrievalService;

    public RagSearchController(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    @PostMapping("/vector")
    public ApiResponse<VectorSearchResponse> search(
            Authentication authentication,
            @Valid @RequestBody VectorSearchRequest request) {
        return ApiResponse.success(ragRetrievalService.search(currentUser(authentication), request));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
