package com.example.ragkb.document.parser;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public DocumentParser getParser(String fileType) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT, "不支持的文档类型"));
    }
}
