package com.example.ragkb.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.model.EmbeddingService;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.repository.RagRetrievalRepository;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTests {

    private final CurrentUser currentUser = new CurrentUser(1L, "alice", UserRole.USER);

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private RagRetrievalRepository ragRetrievalRepository;

    @Test
    void shouldSearchSimilarChunksWithDefaultTopK() {
        RagRetrievalService service = service();
        EmbeddingVector queryEmbedding = new EmbeddingVector(List.of(0.1, 0.2));
        RetrievedChunkResponse item = new RetrievedChunkResponse(200L, 100L, 0, "Spring Boot notes", 0.87);
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase()));
        when(embeddingService.embedText("Spring Boot")).thenReturn(queryEmbedding);
        when(ragRetrievalRepository.searchSimilarChunks(10L, queryEmbedding, 5)).thenReturn(List.of(item));

        VectorSearchResponse response = service.search(
                currentUser,
                new VectorSearchRequest(10L, " Spring Boot ", null));

        assertThat(response.knowledgeBaseId()).isEqualTo(10L);
        assertThat(response.question()).isEqualTo("Spring Boot");
        assertThat(response.topK()).isEqualTo(5);
        assertThat(response.items()).containsExactly(item);
        verify(ragRetrievalRepository).searchSimilarChunks(10L, queryEmbedding, 5);
    }

    @Test
    void shouldRejectOtherUsersKnowledgeBase() {
        RagRetrievalService service = service();
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(
                currentUser,
                new VectorSearchRequest(10L, "Spring Boot", 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不存在");
    }

    private RagRetrievalService service() {
        return new RagRetrievalService(knowledgeBaseRepository, embeddingService, ragRetrievalRepository);
    }

    private KnowledgeBase knowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(1L, "Java Notes", null);
        ReflectionTestUtils.setField(knowledgeBase, "id", 10L);
        return knowledgeBase;
    }
}
