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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class ExcelDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "xlsx".equals(fileType);
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
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int sheetCount = Math.max(workbook.getNumberOfSheets(), 1);
            List<ParsedPage> sheets = new ArrayList<>();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                String content = parseSheet(workbook.getSheetAt(sheetIndex), formatter, evaluator);
                if (!content.isBlank()) {
                    sheets.add(new ParsedPage(null, null, content));
                }
                onProgress.accept((sheetIndex + 1) * 100 / sheetCount);
            }
            if (workbook.getNumberOfSheets() == 0) {
                onProgress.accept(100);
            }
            return new ParsedDocument(sheets);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Excel 文档解析失败");
        }
    }

    private String parseSheet(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> rows = new ArrayList<>();
        List<String> headers = List.of();
        boolean hasContent = false;

        for (Row row : sheet) {
            List<String> values = rowValues(row, formatter, evaluator);
            if (values.isEmpty() || values.stream().allMatch(String::isBlank)) {
                continue;
            }
            hasContent = true;
            if (headers.isEmpty()) {
                headers = values;
                rows.add("字段：" + String.join(" | ", headers));
                continue;
            }
            rows.add("第 %d 行：%s".formatted(
                    row.getRowNum() + 1,
                    labeledRow(headers, values)));
        }

        if (!hasContent) {
            return "";
        }
        return "工作表：%s\n%s".formatted(sheet.getSheetName(), String.join("\n", rows));
    }

    private List<String> rowValues(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        int lastCell = Math.max(row.getLastCellNum(), 0);
        List<String> values = new ArrayList<>(lastCell);
        for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
            Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(cell == null ? "" : normalize(formatter.formatCellValue(cell, evaluator)));
        }
        return values;
    }

    private String labeledRow(List<String> headers, List<String> values) {
        List<String> cells = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value.isBlank()) {
                continue;
            }
            String header = index < headers.size() && !headers.get(index).isBlank()
                    ? headers.get(index)
                    : "第 %d 列".formatted(index + 1);
            cells.add(header + "=" + value);
        }
        return String.join("；", cells);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
