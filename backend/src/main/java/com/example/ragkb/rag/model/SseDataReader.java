package com.example.ragkb.rag.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class SseDataReader {

    private SseDataReader() {
    }

    static void read(InputStream inputStream, Consumer<String> onData) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder eventData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    emit(eventData, onData);
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (!eventData.isEmpty()) {
                        eventData.append('\n');
                    }
                    eventData.append(line.substring("data:".length()).stripLeading());
                }
            }
            emit(eventData, onData);
        } catch (IOException ex) {
            throw new IllegalStateException("模型流式响应读取失败", ex);
        }
    }

    private static void emit(StringBuilder eventData, Consumer<String> onData) {
        if (eventData.isEmpty()) {
            return;
        }
        onData.accept(eventData.toString());
        eventData.setLength(0);
    }
}
