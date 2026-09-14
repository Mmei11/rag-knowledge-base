# Day 17：pgvector 向量入库

## 今日目标

把 Day 16 生成的 Embedding 写入 PostgreSQL 的 pgvector 字段。

Day 16 完成的是：

```text
chunk text -> EmbeddingService -> EmbeddingVector
```

Day 17 完成的是：

```text
EmbeddingVector -> document_chunks.embedding
```

## pgvector 字段

`document_chunks` 表中的字段：

```sql
embedding VECTOR(384)
```

当前项目配置：

```yaml
app:
  embedding:
    provider: mock
    dimension: 384
```

也就是说：

```text
代码生成 384 维向量
数据库保存 384 维向量
```

这两个维度必须一致。

如果你本地数据库之前已经建过表，确认字段类型：

```sql
\d document_chunks
```

或者在 DBeaver 里查看 `document_chunks.embedding`。

如果旧字段是无维度的 `vector`，后面可以按需要调整为：

```sql
ALTER TABLE document_chunks
    ALTER COLUMN embedding TYPE vector(384);
```

## 新增文件

```text
rag/model/PgVectorFormatter.java
document/repository/DocumentChunkEmbeddingRepository.java
rag/model/PgVectorFormatterTests.java
document/repository/DocumentChunkEmbeddingRepositoryTests.java
```

## 修改文件

```text
document/service/DocumentParseService.java
docs/database-schema.sql
document/service/DocumentParseServiceTests.java
```

## 核心流程

现在文档处理完整流程是：

```text
上传文档
  ↓
后台任务触发
  ↓
解析文档全文
  ↓
TextChunker 切分 chunks
  ↓
删除旧 chunks
  ↓
保存新 chunks
  ↓
EmbeddingService 为每个 chunk 生成向量
  ↓
DocumentChunkEmbeddingRepository 批量写入 embedding
  ↓
文档状态改成 COMPLETED
```

## PgVectorFormatter

pgvector 接收的文本格式类似：

```text
[0.10000000,-0.25000000,1.00000000]
```

`PgVectorFormatter` 负责把：

```java
EmbeddingVector(List.of(0.1, -0.25, 1.0))
```

转换成：

```text
[0.10000000,-0.25000000,1.00000000]
```

同时会拒绝非法值：

```text
null
NaN
Infinity
```

## DocumentChunkEmbeddingRepository

这个类专门负责写入 pgvector 字段。

核心 SQL：

```sql
UPDATE document_chunks
SET embedding = CAST(? AS vector)
WHERE id = ?
```

为什么这里用 `JdbcTemplate`，而不是直接把 `embedding` 放进 JPA 实体？

因为 pgvector 是 PostgreSQL 扩展类型，不是普通 Java 类型。用 JDBC 显式写：

```sql
CAST(? AS vector)
```

更直接，也方便我们控制向量格式。

## 维度校验

写入前会检查：

```text
embedding.dimension() == app.embedding.dimension
```

如果不一致，直接抛异常。

这样可以避免把错误维度的数据写进数据库。

## DocumentParseService 的变化

Day 17 后，保存 chunk 的逻辑变成：

```text
TextChunk -> DocumentChunk
saveAllAndFlush
embedBatch
updateEmbeddings
```

其中 `saveAllAndFlush` 很重要。

因为我们要先让数据库生成 chunk 的 `id`，后面才能执行：

```sql
UPDATE document_chunks SET embedding = ... WHERE id = ?
```

## 为什么还是先删除旧 chunks

重复解析时，旧 chunk 和旧 embedding 都应该作废。

当前策略：

```text
deleteByDocumentId
保存新 chunk
写入新 embedding
```

所以 `document_chunks` 里只保留当前文档最新的一版切片和向量。

## 测试验证

新增或更新测试覆盖：

- pgvector 字符串格式正确。
- 非法向量值会被拒绝。
- 批量写入时会设置 vector 字符串和 chunk id。
- 向量维度不一致会被拒绝。
- 文档解析成功后会调用 `EmbeddingService.embedBatch`。
- 文档解析成功后会调用 `DocumentChunkEmbeddingRepository.updateEmbeddings`。

测试结果：

```text
mvn test
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0
```

## 今日重点

Day 17 最重要的是理解：

```text
chunk 文本用于生成 embedding。
embedding 是 pgvector 能检索的核心数据。
写入前必须保证向量维度一致。
```

到目前为止，RAG 数据处理链路已经走到：

```text
上传文件 -> 解析文本 -> 切分 chunk -> 生成 embedding -> 写入 pgvector
```

下一步 Day 18 会基于 pgvector 做 TopK 相似度检索。
