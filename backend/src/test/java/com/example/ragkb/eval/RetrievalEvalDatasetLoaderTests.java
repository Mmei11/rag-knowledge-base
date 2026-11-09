package com.example.ragkb.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetrievalEvalDatasetLoaderTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadDatasetWithDefaultsAndIgnoreUnknownFields() throws IOException {
        Path datasetPath = tempDir.resolve("dataset.json");
        Files.writeString(datasetPath, """
                {
                  "name": "demo",
                  "_comment": "unknown fields are allowed",
                  "username": "alice",
                  "knowledgeBaseName": "kb",
                  "cases": [
                    {
                      "question": "what is food safety?",
                      "relevantChunks": ["doc.pdf#1"]
                    }
                  ]
                }
                """);

        RetrievalEvalDataset dataset = RetrievalEvalDatasetLoader.load(datasetPath);

        assertThat(dataset.name()).isEqualTo("demo");
        assertThat(dataset.ks()).containsExactly(1, 3, 5, 10);
        assertThat(dataset.effectiveTopK()).isEqualTo(10);
        assertThat(dataset.cases()).singleElement()
                .satisfies(evalCase -> {
                    assertThat(evalCase.id()).isEqualTo("what is food safety?");
                    assertThat(evalCase.relevantChunks()).containsExactly("doc.pdf#1");
                    assertThat(evalCase.chunkKeys()).singleElement()
                            .satisfies(key -> assertThat(key.canonical()).isEqualTo("doc.pdf#1"));
                });
    }

    @Test
    void shouldRejectDatasetWithoutTarget() throws IOException {
        Path datasetPath = tempDir.resolve("invalid.json");
        Files.writeString(datasetPath, """
                {
                  "name": "invalid",
                  "cases": [
                    {"question": "q", "relevantChunks": ["doc.pdf#1"]}
                  ]
                }
                """);

        assertThatThrownBy(() -> RetrievalEvalDatasetLoader.load(datasetPath))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
