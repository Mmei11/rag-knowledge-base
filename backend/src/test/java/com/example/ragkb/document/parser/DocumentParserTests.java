package com.example.ragkb.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
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
    void shouldSplitMarkdownByHeadingAndKeepHeadingPath() throws Exception {
        Path file = tempDir.resolve("guide.md");
        Files.writeString(file, """
                # 使用指南
                ## 安装
                执行安装命令。
                ## 配置
                修改应用配置。
                """);

        ParsedDocument document = new MarkdownDocumentParser().parseDocument(file);

        assertThat(document.pages()).hasSize(2);
        assertThat(document.pages().get(0).content()).contains("使用指南 > 安装", "执行安装命令");
        assertThat(document.pages().get(1).content()).contains("使用指南 > 配置", "修改应用配置");
    }

    @Test
    void shouldParseDocxParagraphsAndTables() throws Exception {
        Path file = tempDir.resolve("notes.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream outputStream = Files.newOutputStream(file)) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("食品安全检查项目");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("项目");
            table.getRow(0).getCell(1).setText("要求");
            table.getRow(1).getCell(0).setText("冷藏");
            table.getRow(1).getCell(1).setText("温度不高于 4℃");
            document.write(outputStream);
        }

        String content = new DocxDocumentParser().parse(file);

        assertThat(content).contains("食品安全检查项目", "表格：", "项目 | 要求", "冷藏 | 温度不高于 4℃");
    }

    @Test
    void shouldParseExcelSheetsAsLabeledRows() throws Exception {
        Path file = tempDir.resolve("food.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                OutputStream outputStream = Files.newOutputStream(file)) {
            var sheet = workbook.createSheet("食品清单");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("名称");
            header.createCell(1).setCellValue("保质期");
            var item = sheet.createRow(1);
            item.createCell(0).setCellValue("牛奶");
            item.createCell(1).setCellValue("2026-10-01");
            workbook.write(outputStream);
        }

        String content = new ExcelDocumentParser().parse(file);

        assertThat(content).contains("工作表：食品清单", "字段：名称 | 保质期", "名称=牛奶", "保质期=2026-10-01");
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
