package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.EmbeddingProperties;
import com.example.ragkb.rag.config.OpenAiProperties;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "app.embedding", name = "provider", havingValue = "openai")
public class OpenAiEmbeddingService implements EmbeddingService {

    private final OpenAiProperties openAiProperties;
    private final EmbeddingProperties embeddingProperties;
    private final RestClient restClient;

    public OpenAiEmbeddingService(
            OpenAiProperties openAiProperties,
            EmbeddingProperties embeddingProperties,
            RestClient.Builder restClientBuilder) {
        this.openAiProperties = openAiProperties;
        this.embeddingProperties = embeddingProperties;
        this.restClient = restClientBuilder
                .baseUrl(openAiProperties.getBaseUrl())
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

        OpenAiEmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(openAiProperties.requireApiKey()))
                .body(new OpenAiEmbeddingRequest(
                        openAiProperties.getEmbeddingModel(),
                        texts,
                        embeddingProperties.getDimension(),
                        "float"))
                .retrieve()
                .body(OpenAiEmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().size() != texts.size()) {
            throw new IllegalStateException("OpenAI embedding response size does not match request size");
        }

        return response.data()
                .stream()
                .sorted(Comparator.comparingInt(OpenAiEmbeddingData::index))
                .map(data -> new EmbeddingVector(data.embedding()))
                .toList();
    }

    private record OpenAiEmbeddingRequest(
            String model,
            List<String> input,
            Integer dimensions,
            String encoding_format) {
    }

    private record OpenAiEmbeddingResponse(List<OpenAiEmbeddingData> data) {
    }

    private record OpenAiEmbeddingData(List<Double> embedding, int index) {
    }
}
