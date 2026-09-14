# Day 13：文档文本解析

## 今日目标

让系统可以从已经上传的文档中提取纯文本。

支持文件类型：

- TXT
- Markdown
- PDF

## 新增接口

```text
POST /api/documents/{documentId}/parse
```

作用：

```text
手动触发某个文档的文本解析。
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "documentId": 10,
    "status": "COMPLETED",
    "characterCount": 32,
    "content": "Spring Boot document notes"
  }
}
```

## 新增依赖

```text
org.apache.pdfbox:pdfbox:3.0.5
```

用于从 PDF 文件中提取文本。

## 新增文件

```text
document/dto/DocumentParseResponse.java
document/parser/DocumentParser.java
document/parser/TextDocumentParser.java
document/parser/MarkdownDocumentParser.java
document/parser/PdfDocumentParser.java
document/parser/DocumentParserFactory.java
document/service/DocumentParseService.java
```

## 修改文件

```text
pom.xml
document/entity/Document.java
document/controller/DocumentController.java
document/controller/DocumentControllerTests.java
```

## 核心设计

### 统一解析接口

```java
public interface DocumentParser {
    boolean supports(String fileType);
    String parse(Path filePath);
}
```

不同文件类型有不同解析器：

```text
txt       -> TextDocumentParser
md        -> MarkdownDocumentParser
markdown  -> MarkdownDocumentParser
pdf       -> PdfDocumentParser
```

这是一种简单的策略模式。

### 解析器选择

`DocumentParserFactory` 持有所有 `DocumentParser`，根据 `fileType` 选择合适的解析器。

```text
DocumentParseService
        ↓
DocumentParserFactory
        ↓
Text / Markdown / PDF Parser
```

## 状态流转

解析开始：

```text
PENDING -> PROCESSING
```

解析成功：

```text
PROCESSING -> COMPLETED
```

解析失败：

```text
PROCESSING -> FAILED
```

失败原因会写入：

```text
documents.error_message
```

## 当前限制

Day 13 只做文本提取，并把提取结果通过接口返回。

暂时还没有把解析后的全文持久化到数据库，因为 Day 14 会继续做文本切分，并把分块内容写入 `document_chunks` 表。

## 测试验证

已覆盖：

- TXT 文件解析
- Markdown 文件解析
- PDF 文件解析
- 解析成功后状态变为 `COMPLETED`
- 空内容解析失败后状态变为 `FAILED`
- 只能解析自己的文档

测试结果：

```text
mvn test
Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
```

## 今日重点

- 文件解析要通过接口抽象，不要把所有逻辑写在一个方法里。
- PDF 不能直接按普通文本读取，需要 PDFBox。
- 解析过程要有状态流转。
- 解析失败要记录原因，而不是只抛异常。
