package com.example.ragkb.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.entity.UserStatus;
import com.example.ragkb.user.repository.UserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
class AdminRepositoryTests {

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldExecuteAdminQueries() {
        assertThat(operationLogRepository.findAll(
                (root, query, criteriaBuilder) -> criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("action"), OperationAction.USER_LOGIN),
                        criteriaBuilder.equal(root.get("resourceType"), OperationResourceType.USER),
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.<OffsetDateTime>get("createdAt"),
                                OffsetDateTime.now().minusDays(1))),
                PageRequest.of(0, 10)))
                .isNotNull();
        assertThat(userRepository.findAll(
                (root, query, criteriaBuilder) -> criteriaBuilder.and(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.<String>get("username")), "%alice%"),
                        criteriaBuilder.equal(root.get("status"), UserStatus.ENABLED),
                        criteriaBuilder.equal(root.get("role"), UserRole.USER)),
                PageRequest.of(0, 10)))
                .isNotNull();
    }
}
