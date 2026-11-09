package com.example.ragkb.eval;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;

/**
 * Stable reference to a chunk in the evaluation dataset.
 *
 * <p>Supported formats: {@code fileName#chunkIndex} and {@code chunkId:<id>}.
 * File names are preferred because chunk IDs change when the database is rebuilt.
 */
record ChunkKey(String fileName, Integer chunkIndex, Long chunkId) {

    static ChunkKey parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("relevantChunks entry must not be blank");
        }
        String value = raw.trim();
        if (value.startsWith("chunkId:")) {
            try {
                return new ChunkKey(null, null, Long.parseLong(value.substring("chunkId:".length())));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid chunkId label: " + raw, exception);
            }
        }

        int separator = value.lastIndexOf('#');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(
                    "relevantChunks entry must be `fileName#chunkIndex` or `chunkId:<id>`: " + raw);
        }
        try {
            return new ChunkKey(
                    value.substring(0, separator),
                    Integer.parseInt(value.substring(separator + 1)),
                    null);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid chunkIndex in label: " + raw, exception);
        }
    }

    boolean matches(RetrievedChunkResponse chunk) {
        if (chunkId != null) {
            return chunkId.equals(chunk.chunkId());
        }
        return chunkIndex == chunk.chunkIndex() && fileName.equals(chunk.fileName());
    }

    String canonical() {
        return chunkId != null ? "chunkId:" + chunkId : fileName + "#" + chunkIndex;
    }
}
