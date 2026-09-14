package com.example.ragkb.rag.service;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.model.EmbeddingVector;
import com.example.ragkb.rag.model.EmbeddingService;
import com.example.ragkb.rag.repository.RagRetrievalRepository;
import com.example.ragkb.security.CurrentUser;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final EmbeddingService embeddingService;
    private final RagRetrievalRepository ragRetrievalRepository;

    public RagRetrievalService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            EmbeddingService embeddingService,
            RagRetrievalRepository ragRetrievalRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.embeddingService = embeddingService;
        this.ragRetrievalRepository = ragRetrievalRepository;
    }

    @Transactional(readOnly = true)
    public VectorSearchResponse search(CurrentUser currentUser, VectorSearchRequest request) {
        knowledgeBaseRepository.findByIdAndUserId(request.knowledgeBaseId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库不存在"));

        String question = request.question().trim();
        int topK = request.normalizedTopK();
        long embeddingStartedAt = System.nanoTime();
        EmbeddingVector queryEmbedding = embeddingService.embedText(question);
        long embeddingDurationMs = elapsedMs(embeddingStartedAt);
        long searchStartedAt = System.nanoTime();
        List<RetrievedChunkResponse> items = ragRetrievalRepository.searchSimilarChunks(
                request.knowledgeBaseId(),
                queryEmbedding,
                topK);
        log.info("vector search completed, userId={}, knowledgeBaseId={}, topK={}, resultCount={}, embeddingMs={}, searchMs={}",
                currentUser.id(),
                request.knowledgeBaseId(),
                topK,
                items.size(),
                embeddingDurationMs,
                elapsedMs(searchStartedAt));

        return new VectorSearchResponse(request.knowledgeBaseId(), question, topK, items);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
