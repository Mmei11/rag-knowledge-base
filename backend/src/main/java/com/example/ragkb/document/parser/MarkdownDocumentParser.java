package com.example.ragkb.document.parser;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MarkdownDocumentParser implements DocumentParser {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern TABLE_DIVIDER = Pattern.compile(
            "^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");

    @Override
    public boolean supports(String fileType) {
        return "md".equals(fileType) || "markdown".equals(fileType);
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
        try {
            String markdown = Files.readString(filePath, StandardCharsets.UTF_8)
                    .replace("\uFEFF", "")
                    .replace("\r\n", "\n")
                    .replace('\r', '\n');
            String[] lines = markdown.split("\\n", -1);
            List<String> headingPath = new ArrayList<>();
            List<ParsedPage> sections = new ArrayList<>();
            StringBuilder sectionBody = new StringBuilder();
            int totalLines = Math.max(lines.length, 1);

            for (int index = 0; index < lines.length; index++) {
                Matcher headingMatcher = HEADING.matcher(lines[index]);
                if (headingMatcher.matches()) {
                    addSection(sections, headingPath, sectionBody);
                    updateHeadingPath(headingPath, headingMatcher.group(1).length(), headingMatcher.group(2));
                    sectionBody.setLength(0);
                } else {
                    String normalizedLine = normalizeLine(lines[index]);
                    if (!normalizedLine.isBlank()) {
                        if (!sectionBody.isEmpty()) {
                            sectionBody.append('\n');
                        }
                        sectionBody.append(normalizedLine);
                    }
                }
                onProgress.accept((index + 1) * 100 / totalLines);
            }
            addSection(sections, headingPath, sectionBody);

            if (sections.isEmpty()) {
                return ParsedDocument.single(normalizeLine(markdown));
            }
            return new ParsedDocument(sections);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Markdown 文档读取失败");
        }
    }

    private void addSection(List<ParsedPage> sections, List<String> headingPath, StringBuilder body) {
        String normalizedBody = body.toString().trim();
        if (normalizedBody.isBlank()) {
            return;
        }
        String title = String.join(" > ", headingPath);
        String content = title.isBlank() ? normalizedBody : title + "\n" + normalizedBody;
        sections.add(new ParsedPage(null, null, content));
    }

    private void updateHeadingPath(List<String> headingPath, int level, String title) {
        while (headingPath.size() >= level) {
            headingPath.removeLast();
        }
        headingPath.add(normalizeInline(title));
    }

    private String normalizeLine(String line) {
        String normalized = line == null ? "" : line.trim();
        if (TABLE_DIVIDER.matcher(normalized).matches() || normalized.startsWith("```")) {
            return "";
        }
        normalized = normalized.replaceFirst("^>\\s?", "");
        if (normalized.startsWith("|") && normalized.endsWith("|")) {
            normalized = "表格行：" + normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalizeInline(normalized);
    }

    private String normalizeInline(String value) {
        return value
                .replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .replaceAll("`{1,3}([^`]+)`{1,3}", "$1")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("__([^_]+)__", "$1")
                .replaceAll("\\*([^*]+)\\*", "$1")
                .replaceAll("_([^_]+)_", "$1")
                .trim();
    }
}
