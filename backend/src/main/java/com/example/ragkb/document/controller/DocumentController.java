package com.example.ragkb.document.controller;

import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.document.dto.DocumentParseResponse;
import com.example.ragkb.document.dto.DocumentResponse;
import com.example.ragkb.document.dto.DocumentStatusResponse;
import com.example.ragkb.document.service.DocumentParseService;
import com.example.ragkb.document.service.DocumentService;
import com.example.ragkb.document.task.DocumentProcessTask;
import com.example.ragkb.security.CurrentUser;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentParseService documentParseService;
    private final DocumentProcessTask documentProcessTask;

    public DocumentController(
            DocumentService documentService,
            DocumentParseService documentParseService,
            DocumentProcessTask documentProcessTask) {
        this.documentService = documentService;
        this.documentParseService = documentParseService;
        this.documentProcessTask = documentProcessTask;
    }

    @PostMapping(
            value = "/knowledge-bases/{knowledgeBaseId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> upload(
            Authentication authentication,
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file) {
        DocumentResponse response = documentService.upload(currentUser(authentication), knowledgeBaseId, file);
        documentProcessTask.process(response.id());
        return ApiResponse.success(response);
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<List<DocumentResponse>> listByKnowledgeBase(
            Authentication authentication,
            @PathVariable Long knowledgeBaseId) {
        return ApiResponse.success(documentService.listByKnowledgeBase(currentUser(authentication), knowledgeBaseId));
    }

    @GetMapping("/documents/{documentId}")
    public ApiResponse<DocumentResponse> getMine(
            Authentication authentication,
            @PathVariable Long documentId) {
        return ApiResponse.success(documentService.getMine(currentUser(authentication), documentId));
    }

    @GetMapping("/documents/{documentId}/status")
    public ApiResponse<DocumentStatusResponse> getStatus(
            Authentication authentication,
            @PathVariable Long documentId) {
        return ApiResponse.success(documentService.getStatus(currentUser(authentication), documentId));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable Long documentId) {
        documentService.delete(currentUser(authentication), documentId);
        return ApiResponse.success();
    }

    @PostMapping("/documents/{documentId}/parse")
    public ApiResponse<DocumentParseResponse> parse(
            Authentication authentication,
            @PathVariable Long documentId) {
        return ApiResponse.success(documentParseService.parse(currentUser(authentication), documentId));
    }

    @PostMapping("/documents/{documentId}/reprocess")
    public ApiResponse<DocumentStatusResponse> reprocess(
            Authentication authentication,
            @PathVariable Long documentId) {
        DocumentStatusResponse response = documentService.reprocess(currentUser(authentication), documentId);
        documentProcessTask.process(documentId);
        return ApiResponse.success(response);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
