package com.example.ragkb.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTests {

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldPromoteExistingUser() {
        User user = user(1L, UserRole.USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        new AdminBootstrapRunner(userRepository, "alice").run(null);

        assertThat(user.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        verify(userRepository).save(user);
    }

    @Test
    void shouldNoOpWhenUserMissing() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        new AdminBootstrapRunner(userRepository, "missing").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNoOpWhenUserIsAlreadySuperAdmin() {
        User user = user(1L, UserRole.SUPER_ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        new AdminBootstrapRunner(userRepository, "alice").run(null);

        verify(userRepository, never()).save(any());
    }

    private User user(Long id, UserRole role) {
        User user = new User("alice", "encoded-password", "Alice");
        ReflectionTestUtils.setField(user, "id", id);
        user.changeRole(role);
        return user;
    }
}
