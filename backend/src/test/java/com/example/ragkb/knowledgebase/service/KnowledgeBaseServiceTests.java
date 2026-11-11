package com.example.ragkb.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.admin.service.OperationLogService;
import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.knowledgebase.dto.CreateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseAccess;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseResponse;
import com.example.ragkb.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseAccessRole;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseMemberRepository;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KnowledgeBaseMemberRepository knowledgeBaseMemberRepository;

    @Mock
    private KnowledgeBaseAccessService knowledgeBaseAccessService;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Mock
    private OperationLogService operationLogService;

    @Test
    void shouldCreateKnowledgeBase() {
        KnowledgeBaseService service = service();
        when(knowledgeBaseRepository.existsByUserIdAndName(1L, "Java Notes")).thenReturn(false);
        when(knowledgeBaseRepository.saveAndFlush(any(KnowledgeBase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseResponse response = service.create(
                currentUser,
                new CreateKnowledgeBaseRequest(" Java Notes ", " Spring Boot "));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Java Notes");
        assertThat(response.description()).isEqualTo("Spring Boot");

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        verify(cacheInvalidationService).evictKnowledgeBaseList(1L);
    }

    @Test
    void shouldRejectDuplicatedNameOnCreate() {
        KnowledgeBaseService service = service();
        when(knowledgeBaseRepository.existsByUserIdAndName(1L, "Java Notes")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                currentUser,
                new CreateKnowledgeBaseRequest("Java Notes", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库名称已存在");
    }

    @Test
    void shouldListCurrentUserKnowledgeBases() {
        KnowledgeBaseService service = service();
        KnowledgeBase knowledgeBase = knowledgeBase(10L, 1L, "Java Notes", null);
        when(knowledgeBaseRepository.findAccessibleByUserId(1L))
                .thenReturn(List.of(knowledgeBase));
        when(knowledgeBaseMemberRepository.findByUserId(1L)).thenReturn(List.of());

        List<KnowledgeBaseResponse> responses = service.listMine(currentUser);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(10L);
        assertThat(responses.getFirst().name()).isEqualTo("Java Notes");
    }

    @Test
    void shouldRejectOtherUsersKnowledgeBase() {
        KnowledgeBaseService service = service();
        when(knowledgeBaseAccessService.requireVisible(currentUser, 10L))
                .thenThrow(new BusinessException(
                        com.example.ragkb.common.exception.ErrorCode.RESOURCE_NOT_FOUND, "知识库不存在"));

        assertThatThrownBy(() -> service.getMine(currentUser, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不存在");
    }

    @Test
    void shouldUpdateKnowledgeBase() {
        KnowledgeBaseService service = service();
        KnowledgeBase knowledgeBase = knowledgeBase(10L, 1L, "Old Name", null);
        when(knowledgeBaseAccessService.requireOwner(currentUser, 10L))
                .thenReturn(new KnowledgeBaseAccess(knowledgeBase, KnowledgeBaseAccessRole.OWNER));
        when(knowledgeBaseRepository.existsByUserIdAndNameAndIdNot(1L, "New Name", 10L)).thenReturn(false);
        when(knowledgeBaseRepository.saveAndFlush(knowledgeBase)).thenReturn(knowledgeBase);

        KnowledgeBaseResponse response = service.update(
                currentUser,
                10L,
                new UpdateKnowledgeBaseRequest(" New Name ", " Updated "));

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("Updated");
        verify(cacheInvalidationService).evictAllKnowledgeBaseCaches();
    }

    @Test
    void shouldDeleteKnowledgeBase() {
        KnowledgeBaseService service = service();
        KnowledgeBase knowledgeBase = knowledgeBase(10L, 1L, "Java Notes", null);
        when(knowledgeBaseAccessService.requireOwner(currentUser, 10L))
                .thenReturn(new KnowledgeBaseAccess(knowledgeBase, KnowledgeBaseAccessRole.OWNER));

        service.delete(currentUser, 10L);

        verify(knowledgeBaseRepository).delete(knowledgeBase);
        verify(cacheInvalidationService).evictAllKnowledgeBaseCaches();
        verify(cacheInvalidationService).evictAllDocumentCaches();
    }

    private KnowledgeBaseService service() {
        return new KnowledgeBaseService(
                knowledgeBaseRepository,
                knowledgeBaseMemberRepository,
                knowledgeBaseAccessService,
                cacheInvalidationService,
                operationLogService);
    }

    private KnowledgeBase knowledgeBase(Long id, Long userId, String name, String description) {
        KnowledgeBase knowledgeBase = new KnowledgeBase(userId, name, description);
        ReflectionTestUtils.setField(knowledgeBase, "id", id);
        return knowledgeBase;
    }
}
