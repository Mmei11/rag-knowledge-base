package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.EmbeddingProperties;
import com.example.ragkb.rag.config.QwenProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "app.embedding", name = "provider", havingValue = "qwen")
public class QwenEmbeddingService implements EmbeddingService {

    private final QwenProperties qwenProperties;
    private final EmbeddingProperties embeddingProperties;
    private final RestClient restClient;

    public QwenEmbeddingService(
            QwenProperties qwenProperties,
            EmbeddingProperties embeddingProperties,
            RestClient.Builder restClientBuilder) {
        this.qwenProperties = qwenProperties;
        this.embeddingProperties = embeddingProperties;
        this.restClient = restClientBuilder
                .baseUrl(qwenProperties.getBaseUrl())
                .build();
    }

    @Override
    public EmbeddingVector embedText(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @Override
    public List<EmbeddingVector> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("embedding input must not contain blank text");
        }

        List<EmbeddingVector> embeddings = new ArrayList<>(texts.size());
        int batchSize = qwenProperties.getEmbeddingBatchSize();
        for (int from = 0; from < texts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, texts.size());
            embeddings.addAll(embedBatchRequest(texts.subList(from, to)));
        }
        return List.copyOf(embeddings);
    }

    private List<EmbeddingVector> embedBatchRequest(List<String> texts) {
        QwenEmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(qwenProperties.requireApiKey()))
                .body(new QwenEmbeddingRequest(
                        qwenProperties.getEmbeddingModel(),
                        texts,
                        embeddingProperties.getDimension(),
                        "float"))
                .retrieve()
                .body(QwenEmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().size() != texts.size()) {
            throw new IllegalStateException("Qwen embedding response size does not match request size");
        }

        return response.data()
                .stream()
                .sorted(Comparator.comparingInt(QwenEmbeddingData::index))
                .map(data -> new EmbeddingVector(data.embedding()))
                .toList();
    }

    private record QwenEmbeddingRequest(
            String model,
            List<String> input,
            Integer dimensions,
            String encoding_format) {
    }

    private record QwenEmbeddingResponse(List<QwenEmbeddingData> data) {
    }

    private record QwenEmbeddingData(List<Double> embedding, int index) {
    }
}
