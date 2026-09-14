package com.example.ragkb.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentParserTests {

    @TempDir
    private Path tempDir;

    @Test
    void shouldParseTextFile() throws Exception {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Spring Boot notes");

        String content = new TextDocumentParser().parse(file);

        assertThat(content).contains("Spring Boot notes");
    }

    @Test
    void shouldParseMarkdownFile() throws Exception {
        Path file = tempDir.resolve("notes.md");
        Files.writeString(file, "\uFEFF# Title\n\nThis is **important** [link](https://example.com).");

        String content = new MarkdownDocumentParser().parse(file);

        assertThat(content).contains("Title");
        assertThat(content).contains("important");
        assertThat(content).contains("link");
        assertThat(content).doesNotContain("**");
        assertThat(content).doesNotContain("\uFEFF");
    }

    @Test
    void shouldParsePdfFile() throws Exception {
        Path file = tempDir.resolve("notes.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("PDF parser notes");
                contentStream.endText();
            }
            document.save(file.toFile());
        }

        String content = new PdfDocumentParser().parse(file);

        assertThat(content).contains("PDF parser notes");
    }
}
