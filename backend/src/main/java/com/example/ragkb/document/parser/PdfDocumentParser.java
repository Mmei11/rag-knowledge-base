package com.example.ragkb.document.parser;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "pdf".equals(fileType);
    }

    @Override
    public String parse(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PDF 文档解析失败");
        }
    }
}
