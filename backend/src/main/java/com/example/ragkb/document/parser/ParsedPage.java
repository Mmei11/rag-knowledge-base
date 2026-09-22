package com.example.ragkb.document.parser;

public record ParsedPage(Integer pdfPageNumber, Integer pageNumber, String content) {

    public ParsedPage {
        if (pdfPageNumber != null && pdfPageNumber <= 0) {
            throw new IllegalArgumentException("PDF page number must be positive");
        }
        if (pageNumber != null && pageNumber <= 0) {
            throw new IllegalArgumentException("page number must be positive");
        }
        content = content == null ? "" : content;
    }

    public ParsedPage(Integer pageNumber, String content) {
        this(pageNumber, pageNumber, content);
    }
}
