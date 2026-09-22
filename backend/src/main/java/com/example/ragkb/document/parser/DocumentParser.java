package com.example.ragkb.document.parser;

import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(String fileType);

    String parse(Path filePath);

    default ParsedDocument parseDocument(Path filePath) {
        return ParsedDocument.single(parse(filePath));
    }
}
