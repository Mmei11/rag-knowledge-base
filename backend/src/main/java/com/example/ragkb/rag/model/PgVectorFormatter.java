package com.example.ragkb.rag.model;

import java.util.Locale;
import java.util.stream.Collectors;

public final class PgVectorFormatter {

    private PgVectorFormatter() {
    }

    public static String format(EmbeddingVector vector) {
        if (vector.values().isEmpty()) {
            throw new IllegalArgumentException("embedding vector must not be empty");
        }

        return vector.values().stream()
                .map(PgVectorFormatter::formatValue)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String formatValue(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException("embedding vector contains invalid value");
        }
        return String.format(Locale.ROOT, "%.8f", value);
    }
}
