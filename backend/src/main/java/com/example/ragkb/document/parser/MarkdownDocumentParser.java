package com.example.ragkb.document.parser;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "md".equals(fileType) || "markdown".equals(fileType);
    }

    @Override
    public String parse(Path filePath) {
        try {
            String markdown = Files.readString(filePath, StandardCharsets.UTF_8);
            return markdown
                    .replace("\uFEFF", "")
                    .replaceAll("(?m)^#{1,6}\\s*", "")
                    .replaceAll("(?m)^>\\s*", "")
                    .replaceAll("`{1,3}", "")
                    .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                    .replaceAll("\\*([^*]+)\\*", "$1")
                    .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Markdown 文档读取失败");
        }
    }
}
