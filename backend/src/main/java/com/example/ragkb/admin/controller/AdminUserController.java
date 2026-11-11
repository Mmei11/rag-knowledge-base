package com.example.ragkb.admin.controller;

import com.example.ragkb.admin.dto.UpdateUserRoleRequest;
import com.example.ragkb.admin.dto.UpdateUserStatusRequest;
import com.example.ragkb.admin.service.AdminUserService;
import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.common.page.PageRequest;
import com.example.ragkb.common.page.PageResponse;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.dto.UserResponse;
import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.entity.UserStatus;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> search(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            @Valid PageRequest pageRequest) {
        return ApiResponse.success(
                adminUserService.search(currentUser(authentication), keyword, status, role, pageRequest));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserResponse> updateStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(
                adminUserService.updateStatus(currentUser(authentication), id, request.status()));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<UserResponse> updateRole(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return ApiResponse.success(
                adminUserService.updateRole(currentUser(authentication), id, request.role()));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
