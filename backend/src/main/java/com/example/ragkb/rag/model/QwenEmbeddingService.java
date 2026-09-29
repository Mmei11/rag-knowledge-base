package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.EmbeddingProperties;
import com.example.ragkb.rag.config.QwenProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final Executor embeddingRequestExecutor;

    public QwenEmbeddingService(
            QwenProperties qwenProperties,
            EmbeddingProperties embeddingProperties,
            RestClient.Builder restClientBuilder,
            @Qualifier("embeddingRequestExecutor") Executor embeddingRequestExecutor) {
        this.qwenProperties = qwenProperties;
        this.embeddingProperties = embeddingProperties;
        this.embeddingRequestExecutor = embeddingRequestExecutor;
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
        return embedBatch(texts, progress -> {
        });
    }

    @Override
    public List<EmbeddingVector> embedBatch(
            List<String> texts,
            Consumer<EmbeddingBatchProgress> progressListener) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("embedding input must not contain blank text");
        }

        int batchSize = qwenProperties.getEmbeddingBatchSize();
        List<List<String>> batches = new ArrayList<>();
        for (int from = 0; from < texts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, texts.size());
            batches.add(List.copyOf(texts.subList(from, to)));
        }

        long startedAt = System.nanoTime();
        ExecutorCompletionService<EmbeddingBatchResult> completionService =
                new ExecutorCompletionService<>(embeddingRequestExecutor);
        List<Future<EmbeddingBatchResult>> submittedRequests = new ArrayList<>();
        List<List<EmbeddingVector>> results = new ArrayList<>(java.util.Collections.nCopies(batches.size(), null));
        int maxInFlight = Math.min(qwenProperties.getEmbeddingConcurrency(), batches.size());
        int nextBatchIndex = 0;

        for (; nextBatchIndex < maxInFlight; nextBatchIndex++) {
            submittedRequests.add(submitBatch(completionService, batches, nextBatchIndex));
        }

        int completedItems = 0;
        try {
            for (int completedBatchCount = 0; completedBatchCount < batches.size(); completedBatchCount++) {
                EmbeddingBatchResult result = completionService.take().get();
                results.set(result.index(), result.embeddings());
                completedItems += result.itemCount();
                long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
                progressListener.accept(new EmbeddingBatchProgress(completedItems, texts.size(), elapsedMillis));

                if (nextBatchIndex < batches.size()) {
                    submittedRequests.add(submitBatch(completionService, batches, nextBatchIndex));
                    nextBatchIndex++;
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            cancelOutstandingRequests(submittedRequests);
            throw new IllegalStateException("Qwen embedding request was interrupted", ex);
        } catch (ExecutionException ex) {
            cancelOutstandingRequests(submittedRequests);
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Qwen embedding request failed", ex.getCause());
        }

        List<EmbeddingVector> embeddings = new ArrayList<>(texts.size());
        results.forEach(embeddings::addAll);
        return List.copyOf(embeddings);
    }

    private Future<EmbeddingBatchResult> submitBatch(
            ExecutorCompletionService<EmbeddingBatchResult> completionService,
            List<List<String>> batches,
            int batchIndex) {
        List<String> batch = batches.get(batchIndex);
        return completionService.submit(() -> new EmbeddingBatchResult(
                batchIndex,
                batch.size(),
                embedBatchRequest(batch)));
    }

    private void cancelOutstandingRequests(List<Future<EmbeddingBatchResult>> submittedRequests) {
        submittedRequests.forEach(request -> request.cancel(true));
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

    private record EmbeddingBatchResult(int index, int itemCount, List<EmbeddingVector> embeddings) {
    }
}
