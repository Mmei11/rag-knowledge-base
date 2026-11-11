package com.example.ragkb.admin.controller;

import com.example.ragkb.admin.dto.OperationLogResponse;
import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.admin.service.OperationLogService;
import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.common.page.PageRequest;
import com.example.ragkb.common.page.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operation-logs")
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    public AdminOperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OperationLogResponse>> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) OperationAction action,
            @RequestParam(required = false) OperationResourceType resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Valid PageRequest pageRequest) {
        return ApiResponse.success(operationLogService.search(
                userId, action, resourceType, resourceId, from, to, pageRequest));
    }
}
