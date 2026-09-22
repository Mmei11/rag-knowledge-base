package com.example.ragkb.document.parser;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Pattern PRINTED_PAGE_ONE = Pattern.compile(
            "(?m)^\\s*[-‐‑–—]\\s*1\\s*[-‐‑–—]\\s*$");

    @Override
    public boolean supports(String fileType) {
        return "pdf".equals(fileType);
    }

    @Override
    public String parse(Path filePath) {
        return parseDocument(filePath).content();
    }

    @Override
    public ParsedDocument parseDocument(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            List<String> pageContents = new ArrayList<>();
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                textStripper.setStartPage(pageNumber);
                textStripper.setEndPage(pageNumber);
                pageContents.add(textStripper.getText(document));
            }

            int restartPage = findSecondPrintedPageOne(pageContents);
            List<ParsedPage> pages = new ArrayList<>();
            for (int index = 0; index < pageContents.size(); index++) {
                int pdfPageNumber = index + 1;
                int displayPageNumber = restartPage > 0 && pdfPageNumber >= restartPage
                        ? pdfPageNumber - restartPage + 1
                        : pdfPageNumber;
                pages.add(new ParsedPage(pdfPageNumber, displayPageNumber, pageContents.get(index)));
            }
            return new ParsedDocument(pages);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PDF 文档解析失败");
        }
    }

    private int findSecondPrintedPageOne(List<String> pageContents) {
        boolean foundFirstPageOne = false;
        for (int index = 0; index < pageContents.size(); index++) {
            if (!PRINTED_PAGE_ONE.matcher(pageContents.get(index)).find()) {
                continue;
            }
            if (foundFirstPageOne) {
                return index + 1;
            }
            foundFirstPageOne = true;
        }
        return -1;
    }
}
