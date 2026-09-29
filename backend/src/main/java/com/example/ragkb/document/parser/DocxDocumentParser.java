package com.example.ragkb.document.parser;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "docx".equals(fileType);
    }

    @Override
    public String parse(Path filePath) {
        return parseDocument(filePath).content();
    }

    @Override
    public ParsedDocument parseDocument(Path filePath) {
        return parseDocument(filePath, ignored -> {
        });
    }

    @Override
    public ParsedDocument parseDocument(Path filePath, IntConsumer onProgress) {
        try (InputStream inputStream = Files.newInputStream(filePath);
                XWPFDocument document = new XWPFDocument(inputStream)) {
            List<IBodyElement> elements = document.getBodyElements();
            int totalElements = Math.max(elements.size(), 1);
            List<String> blocks = new ArrayList<>();

            for (int index = 0; index < elements.size(); index++) {
                String content = parseElement(elements.get(index));
                if (!content.isBlank()) {
                    blocks.add(content);
                }
                onProgress.accept((index + 1) * 100 / totalElements);
            }
            if (elements.isEmpty()) {
                onProgress.accept(100);
            }
            return ParsedDocument.single(String.join("\n\n", blocks));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Word 文档解析失败");
        }
    }

    private String parseElement(IBodyElement element) {
        if (element instanceof XWPFParagraph paragraph) {
            return normalize(paragraph.getText());
        }
        if (element instanceof XWPFTable table) {
            return parseTable(table);
        }
        return "";
    }

    private String parseTable(XWPFTable table) {
        List<String> rows = new ArrayList<>();
        List<XWPFTableRow> tableRows = table.getRows();
        for (int rowIndex = 0; rowIndex < tableRows.size(); rowIndex++) {
            List<String> cells = tableRows.get(rowIndex).getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .map(this::normalize)
                    .toList();
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add("第 %d 行：%s".formatted(rowIndex + 1, String.join(" | ", cells)));
        }
        return rows.isEmpty() ? "" : "表格：\n" + String.join("\n", rows);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }
}
