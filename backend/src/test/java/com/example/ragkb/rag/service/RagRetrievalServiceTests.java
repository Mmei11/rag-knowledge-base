package com.example.ragkb.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.text.ChineseTextSegmenter;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.rag.config.RerankProperties;
import com.example.ragkb.rag.config.RetrievalProperties;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.RetrievalFilters;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.model.EmbeddingService;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.model.NoopRerankService;
import com.example.ragkb.rag.model.RerankService;
import com.example.ragkb.rag.repository.RagRetrievalRepository;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.UserRole;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
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
    private DocumentRepository documentRepository;

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
        when(ragRetrievalRepository.searchSimilarChunks(
                eq(10L), eq(queryEmbedding), eq(15), eq(0.35), any(RetrievalFilters.class)))
                .thenReturn(List.of(item));
        when(ragRetrievalRepository.searchKeywordChunks(
                eq(10L), eq(queryEmbedding), anyList(), eq(15), any(RetrievalFilters.class)))
                .thenReturn(List.of());

        VectorSearchResponse response = service.search(
                currentUser,
                new VectorSearchRequest(10L, " Spring Boot ", null));

        assertThat(response.knowledgeBaseId()).isEqualTo(10L);
        assertThat(response.question()).isEqualTo("Spring Boot");
        assertThat(response.topK()).isEqualTo(5);
        assertThat(response.items()).containsExactly(item);
        verify(ragRetrievalRepository).searchSimilarChunks(
                eq(10L), eq(queryEmbedding), eq(15), eq(0.35), any(RetrievalFilters.class));
    }

    @Test
    void shouldFuseVectorAndKeywordResultsWithReciprocalRankFusion() {
        RagRetrievalService service = service();
        EmbeddingVector queryEmbedding = new EmbeddingVector(List.of(0.1, 0.2));
        RetrievedChunkResponse vectorFirst = new RetrievedChunkResponse(200L, 100L, 0, "Spring Boot notes", 0.87);
        RetrievedChunkResponse shared = new RetrievedChunkResponse(201L, 100L, 1, "Spring transaction notes", 0.82);
        RetrievedChunkResponse keywordOnly = new RetrievedChunkResponse(202L, 100L, 2, "Transaction annotations", 0.62);
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase()));
        when(embeddingService.embedText("Spring transaction")).thenReturn(queryEmbedding);
        when(ragRetrievalRepository.searchSimilarChunks(
                eq(10L), eq(queryEmbedding), eq(9), eq(0.35), any(RetrievalFilters.class)))
                .thenReturn(List.of(vectorFirst, shared));
        when(ragRetrievalRepository.searchKeywordChunks(
                eq(10L), eq(queryEmbedding), anyList(), eq(9), any(RetrievalFilters.class)))
                .thenReturn(List.of(shared, keywordOnly));

        VectorSearchResponse response = service.search(
                currentUser,
                new VectorSearchRequest(10L, "Spring transaction", 3, null));

        assertThat(response.items()).extracting(RetrievedChunkResponse::chunkId)
                .containsExactly(201L, 200L, 202L);
    }

    @Test
    void shouldFilterKeywordResultBelowSimilarityThreshold() {
        RagRetrievalService service = service();
        EmbeddingVector queryEmbedding = new EmbeddingVector(List.of(0.1, 0.2));
        RetrievedChunkResponse keywordOnly = new RetrievedChunkResponse(202L, 100L, 2, "Transaction annotations", 0.34);
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase()));
        when(embeddingService.embedText("transaction annotation")).thenReturn(queryEmbedding);
        when(ragRetrievalRepository.searchSimilarChunks(
                eq(10L), eq(queryEmbedding), eq(9), eq(0.35), any(RetrievalFilters.class)))
                .thenReturn(List.of());
        when(ragRetrievalRepository.searchKeywordChunks(
                eq(10L), eq(queryEmbedding), anyList(), eq(9), any(RetrievalFilters.class)))
                .thenReturn(List.of(keywordOnly));

        VectorSearchResponse response = service.search(
                currentUser,
                new VectorSearchRequest(10L, "transaction annotation", 3, null));

        assertThat(response.items()).isEmpty();
    }

    @Test
    void shouldPassDocumentAndDateFiltersToBothSearchStrategies() {
        RagRetrievalService service = service();
        EmbeddingVector queryEmbedding = new EmbeddingVector(List.of(0.1, 0.2));
        RetrievalFilters filters = new RetrievalFilters(
                List.of(100L), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20));
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase()));
        when(documentRepository.countByIdInAndKnowledgeBaseIdAndUserId(List.of(100L), 10L, 1L)).thenReturn(1L);
        when(embeddingService.embedText("Spring Boot")).thenReturn(queryEmbedding);
        when(ragRetrievalRepository.searchSimilarChunks(10L, queryEmbedding, 9, 0.35, filters))
                .thenReturn(List.of());
        when(ragRetrievalRepository.searchKeywordChunks(
                eq(10L), eq(queryEmbedding), anyList(), eq(9), eq(filters))).thenReturn(List.of());

        service.search(
                currentUser,
                new VectorSearchRequest(
                        10L,
                        "Spring Boot",
                        3,
                        null,
                        List.of(100L),
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 20)));

        verify(ragRetrievalRepository).searchSimilarChunks(10L, queryEmbedding, 9, 0.35, filters);
        verify(documentRepository).countByIdInAndKnowledgeBaseIdAndUserId(List.of(100L), 10L, 1L);
    }

    @Test
    void shouldRejectDocumentThatDoesNotBelongToCurrentKnowledgeBase() {
        RagRetrievalService service = service();
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase()));
        when(documentRepository.countByIdInAndKnowledgeBaseIdAndUserId(List.of(200L), 10L, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.search(
                currentUser,
                new VectorSearchRequest(10L, "Spring Boot", 3, null, List.of(200L), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("筛选文档不存在或不属于当前知识库");
        verifyNoInteractions(embeddingService, ragRetrievalRepository);
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

    @Test
    void shouldRerankFusedCandidatesBeforeApplyingTopK() {
        EmbeddingVector queryEmbedding = new EmbeddingVector(List.of(0.1, 0.2));
        RetrievedChunkResponse first = new RetrievedChunkResponse(1L, 100L, 0, "first", 0.90);
        RetrievedChunkResponse second = new RetrievedChunkResponse(2L, 100L, 1, "second", 0.80);
        RetrievedChunkResponse third = new RetrievedChunkResponse(3L, 100L, 2, "third", 0.70);
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase()));
        when(embeddingService.embedText("Spring Boot")).thenReturn(queryEmbedding);
        when(ragRetrievalRepository.searchSimilarChunks(
                eq(10L), eq(queryEmbedding), eq(6), eq(0.35), any(RetrievalFilters.class)))
                .thenReturn(List.of(first, second, third));
        when(ragRetrievalRepository.searchKeywordChunks(
                eq(10L), eq(queryEmbedding), anyList(), eq(6), any(RetrievalFilters.class)))
                .thenReturn(List.of());

        RerankService reversing = (query, candidates) -> candidates.reversed();
        RagRetrievalService service = service(reversing, new RerankProperties());

        VectorSearchResponse response = service.search(
                currentUser,
                new VectorSearchRequest(10L, "Spring Boot", 2));

        assertThat(response.items()).containsExactly(third, second);
    }

    @Test
    void shouldLimitRerankPoolToConfiguredCandidateLimit() {
        EmbeddingVector queryEmbedding = new EmbeddingVector(List.of(0.1, 0.2));
        List<RetrievedChunkResponse> items = List.of(
                new RetrievedChunkResponse(1L, 100L, 0, "one", 0.90),
                new RetrievedChunkResponse(2L, 100L, 1, "two", 0.85),
                new RetrievedChunkResponse(3L, 100L, 2, "three", 0.80),
                new RetrievedChunkResponse(4L, 100L, 3, "four", 0.75),
                new RetrievedChunkResponse(5L, 100L, 4, "five", 0.70));
        when(knowledgeBaseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(knowledgeBase()));
        when(embeddingService.embedText("Spring Boot")).thenReturn(queryEmbedding);
        when(ragRetrievalRepository.searchSimilarChunks(
                eq(10L), eq(queryEmbedding), eq(3), eq(0.35), any(RetrievalFilters.class)))
                .thenReturn(items);
        when(ragRetrievalRepository.searchKeywordChunks(
                eq(10L), eq(queryEmbedding), anyList(), eq(3), any(RetrievalFilters.class)))
                .thenReturn(List.of());

        AtomicReference<Integer> rerankCandidateCount = new AtomicReference<>();
        RerankService capturing = (query, candidates) -> {
            rerankCandidateCount.set(candidates.size());
            return candidates;
        };
        RerankProperties rerankProperties = new RerankProperties();
        rerankProperties.setCandidateLimit(2);
        RagRetrievalService service = service(capturing, rerankProperties);

        VectorSearchResponse response = service.search(
                currentUser,
                new VectorSearchRequest(10L, "Spring Boot", 1));

        assertThat(rerankCandidateCount.get()).isEqualTo(2);
        assertThat(response.items()).hasSize(1);
    }

    private RagRetrievalService service() {
        return service(new NoopRerankService(), new RerankProperties());
    }

    private RagRetrievalService service(RerankService rerankService, RerankProperties rerankProperties) {
        return new RagRetrievalService(
                knowledgeBaseRepository,
                documentRepository,
                embeddingService,
                ragRetrievalRepository,
                new RetrievalProperties(),
                rerankService,
                rerankProperties,
                new ChineseTextSegmenter());
    }

    private KnowledgeBase knowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(1L, "Java Notes", null);
        ReflectionTestUtils.setField(knowledgeBase, "id", 10L);
        return knowledgeBase;
    }
}
