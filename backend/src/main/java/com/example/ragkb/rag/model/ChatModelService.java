package com.example.ragkb.rag.model;

import java.util.Objects;
import java.util.function.Consumer;

public interface ChatModelService {

    String generate(String prompt);

    default void generateStream(String prompt, Consumer<String> onDelta) {
        Objects.requireNonNull(onDelta, "onDelta must not be null");
        onDelta.accept(generate(prompt));
    }
}
