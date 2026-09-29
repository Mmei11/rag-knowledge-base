# 文档解析能力增强：后端业务链路

本文说明本次新增 Word、Excel 解析能力，以及 Markdown 标题分段后，后端如何将上传文件转换为可检索的 RAG Chunk。

## 1. 总体链路

```text
用户上传文件
    ↓
DocumentService 校验扩展名、MIME 类型、文件大小
    ↓
保存原始文件，创建 Document 记录（PENDING）
    ↓
异步任务触发 DocumentParseService.process(documentId)
    ↓
DocumentParserFactory 按 fileType 选择解析器
    ├─ PDF  → PdfDocumentParser
    ├─ TXT  → TextDocumentParser
    ├─ MD   → MarkdownDocumentParser
    ├─ DOCX → DocxDocumentParser
    └─ XLSX → ExcelDocumentParser
    ↓
解析器输出 ParsedDocument（由多个 ParsedPage 组成）
    ↓
DocumentParseService 逐个 ParsedPage 调用 TextChunker
    ↓
生成 TextChunk（1000 字符大小、150 字符重叠）
    ↓
保存 DocumentChunk 文本记录
    ↓
EmbeddingService 批量生成向量
    ↓
保存向量，Document 状态改为 COMPLETED
```

`ParsedPage` 是统一的“逻辑分段”对象，不必一定是真实页码：PDF 的一个物理页、Markdown 的一个标题章节、Excel 的一个工作表，都可以是一个 `ParsedPage`。

## 2. 上传与格式校验

入口类：`DocumentService`

上传前会依次校验：

1. 文件是否为空。
2. 文件大小是否超过 `app.document.max-size-bytes`，默认 10 MB。
3. 文件名是否安全，拒绝带 `..` 的路径穿越文件名。
4. 扩展名是否在白名单中。
5. 浏览器提交的 MIME Type 是否在白名单中。

本次新增白名单：

| 文件类型 | 扩展名 | MIME Type |
| --- | --- | --- |
| Word | `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| Excel | `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |

校验通过后，原文件会保存到文档存储目录，并创建 `Document` 记录。此时不立即执行耗时解析，而是由异步处理任务继续完成解析、切分和向量化。

涉及代码：

- `backend/src/main/java/com/example/ragkb/document/service/DocumentService.java`
- `backend/src/main/java/com/example/ragkb/document/config/DocumentStorageProperties.java`

## 3. 解析器选择

`DocumentParserFactory` 持有 Spring 注入的所有 `DocumentParser` 实现。解析时根据数据库中 `Document.fileType` 调用对应实现的 `supports(fileType)` 方法。

```text
fileType = "docx" → DocxDocumentParser
fileType = "xlsx" → ExcelDocumentParser
fileType = "md"   → MarkdownDocumentParser
```

所有解析器最终都返回同一结构：

```java
ParsedDocument(
    List<ParsedPage> pages
)
```

每个 `ParsedPage` 包含：

```text
pdfPageNumber  PDF 文件中的物理页码；非 PDF 为 null
pageNumber     前端展示页码；非 PDF 为 null
content        该逻辑页或逻辑段的文本
```

统一的数据结构使后续切分、向量化和引用保存不需要关心原始文件格式。

涉及代码：

- `backend/src/main/java/com/example/ragkb/document/parser/DocumentParser.java`
- `backend/src/main/java/com/example/ragkb/document/parser/DocumentParserFactory.java`
- `backend/src/main/java/com/example/ragkb/document/parser/ParsedDocument.java`
- `backend/src/main/java/com/example/ragkb/document/parser/ParsedPage.java`

## 4. 各格式如何转换为逻辑分段

### 4.1 PDF：按物理页

`PdfDocumentParser` 逐页提取 PDF 文本：

```text
PDF 第 1 页 → ParsedPage(physical=1, display=1)
PDF 第 2 页 → ParsedPage(physical=2, display=2)
```

若 PDF 内印刷页码重新从 1 开始，解析器会识别第二次出现的印刷页码 1，重新计算展示页码，避免引用预览展示错误页码。

### 4.2 TXT：整份文本

`TextDocumentParser` 读取 UTF-8 文本，作为一个逻辑段返回。后续由 `TextChunker` 按长度和语义边界切分。

### 4.3 Markdown：按标题章节

`MarkdownDocumentParser` 识别 `#` 到 `######` 标题。每个标题下的正文成为一个独立逻辑段，并在正文前加上标题路径：

```markdown
# 使用指南
## 安装
执行安装命令。
```

会转换为：

```text
使用指南 > 安装
执行安装命令。
```

这样切 Chunk 时不会跨越不同标题章节。解析器也会清理粗体、链接、行内代码等 Markdown 标记，让向量检索面对纯文本语义。

### 4.4 Word：按文档元素读取

`DocxDocumentParser` 使用 Apache POI 依照 Word 文档原始顺序读取元素：

```text
段落 → 表格 → 段落 → 表格 ...
```

普通段落直接保留文本；表格转换为带行号的文本：

```text
表格：
第 1 行：项目 | 要求
第 2 行：冷藏 | 温度不高于 4℃
```

所有非空块之间使用空行连接，作为一个 `ParsedPage` 交给后续切分。因此 Word 会优先在段落边界切开，但由于 Word 不提供稳定的物理页码，本次不会保存页面引用信息。

### 4.5 Excel：按工作表读取

`ExcelDocumentParser` 使用 Apache POI 逐个处理工作表。一个工作表对应一个 `ParsedPage`，因此 Chunk 不会跨工作表。

每个工作表的第一个非空行视为表头，后续行转为“字段名=值”的记录：

```text
工作表：食品清单
字段：名称 | 保质期
第 2 行：名称=牛奶；保质期=2026-10-01
第 3 行：名称=酸奶；保质期=2026-10-03
```

相比直接拼接单元格，字段名会保留在文本中，因此“牛奶保质期”等问题更容易命中正确行。

涉及代码：

- `backend/src/main/java/com/example/ragkb/document/parser/MarkdownDocumentParser.java`
- `backend/src/main/java/com/example/ragkb/document/parser/DocxDocumentParser.java`
- `backend/src/main/java/com/example/ragkb/document/parser/ExcelDocumentParser.java`

## 5. 统一文本切分

入口类：`DocumentParseService`

它遍历 `ParsedDocument.pages()`，逐个将内容传给 `TextChunker`：

```java
textChunker.chunk(
    page.content(),
    nextIndex,
    page.pageNumber(),
    page.pdfPageNumber()
);
```

`nextIndex` 会随着前一逻辑段产生的 Chunk 数递增，因此一份文档内的 `chunkIndex` 始终全局连续。

`TextChunker` 的默认策略是：

```text
Chunk 最大长度：1000 个字符
相邻 Chunk 重叠：150 个字符
```

在接近 1000 字符的位置，切分优先级如下：

1. 段落空行 `\n\n`。
2. 单个换行符。
3. 句末标点，如 `。！？；.!?;`。
4. 没有合适边界时才强制在目标长度处切开。

下一块从上一个块结尾向前约 150 字符的位置开始，并跳过开头空白。这段重叠文本让跨 Chunk 的句子和上下文在相邻向量中都有机会被检索到。

涉及代码：

- `backend/src/main/java/com/example/ragkb/document/service/DocumentParseService.java`
- `backend/src/main/java/com/example/ragkb/document/chunk/TextChunker.java`
- `backend/src/main/java/com/example/ragkb/document/chunk/TextChunk.java`

## 6. 入库与向量化

每个 `TextChunk` 会先保存为一条 `DocumentChunk`：

```text
documentId
knowledgeBaseId
chunkIndex
content
tokenCount（粗略估算）
pageNumber / pdfPageNumber
```

之后 `EmbeddingService.embedBatch(...)` 批量为所有 Chunk 生成向量，并通过 `DocumentChunkEmbeddingRepository` 写回数据库。

```text
Chunk 文本
    ↓
EmbeddingService
    ↓
向量
    ↓
document_chunks.embedding
```

文档处理完成后状态会从 `PROCESSING` 变为 `COMPLETED`。若任一阶段失败，会记录失败进度，并让文档进入失败状态。

## 7. 本次实现的边界

- 已支持：TXT、PDF、Markdown、DOCX、XLSX。
- Word 和 Excel 暂不提供 PDF 式物理页码。
- Word 目前按段落和表格转文本后统一切分，不读取 Word 的版式页码。
- Excel 当前将首个非空行视为表头；适合常规表格，不适合多层表头或复杂合并单元格。
- 网页 URL 导入和图片 OCR 尚未实现，需要后续分别确定安全抓取策略与 OCR 引擎。
