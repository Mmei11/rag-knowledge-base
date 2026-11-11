package com.example.ragkb.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.dto.UserResponse;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.entity.UserStatus;
import com.example.ragkb.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTests {

    private final CurrentUser superAdmin = new CurrentUser(1L, "super", UserRole.SUPER_ADMIN);
    private final CurrentUser subAdmin = new CurrentUser(3L, "sub", UserRole.ADMIN);

    @Mock
    private UserRepository userRepository;

    @Mock
    private OperationLogService operationLogService;

    @Test
    void shouldDisableUserAndAudit() {
        User target = user(2L, UserRole.USER, UserStatus.ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UserResponse response = service().updateStatus(superAdmin, 2L, UserStatus.DISABLED);

        assertThat(response.status()).isEqualTo(UserStatus.DISABLED);
        verify(operationLogService).record(
                1L,
                OperationAction.USER_DISABLE,
                OperationResourceType.USER,
                2L);
    }

    @Test
    void shouldEnableUserAndAudit() {
        User target = user(2L, UserRole.USER, UserStatus.DISABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UserResponse response = service().updateStatus(superAdmin, 2L, UserStatus.ENABLED);

        assertThat(response.status()).isEqualTo(UserStatus.ENABLED);
        verify(operationLogService).record(
                1L,
                OperationAction.USER_ENABLE,
                OperationResourceType.USER,
                2L);
    }

    @Test
    void shouldLetSubAdminDisableUser() {
        User target = user(2L, UserRole.USER, UserStatus.ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UserResponse response = service().updateStatus(subAdmin, 2L, UserStatus.DISABLED);

        assertThat(response.status()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void shouldRejectDisablingSelf() {
        User self = user(1L, UserRole.SUPER_ADMIN, UserStatus.ENABLED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service().updateStatus(superAdmin, 1L, UserStatus.DISABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能修改自己的账号状态");
    }

    @Test
    void shouldRejectSubAdminDisablingAnotherAdmin() {
        User target = user(4L, UserRole.ADMIN, UserStatus.ENABLED);
        when(userRepository.findById(4L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().updateStatus(subAdmin, 4L, UserStatus.DISABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有权限操作该用户");
    }

    @Test
    void shouldRejectChangingOwnRole() {
        User self = user(1L, UserRole.SUPER_ADMIN, UserStatus.ENABLED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service().updateRole(superAdmin, 1L, UserRole.ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能修改自己的角色");
    }

    @Test
    void shouldRejectSubAdminChangingRole() {
        User target = user(2L, UserRole.USER, UserStatus.ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().updateRole(subAdmin, 2L, UserRole.ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有主管理员可以修改用户角色");
    }

    @Test
    void shouldPromoteUserToSubAdminAndAudit() {
        User target = user(2L, UserRole.USER, UserStatus.ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UserResponse response = service().updateRole(superAdmin, 2L, UserRole.ADMIN);

        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        verify(operationLogService).record(
                1L,
                OperationAction.USER_ROLE_CHANGE,
                OperationResourceType.USER,
                2L);
    }

    @Test
    void shouldDemoteSubAdminToUser() {
        User target = user(2L, UserRole.ADMIN, UserStatus.ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UserResponse response = service().updateRole(superAdmin, 2L, UserRole.USER);

        assertThat(response.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void shouldRejectPromotingUserToSuperAdmin() {
        User target = user(2L, UserRole.USER, UserStatus.ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().updateRole(superAdmin, 2L, UserRole.SUPER_ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能将用户调整为下级角色");
    }

    @Test
    void shouldRejectOperatingOnAnotherSuperAdmin() {
        User target = user(2L, UserRole.SUPER_ADMIN, UserStatus.ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().updateStatus(superAdmin, 2L, UserStatus.DISABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有权限操作该用户");
    }

    private AdminUserService service() {
        return new AdminUserService(userRepository, operationLogService);
    }

    private User user(Long id, UserRole role, UserStatus status) {
        User user = new User("user" + id, "encoded-password", null);
        ReflectionTestUtils.setField(user, "id", id);
        user.changeRole(role);
        if (status == UserStatus.DISABLED) {
            user.disable();
        }
        return user;
    }
}
