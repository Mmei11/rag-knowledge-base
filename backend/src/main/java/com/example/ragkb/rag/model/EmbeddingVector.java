package com.example.ragkb.rag.model;

import java.util.List;

public record EmbeddingVector(List<Double> values) {

    public EmbeddingVector {
        values = List.copyOf(values);
    }

    public int dimension() {
        return values.size();
    }
}
