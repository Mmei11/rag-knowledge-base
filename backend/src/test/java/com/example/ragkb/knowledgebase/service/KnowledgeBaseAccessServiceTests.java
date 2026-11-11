package com.example.ragkb.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.knowledgebase.dto.KnowledgeBaseAccess;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseAccessRole;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMember;
import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMemberRole;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseMemberRepository;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseAccessServiceTests {

    private final CurrentUser owner = new CurrentUser(1L, "alice", UserRole.USER);
    private final CurrentUser editor = new CurrentUser(2L, "bob", UserRole.USER);
    private final CurrentUser viewer = new CurrentUser(3L, "carol", UserRole.USER);
    private final CurrentUser outsider = new CurrentUser(4L, "dave", UserRole.USER);

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KnowledgeBaseMemberRepository memberRepository;

    @Test
    void shouldResolveOwnerAsOwner() {
        KnowledgeBaseAccessService service = service();
        when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(knowledgeBase()));

        KnowledgeBaseAccess access = service.requireVisible(owner, 10L);

        assertThat(access.role()).isEqualTo(KnowledgeBaseAccessRole.OWNER);
        assertThat(access.canWrite()).isTrue();
        assertThat(access.canManageMembers()).isTrue();
    }

    @Test
    void shouldResolveEditorAsWritableButNotMemberManager() {
        KnowledgeBaseAccessService service = service();
        when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(knowledgeBase()));
        when(memberRepository.findByKnowledgeBaseIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(member(10L, 2L, KnowledgeBaseMemberRole.EDITOR)));

        KnowledgeBaseAccess access = service.requireWritable(editor, 10L);

        assertThat(access.role()).isEqualTo(KnowledgeBaseAccessRole.EDITOR);
        assertThat(access.canWrite()).isTrue();
        assertThat(access.canManageMembers()).isFalse();
        assertThatThrownBy(() -> service.requireOwner(editor, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有知识库所有者可以执行此操作");
    }

    @Test
    void shouldRejectViewerWrite() {
        KnowledgeBaseAccessService service = service();
        when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(knowledgeBase()));
        when(memberRepository.findByKnowledgeBaseIdAndUserId(10L, 3L))
                .thenReturn(Optional.of(member(10L, 3L, KnowledgeBaseMemberRole.VIEWER)));

        KnowledgeBaseAccess access = service.requireVisible(viewer, 10L);

        assertThat(access.role()).isEqualTo(KnowledgeBaseAccessRole.VIEWER);
        assertThat(access.canWrite()).isFalse();
        assertThatThrownBy(() -> service.requireWritable(viewer, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限修改该知识库");
    }

    @Test
    void shouldHideUnrelatedKnowledgeBase() {
        KnowledgeBaseAccessService service = service();
        when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(knowledgeBase()));
        when(memberRepository.findByKnowledgeBaseIdAndUserId(10L, 4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireVisible(outsider, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不存在");
    }

    private KnowledgeBaseAccessService service() {
        return new KnowledgeBaseAccessService(knowledgeBaseRepository, memberRepository);
    }

    private KnowledgeBase knowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(1L, "Java Notes", null);
        ReflectionTestUtils.setField(knowledgeBase, "id", 10L);
        return knowledgeBase;
    }

    private KnowledgeBaseMember member(
            Long knowledgeBaseId,
            Long userId,
            KnowledgeBaseMemberRole role) {
        return new KnowledgeBaseMember(knowledgeBaseId, userId, role);
    }
}
