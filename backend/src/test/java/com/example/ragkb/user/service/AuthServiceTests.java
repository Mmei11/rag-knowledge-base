package com.example.ragkb.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.admin.service.OperationLogService;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.security.JwtTokenProvider;
import com.example.ragkb.user.dto.LoginRequest;
import com.example.ragkb.user.dto.LoginResponse;
import com.example.ragkb.user.dto.RegisterRequest;
import com.example.ragkb.user.dto.UserResponse;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    // Mock 表示这里不使用真实数据库，只模拟 UserRepository 的行为。
    @Mock
    private UserRepository userRepository;

    // Mock 表示这里不使用真实 BCrypt，只关心 AuthService 是否调用了密码加密。
    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OperationLogService operationLogService;

    @Test
    void shouldRegisterUserWithEncodedPassword() {
        // 手动创建被测试对象，把两个 Mock 依赖传进去。
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, operationLogService);

        // 约定测试场景：alice 这个用户名还不存在，可以注册。
        when(userRepository.existsByUsername("alice")).thenReturn(false);

        // 约定测试场景：明文密码被加密后得到 encoded-password。
        when(passwordEncoder.encode("Password123")).thenReturn("encoded-password");

        // saveAndFlush 正常会由 JPA 保存到数据库；这里直接返回传入的 User，避免连接真实数据库。
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 执行注册。这里故意传入前后空格，用来验证 AuthService 会清理 username 和 nickname。
        UserResponse response = authService.register(
                new RegisterRequest(" alice ", "Password123", " Alice "));

        // 验证返回给前端的数据已经被规范化。
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.nickname()).isEqualTo("Alice");

        // 捕获 AuthService 真正传给 Repository 保存的 User 对象。
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        // 验证写入数据库前的 User 数据是正确的。
        assertThat(savedUser.getUsername()).isEqualTo("alice");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("Password123");
        assertThat(savedUser.getNickname()).isEqualTo("Alice");
    }

    @Test
    void shouldRejectDuplicatedUsername() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, operationLogService);

        // 约定测试场景：alice 已经存在。
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        // 验证重复用户名会被转换成业务异常，而不是继续保存用户。
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "Password123", "Alice")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    void shouldLoginWithToken() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, operationLogService);
        User user = new User("alice", "encoded-password", "Alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(user)).thenReturn("jwt-token");
        when(jwtTokenProvider.expirationSeconds()).thenReturn(86400L);

        LoginResponse response = authService.login(new LoginRequest(" alice ", "Password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(86400L);
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void shouldRejectInvalidPassword() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, operationLogService);
        User user = new User("alice", "encoded-password", "Alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "WrongPassword")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void shouldRejectDisabledUser() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, operationLogService);
        User user = new User("alice", "encoded-password", "Alice");
        user.disable();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "Password123")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户已被禁用");
    }
}
