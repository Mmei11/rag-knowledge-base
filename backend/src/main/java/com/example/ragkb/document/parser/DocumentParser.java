package com.example.ragkb.document.parser;

import java.nio.file.Path;
import java.util.function.IntConsumer;

public interface DocumentParser {

    boolean supports(String fileType);

    String parse(Path filePath);

    default ParsedDocument parseDocument(Path filePath) {
        return ParsedDocument.single(parse(filePath));
    }

    default ParsedDocument parseDocument(Path filePath, IntConsumer onProgress) {
        ParsedDocument parsedDocument = parseDocument(filePath);
        onProgress.accept(100);
        return parsedDocument;
    }
}
