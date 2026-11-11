package com.example.ragkb.admin.service;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.common.page.PageRequest;
import com.example.ragkb.common.page.PageResponse;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.dto.UserResponse;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.entity.UserStatus;
import com.example.ragkb.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final OperationLogService operationLogService;

    public AdminUserService(UserRepository userRepository, OperationLogService operationLogService) {
        this.userRepository = userRepository;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(
            CurrentUser admin,
            String keyword,
            UserStatus status,
            UserRole role,
            PageRequest pageRequest) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getSize());
        Specification<User> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (admin.role() == UserRole.ADMIN) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("id"), admin.id()),
                        criteriaBuilder.equal(root.get("role"), UserRole.USER)));
            }
            if (normalizedKeyword != null) {
                String likePattern = "%" + normalizedKeyword + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), likePattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("nickname")), likePattern)));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (role != null) {
                predicates.add(criteriaBuilder.equal(root.get("role"), role));
            }
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(
                        criteriaBuilder.asc(criteriaBuilder.selectCase()
                                .when(criteriaBuilder.equal(root.get("role"), UserRole.SUPER_ADMIN), 0)
                                .when(criteriaBuilder.equal(root.get("role"), UserRole.ADMIN), 1)
                                .otherwise(2)),
                        criteriaBuilder.asc(root.get("id")));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<User> page = userRepository.findAll(specification, pageable);
        return PageResponse.from(page.map(UserResponse::from));
    }

    @Transactional
    public UserResponse updateStatus(CurrentUser admin, Long userId, UserStatus status) {
        User target = findUser(userId);
        if (target.getId().equals(admin.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能修改自己的账号状态");
        }
        requireManageableTarget(admin, target);

        if (status == UserStatus.DISABLED) {
            target.disable();
        } else {
            target.enable();
        }
        userRepository.saveAndFlush(target);
        operationLogService.record(
                admin.id(),
                status == UserStatus.DISABLED ? OperationAction.USER_DISABLE : OperationAction.USER_ENABLE,
                OperationResourceType.USER,
                target.getId());
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse updateRole(CurrentUser admin, Long userId, UserRole role) {
        User target = findUser(userId);
        if (target.getId().equals(admin.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能修改自己的角色");
        }
        if (admin.role() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有主管理员可以修改用户角色");
        }
        requireManageableTarget(admin, target);
        if (!admin.role().outranks(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能将用户调整为下级角色");
        }

        target.changeRole(role);
        userRepository.saveAndFlush(target);
        operationLogService.record(
                admin.id(),
                OperationAction.USER_ROLE_CHANGE,
                OperationResourceType.USER,
                target.getId());
        return UserResponse.from(target);
    }

    private void requireManageableTarget(CurrentUser admin, User target) {
        if (!admin.role().outranks(target.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有权限操作该用户");
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }
}
