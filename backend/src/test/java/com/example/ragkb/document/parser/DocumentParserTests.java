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
            addPage(document, "PDF parser notes page one");
            addPage(document, "PDF parser notes page two");
            document.save(file.toFile());
        }

        PdfDocumentParser parser = new PdfDocumentParser();
        ParsedDocument parsedDocument = parser.parseDocument(file);

        assertThat(parsedDocument.pages()).hasSize(2);
        assertThat(parsedDocument.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(parsedDocument.pages().get(0).content()).contains("page one");
        assertThat(parsedDocument.pages().get(1).pageNumber()).isEqualTo(2);
        assertThat(parsedDocument.pages().get(1).content()).contains("page two");
        assertThat(parser.parse(file)).contains("page one", "page two");
    }

    @Test
    void shouldRestartDisplayPageNumberAtSecondPrintedPageOne() throws Exception {
        Path file = tempDir.resolve("restarted-pages.pdf");
        try (PDDocument document = new PDDocument()) {
            addPage(document, "Chinese contents\n- 1 -");
            addPage(document, "English contents\n- 2 -");
            addPage(document, "Main body starts\n- 1 -");
            addPage(document, "Second main page\n- 2 -");
            document.save(file.toFile());
        }

        ParsedDocument parsedDocument = new PdfDocumentParser().parseDocument(file);

        assertThat(parsedDocument.pages())
                .extracting(ParsedPage::pdfPageNumber, ParsedPage::pageNumber)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 1),
                        org.assertj.core.groups.Tuple.tuple(2, 2),
                        org.assertj.core.groups.Tuple.tuple(3, 1),
                        org.assertj.core.groups.Tuple.tuple(4, 2));
    }

    private void addPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(50, 700);
            for (String line : text.split("\\n")) {
                contentStream.showText(line);
                contentStream.newLineAtOffset(0, -16);
            }
            contentStream.endText();
        }
    }
}
