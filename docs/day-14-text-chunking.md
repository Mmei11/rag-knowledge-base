# Day 14：文本切分 Chunking

## 今日目标

把 Day 13 解析出来的全文切成多个较小的文本块，并写入 `document_chunks` 表。

这一步是后续向量化和相似度检索的基础。

## 为什么要切分

RAG 不适合直接拿整篇文档做检索。

原因是：

- 文档太长，向量表达会变得不精确。
- 用户问题通常只命中文档中的某一小段。
- 大模型上下文有限，不能无限塞全文。
- 切成 chunk 后，可以只取最相关的几个片段构造 Prompt。

## 核心概念

### chunk size

每个文本块的目标长度。

当前第一版使用：

```text
1000 字符
```

### overlap

相邻 chunk 之间保留一小段重叠内容，避免关键信息刚好被切断。

当前第一版使用：

```text
150 字符
```

示意：

```text
chunk 0: 0    - 1000
chunk 1: 850  - 1850
chunk 2: 1700 - 2700
```

## 新增文件

```text
document/chunk/TextChunk.java
document/chunk/TextChunker.java
document/entity/DocumentChunk.java
document/repository/DocumentChunkRepository.java
```

## 修改文件

```text
document/dto/DocumentParseResponse.java
document/service/DocumentParseService.java
document/controller/DocumentControllerTests.java
document/service/DocumentParseServiceTests.java
```

## 核心流程

```text
POST /api/documents/{documentId}/parse
        ↓
DocumentParseService.parse
        ↓
DocumentParser 解析原始文件
        ↓
TextChunker.chunk 进行文本切分
        ↓
删除该文档旧 chunk
        ↓
保存新 chunk 到 document_chunks
        ↓
文档状态变为 COMPLETED
```

## TextChunker 做了什么

`TextChunker` 负责把一段字符串切成：

```java
public record TextChunk(
        int index,
        String content,
        int tokenCount) {
}
```

其中：

- `index`：第几个 chunk，从 0 开始。
- `content`：当前 chunk 的文本内容。
- `tokenCount`：第一版估算 token 数，后续可以替换成真实 tokenizer。

## DocumentChunk 对应哪张表

`DocumentChunk` 对应数据库表：

```text
document_chunks
```

关键字段：

```text
document_id        属于哪个文档
knowledge_base_id  属于哪个知识库
chunk_index        当前文档里的第几个 chunk
content            chunk 文本内容
token_count        估算 token 数
page_number        页码，当前第一版先为空
embedding          向量，后续 Day 16 再写入
```

## 为什么解析前要删除旧 chunk

同一个文档可能被重复解析。

如果不删除旧 chunk，再插入新的 chunk，会触发数据库唯一约束：

```text
UNIQUE (document_id, chunk_index)
```

所以当前策略是：

```text
先 deleteByDocumentId
再 saveAll 新 chunk
```

## 当前限制

- chunk size 和 overlap 先写成固定值。
- token_count 是估算值，不是真实模型 tokenizer 结果。
- PDF 暂时不记录 page_number。
- embedding 字段暂时为空，后续向量化阶段再写入。

## 测试验证

新增或更新测试覆盖：

- 长文本可以切成多个 chunk。
- chunk_index 从 0 开始递增。
- chunk 之间有 overlap。
- 优先在句号等位置切分。
- 解析成功后会删除旧 chunk 并保存新 chunk。
- 接口返回 `chunkCount`。

## 今日重点

今天最重要的是理解：

```text
解析是把文件变成全文。
切分是把全文变成可检索的小片段。
向量化是把小片段变成可相似度计算的 embedding。
```

Day 14 完成后，RAG 的数据准备流程已经走到：

```text
上传文件 -> 解析文本 -> 切分 chunk
```
