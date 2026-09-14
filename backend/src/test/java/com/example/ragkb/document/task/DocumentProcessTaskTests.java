package com.example.ragkb.document.task;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.document.service.DocumentParseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentProcessTaskTests {

    @Mock
    private DocumentParseService documentParseService;

    @Test
    void shouldProcessDocument() {
        DocumentProcessTask task = new DocumentProcessTask(documentParseService);

        task.process(100L);

        verify(documentParseService).process(100L);
    }

    @Test
    void shouldCatchProcessingException() {
        DocumentProcessTask task = new DocumentProcessTask(documentParseService);
        doThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "文档解析失败"))
                .when(documentParseService)
                .process(100L);

        task.process(100L);

        verify(documentParseService).process(100L);
    }
}
