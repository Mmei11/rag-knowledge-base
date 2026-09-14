package com.example.ragkb.user.service;

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

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
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
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "用户名已存在");
        }
    }

    @Transactional(readOnly = true)
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
