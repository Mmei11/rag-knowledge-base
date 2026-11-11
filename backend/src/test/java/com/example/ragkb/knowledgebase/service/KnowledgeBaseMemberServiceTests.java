package com.example.ragkb.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.admin.service.OperationLogService;
import com.example.ragkb.common.cache.CacheInvalidationService;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.knowledgebase.dto.AddKnowledgeBaseMemberRequest;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseAccess;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseMemberResponse;
import com.example.ragkb.knowledgebase.dto.UpdateKnowledgeBaseMemberRoleRequest;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseAccessRole;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMember;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMemberRole;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseMemberRepository;
import com.example.ragkb.security.CurrentUser;
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
class KnowledgeBaseMemberServiceTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @Mock
    private KnowledgeBaseAccessService accessService;

    @Mock
    private KnowledgeBaseMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Test
    void shouldAddMemberAndAudit() {
        KnowledgeBaseAccess access = ownerAccess();
        User bob = user(2L, "bob");
        when(accessService.requireOwner(currentUser, 10L)).thenReturn(access);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(memberRepository.existsByKnowledgeBaseIdAndUserId(10L, 2L)).thenReturn(false);
        when(memberRepository.saveAndFlush(any(KnowledgeBaseMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseMemberResponse response = service().addMember(
                currentUser,
                10L,
                new AddKnowledgeBaseMemberRequest("bob", KnowledgeBaseMemberRole.VIEWER));

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.username()).isEqualTo("bob");
        assertThat(response.role()).isEqualTo(KnowledgeBaseMemberRole.VIEWER);
        verify(operationLogService).record(
                1L,
                OperationAction.KB_MEMBER_ADD,
                OperationResourceType.KNOWLEDGE_BASE,
                10L);
        verify(cacheInvalidationService).evictAllKnowledgeBaseCaches();
        verify(cacheInvalidationService).evictAllDocumentCaches();
    }

    @Test
    void shouldRejectDuplicateMember() {
        when(accessService.requireOwner(currentUser, 10L)).thenReturn(ownerAccess());
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
        when(memberRepository.existsByKnowledgeBaseIdAndUserId(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service().addMember(
                currentUser,
                10L,
                new AddKnowledgeBaseMemberRequest("bob", KnowledgeBaseMemberRole.VIEWER)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户已经是知识库成员");
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectOwnerAsMember() {
        when(accessService.requireOwner(currentUser, 10L)).thenReturn(ownerAccess());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice")));

        assertThatThrownBy(() -> service().addMember(
                currentUser,
                10L,
                new AddKnowledgeBaseMemberRequest("alice", KnowledgeBaseMemberRole.EDITOR)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能把知识库所有者添加为成员");
    }

    @Test
    void shouldRejectDisabledUser() {
        User bob = user(2L, "bob");
        bob.disable();
        when(accessService.requireOwner(currentUser, 10L)).thenReturn(ownerAccess());
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));

        assertThatThrownBy(() -> service().addMember(
                currentUser,
                10L,
                new AddKnowledgeBaseMemberRequest("bob", KnowledgeBaseMemberRole.VIEWER)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户已被禁用");
    }

    @Test
    void shouldChangeMemberRole() {
        KnowledgeBaseMember member = new KnowledgeBaseMember(10L, 2L, KnowledgeBaseMemberRole.VIEWER);
        User bob = user(2L, "bob");
        when(accessService.requireOwner(currentUser, 10L)).thenReturn(ownerAccess());
        when(memberRepository.findByKnowledgeBaseIdAndUserId(10L, 2L)).thenReturn(Optional.of(member));
        when(memberRepository.saveAndFlush(member)).thenReturn(member);
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        KnowledgeBaseMemberResponse response = service().changeRole(
                currentUser,
                10L,
                2L,
                new UpdateKnowledgeBaseMemberRoleRequest(KnowledgeBaseMemberRole.EDITOR));

        assertThat(response.role()).isEqualTo(KnowledgeBaseMemberRole.EDITOR);
        verify(operationLogService).record(
                1L,
                OperationAction.KB_MEMBER_ROLE_CHANGE,
                OperationResourceType.KNOWLEDGE_BASE,
                10L);
        verify(cacheInvalidationService).evictAllKnowledgeBaseCaches();
    }

    @Test
    void shouldRemoveMember() {
        when(accessService.requireOwner(currentUser, 10L)).thenReturn(ownerAccess());
        when(memberRepository.deleteByKnowledgeBaseIdAndUserId(10L, 2L)).thenReturn(1L);

        service().removeMember(currentUser, 10L, 2L);

        verify(operationLogService).record(
                1L,
                OperationAction.KB_MEMBER_REMOVE,
                OperationResourceType.KNOWLEDGE_BASE,
                10L);
        verify(cacheInvalidationService).evictAllDocumentCaches();
    }

    @Test
    void shouldRejectRemovingMissingMember() {
        when(accessService.requireOwner(currentUser, 10L)).thenReturn(ownerAccess());
        when(memberRepository.deleteByKnowledgeBaseIdAndUserId(10L, 2L)).thenReturn(0L);

        assertThatThrownBy(() -> service().removeMember(currentUser, 10L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库成员不存在");
    }

    private KnowledgeBaseMemberService service() {
        return new KnowledgeBaseMemberService(
                accessService,
                memberRepository,
                userRepository,
                operationLogService,
                cacheInvalidationService);
    }

    private KnowledgeBaseAccess ownerAccess() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(1L, "Java Notes", null);
        ReflectionTestUtils.setField(knowledgeBase, "id", 10L);
        return new KnowledgeBaseAccess(knowledgeBase, KnowledgeBaseAccessRole.OWNER);
    }

    private User user(Long id, String username) {
        User user = new User(username, "encoded-password", username);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
