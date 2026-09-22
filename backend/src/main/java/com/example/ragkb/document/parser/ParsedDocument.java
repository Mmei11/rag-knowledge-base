package com.example.ragkb.document.parser;

import java.util.List;
import java.util.stream.Collectors;

public record ParsedDocument(List<ParsedPage> pages) {

    public ParsedDocument {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public static ParsedDocument single(String content) {
        return new ParsedDocument(List.of(new ParsedPage(null, null, content)));
    }

    public String content() {
        return pages.stream()
                .map(ParsedPage::content)
                .filter(content -> !content.isBlank())
                .collect(Collectors.joining("\n\n"));
    }
}
