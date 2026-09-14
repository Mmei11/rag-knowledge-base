package com.example.ragkb.document.parser;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "txt".equals(fileType);
    }

    @Override
    public String parse(Path filePath) {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "TXT 文档读取失败");
        }
    }
}
