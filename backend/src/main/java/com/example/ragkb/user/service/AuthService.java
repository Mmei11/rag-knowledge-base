package com.example.ragkb.user.service;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.admin.service.OperationLogService;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.security.JwtTokenProvider;
import com.example.ragkb.user.dto.LoginRequest;
import com.example.ragkb.user.dto.LoginResponse;
import com.example.ragkb.user.dto.RegisterRequest;
import com.example.ragkb.user.dto.UserResponse;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.entity.UserStatus;
import com.example.ragkb.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OperationLogService operationLogService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            OperationLogService operationLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "用户名已存在");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(username, passwordHash, normalizeNickname(request.nickname()));

        try {
            User saved = userRepository.saveAndFlush(user);
            operationLogService.record(
                    saved.getId(),
                    OperationAction.USER_REGISTER,
                    OperationResourceType.USER,
                    saved.getId());
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "用户名已存在");
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> invalidCredentials());

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户已被禁用");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        String token = jwtTokenProvider.generateToken(user);
        operationLogService.record(
                user.getId(),
                OperationAction.USER_LOGIN,
                OperationResourceType.USER,
                user.getId());
        return LoginResponse.bearer(
                token,
                jwtTokenProvider.expirationSeconds(),
                UserResponse.from(user));
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return nickname.trim();
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
    }
}
