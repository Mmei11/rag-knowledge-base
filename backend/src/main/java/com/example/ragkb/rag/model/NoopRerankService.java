package com.example.ragkb.rag.model;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "app.rerank",
        name = "provider",
        havingValue = "none",
        matchIfMissing = true)
public class NoopRerankService implements RerankService {

    @Override
    public List<RetrievedChunkResponse> rerank(String query, List<RetrievedChunkResponse> candidates) {
        return candidates == null ? List.of() : candidates;
    }
}
