package com.example.ragkb.rag.model;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.List;

public interface RerankService {

    List<RetrievedChunkResponse> rerank(String query, List<RetrievedChunkResponse> candidates);
}
