package com.example.ragkb.rag.service;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.rag.config.RetrievalProperties;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.model.EmbeddingService;
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

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final EmbeddingService embeddingService;
    private final RagRetrievalRepository ragRetrievalRepository;
    private final RetrievalProperties retrievalProperties;

    public RagRetrievalService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            EmbeddingService embeddingService,
            RagRetrievalRepository ragRetrievalRepository,
            RetrievalProperties retrievalProperties) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.embeddingService = embeddingService;
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.retrievalProperties = retrievalProperties;
    }

    @Transactional(readOnly = true)
    public VectorSearchResponse search(CurrentUser currentUser, VectorSearchRequest request) {
        knowledgeBaseRepository.findByIdAndUserId(request.knowledgeBaseId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库不存在"));

        String question = request.question().trim();
        int topK = request.normalizedTopK();
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
                similarityThreshold);
        List<RetrievedChunkResponse> keywordItems = ragRetrievalRepository.searchKeywordChunks(
                request.knowledgeBaseId(),
                queryEmbedding,
                SearchKeywordExtractor.extract(question),
                candidateLimit);
        List<RetrievedChunkResponse> items = fuseResults(vectorItems, keywordItems, topK, similarityThreshold);
        log.info("hybrid search completed, userId={}, knowledgeBaseId={}, topK={}, threshold={}, vectorResultCount={}, keywordResultCount={}, resultCount={}, embeddingMs={}, searchMs={}",
                currentUser.id(),
                request.knowledgeBaseId(),
                topK,
                similarityThreshold,
                vectorItems.size(),
                keywordItems.size(),
                items.size(),
                embeddingDurationMs,
                elapsedMs(searchStartedAt));

        return new VectorSearchResponse(request.knowledgeBaseId(), question, topK, items);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private List<RetrievedChunkResponse> fuseResults(
            List<RetrievedChunkResponse> vectorItems,
            List<RetrievedChunkResponse> keywordItems,
            int topK,
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
                .limit(topK)
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
