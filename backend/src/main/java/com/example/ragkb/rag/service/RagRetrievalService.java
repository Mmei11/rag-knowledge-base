package com.example.ragkb.rag.service;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.common.text.ChineseTextSegmenter;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.knowledgebase.service.KnowledgeBaseAccessService;
import com.example.ragkb.rag.config.RerankProperties;
import com.example.ragkb.rag.config.RetrievalProperties;
import com.example.ragkb.rag.dto.RetrievalFilters;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.model.EmbeddingService;
import com.example.ragkb.rag.model.RerankService;
import com.example.ragkb.rag.repository.RagRetrievalRepository;
import com.example.ragkb.security.CurrentUser;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);
    private static final int RRF_RANKING_CONSTANT = 60;

    private final KnowledgeBaseAccessService knowledgeBaseAccessService;
    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final RagRetrievalRepository ragRetrievalRepository;
    private final RetrievalProperties retrievalProperties;
    private final RerankService rerankService;
    private final RerankProperties rerankProperties;
    private final ChineseTextSegmenter textSegmenter;

    public RagRetrievalService(
            KnowledgeBaseAccessService knowledgeBaseAccessService,
            DocumentRepository documentRepository,
            EmbeddingService embeddingService,
            RagRetrievalRepository ragRetrievalRepository,
            RetrievalProperties retrievalProperties,
            RerankService rerankService,
            RerankProperties rerankProperties,
            ChineseTextSegmenter textSegmenter) {
        this.knowledgeBaseAccessService = knowledgeBaseAccessService;
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.retrievalProperties = retrievalProperties;
        this.rerankService = rerankService;
        this.rerankProperties = rerankProperties;
        this.textSegmenter = textSegmenter;
    }

    @Transactional(readOnly = true)
    public VectorSearchResponse search(CurrentUser currentUser, VectorSearchRequest request) {
        knowledgeBaseAccessService.requireVisible(currentUser, request.knowledgeBaseId());

        String question = request.question().trim();
        int topK = request.normalizedTopK();
        RetrievalFilters filters = new RetrievalFilters(
                request.normalizedDocumentIds(), request.startDate(), request.endDate());
        validateSelectedDocuments(request.knowledgeBaseId(), filters);
        double similarityThreshold = request.similarityThreshold() == null
                ? retrievalProperties.getSimilarityThreshold()
                : request.similarityThreshold();
        int candidateLimit = topK * retrievalProperties.getCandidateMultiplier();
        long embeddingStartedAt = System.nanoTime();
        EmbeddingVector queryEmbedding = embeddingService.embedText(question);
        long embeddingDurationMs = elapsedMs(embeddingStartedAt);
        long searchStartedAt = System.nanoTime();
        List<RetrievedChunkResponse> vectorItems = ragRetrievalRepository.searchSimilarChunks(
                request.knowledgeBaseId(),
                queryEmbedding,
                candidateLimit,
                similarityThreshold,
                filters);
        List<RetrievedChunkResponse> keywordItems = ragRetrievalRepository.searchKeywordChunks(
                request.knowledgeBaseId(),
                queryEmbedding,
                textSegmenter.segmentForQuery(question),
                candidateLimit,
                filters);
        List<RetrievedChunkResponse> fused = fuseResults(vectorItems, keywordItems, similarityThreshold);
        int rerankCandidateCount = Math.min(fused.size(), rerankProperties.effectiveCandidateLimit(topK));
        List<RetrievedChunkResponse> rerankCandidates = fused.stream()
                .limit(rerankCandidateCount)
                .toList();
        long rerankStartedAt = System.nanoTime();
        List<RetrievedChunkResponse> reranked = rerankService.rerank(question, rerankCandidates);
        long rerankDurationMs = elapsedMs(rerankStartedAt);
        List<RetrievedChunkResponse> items = reranked.stream()
                .limit(topK)
                .toList();
        log.info("hybrid search completed, userId={}, knowledgeBaseId={}, topK={}, threshold={}, documentFilterCount={}, startDate={}, endDate={}, vectorResultCount={}, keywordResultCount={}, rerankProvider={}, rerankCandidateCount={}, rerankMs={}, resultCount={}, embeddingMs={}, searchMs={}",
                currentUser.id(),
                request.knowledgeBaseId(),
                topK,
                similarityThreshold,
                filters.documentIds().size(),
                filters.startDate(),
                filters.endDate(),
                vectorItems.size(),
                keywordItems.size(),
                rerankProperties.getProvider(),
                rerankCandidateCount,
                rerankDurationMs,
                items.size(),
                embeddingDurationMs,
                elapsedMs(searchStartedAt));

        return new VectorSearchResponse(request.knowledgeBaseId(), question, topK, items);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void validateSelectedDocuments(Long knowledgeBaseId, RetrievalFilters filters) {
        if (!filters.hasDocumentIds()) {
            return;
        }

        long matchedDocumentCount = documentRepository.countByIdInAndKnowledgeBaseId(
                filters.documentIds(), knowledgeBaseId);
        if (matchedDocumentCount != filters.documentIds().size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "筛选文档不存在或不属于当前知识库");
        }
    }

    private List<RetrievedChunkResponse> fuseResults(
            List<RetrievedChunkResponse> vectorItems,
            List<RetrievedChunkResponse> keywordItems,
            double similarityThreshold) {
        Map<Long, RankedChunk> rankedChunks = new HashMap<>();
        addReciprocalRankScores(rankedChunks, vectorItems);
        addReciprocalRankScores(rankedChunks, keywordItems);

        return rankedChunks.values().stream()
                .filter(item -> item.chunk().similarity() >= similarityThreshold)
                .sorted((left, right) -> {
                    int scoreComparison = Double.compare(right.score(), left.score());
                    if (scoreComparison != 0) {
                        return scoreComparison;
                    }
                    return Double.compare(right.chunk().similarity(), left.chunk().similarity());
                })
                .map(RankedChunk::chunk)
                .toList();
    }

    private void addReciprocalRankScores(
            Map<Long, RankedChunk> rankedChunks,
            List<RetrievedChunkResponse> items) {
        for (int index = 0; index < items.size(); index++) {
            RetrievedChunkResponse item = items.get(index);
            double score = 1.0 / (RRF_RANKING_CONSTANT + index + 1);
            rankedChunks.merge(
                    item.chunkId(),
                    new RankedChunk(item, score),
                    (current, incoming) -> new RankedChunk(
                            current.chunk().similarity() >= incoming.chunk().similarity()
                                    ? current.chunk()
                                    : incoming.chunk(),
                            current.score() + incoming.score()));
        }
    }

    private record RankedChunk(RetrievedChunkResponse chunk, double score) {
    }
}
