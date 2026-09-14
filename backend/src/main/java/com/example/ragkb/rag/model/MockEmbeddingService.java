package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.EmbeddingProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.embedding", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockEmbeddingService implements EmbeddingService {

    private static final String SHA_256 = "SHA-256";

    private final EmbeddingProperties properties;

    public MockEmbeddingService(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @Override
    public EmbeddingVector embedText(String text) {
        int dimension = properties.getDimension();
        SplittableRandom random = new SplittableRandom(seedOf(text));

        List<Double> values = new ArrayList<>(dimension);
        double squaredSum = 0.0;
        for (int i = 0; i < dimension; i++) {
            double value = random.nextDouble(-1.0, 1.0);
            values.add(value);
            squaredSum += value * value;
        }

        double norm = Math.sqrt(squaredSum);
        if (norm == 0.0) {
            return new EmbeddingVector(values);
        }

        return new EmbeddingVector(values.stream()
                .map(value -> value / norm)
                .toList());
    }

    private long seedOf(String text) {
        String normalized = text == null ? "" : text.trim();
        byte[] digest = sha256(normalized);

        long seed = 0L;
        for (int i = 0; i < Long.BYTES; i++) {
            seed = (seed << 8) | (digest[i] & 0xffL);
        }
        return seed;
    }

    private byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance(SHA_256)
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
