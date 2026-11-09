package com.example.ragkb.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Path;

final class RetrievalEvalDatasetLoader {

    private RetrievalEvalDatasetLoader() {
    }

    static RetrievalEvalDataset load(Path path) throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return mapper.readValue(path.toFile(), RetrievalEvalDataset.class);
    }
}
