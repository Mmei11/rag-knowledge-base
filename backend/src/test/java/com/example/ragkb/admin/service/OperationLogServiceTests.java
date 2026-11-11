package com.example.ragkb.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.admin.dto.OperationLogResponse;
import com.example.ragkb.admin.entity.OperationLog;
import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.admin.repository.OperationLogRepository;
import com.example.ragkb.common.page.PageRequest;
import com.example.ragkb.common.page.PageResponse;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceTests {

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldRecordOperationLog() {
        service().record(1L, OperationAction.USER_LOGIN, OperationResourceType.USER, 1L);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogRepository).save(captor.capture());
        OperationLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(1L);
        assertThat(log.getAction()).isEqualTo(OperationAction.USER_LOGIN);
        assertThat(log.getResourceType()).isEqualTo(OperationResourceType.USER);
        assertThat(log.getResourceId()).isEqualTo(1L);
        assertThat(log.getIp()).isNull();
    }

    @Test
    void shouldSearchOperationLogs() {
        User user = new User("alice", "encoded-password", "Alice");
        ReflectionTestUtils.setField(user, "id", 1L);
        OperationLog log = new OperationLog(
                1L, OperationAction.USER_LOGIN, OperationResourceType.USER, 1L, "127.0.0.1");
        when(operationLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        PageResponse<OperationLogResponse> result = service().search(
                null, null, null, null, null, null, new PageRequest(1, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().username()).isEqualTo("alice");
        assertThat(result.items().getFirst().action()).isEqualTo(OperationAction.USER_LOGIN);
    }

    private OperationLogService service() {
        return new OperationLogService(operationLogRepository, userRepository);
    }
}
