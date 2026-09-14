# Day 18：向量检索接口

## 今日目标

实现基于问题的 TopK 向量检索。

前面 Day 17 已经把 chunk embedding 写入：

```text
document_chunks.embedding
```

Day 18 要做的是：

```text
用户问题 -> 问题 embedding -> pgvector 相似度查询 -> TopK chunks
```

## 新增接口

```text
POST /api/search/vector
```

请求示例：

```json
{
  "knowledgeBaseId": 10,
  "question": "Spring Boot 自动配置是什么？",
  "topK": 5
}
```

返回示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "knowledgeBaseId": 10,
    "question": "Spring Boot 自动配置是什么？",
    "topK": 5,
    "items": [
      {
        "chunkId": 200,
        "documentId": 100,
        "chunkIndex": 0,
        "content": "Spring Boot 自动配置...",
        "similarity": 0.87
      }
    ]
  }
}
```

## 新增文件

```text
rag/dto/VectorSearchRequest.java
rag/dto/RetrievedChunkResponse.java
rag/dto/VectorSearchResponse.java
rag/repository/RagRetrievalRepository.java
rag/service/RagRetrievalService.java
rag/controller/RagSearchController.java
```

## 核心 SQL

```sql
SELECT id, document_id, chunk_index, content,
       1 - (embedding <=> CAST(? AS vector)) AS similarity
FROM document_chunks
WHERE knowledge_base_id = ?
  AND embedding IS NOT NULL
ORDER BY embedding <=> CAST(? AS vector)
LIMIT ?
```

`<=>` 是 pgvector 的 cosine distance 操作符。

距离越小，越相似。

所以排序用：

```sql
ORDER BY embedding <=> query_vector
```

返回相似度时用：

```sql
1 - distance
```

## 权限边界

检索前先校验：

```text
knowledge_base_id 是否属于当前用户
```

代码通过：

```java
knowledgeBaseRepository.findByIdAndUserId(...)
```

这样可以避免用户检索到别人的知识库。

## TopK

`topK` 表示最多返回多少条相似 chunk。

当前规则：

```text
默认 5
最小 1
最大 20
```

## 今日重点

Day 18 最重要的是理解：

```text
文档 chunk 有 chunk embedding。
用户问题也要生成 query embedding。
pgvector 用两个向量计算相似度。
TopK 返回最相关的几个 chunk。
```

到目前为止，RAG 检索链路已经走到：

```text
问题 -> 问题向量 -> 向量相似度检索 -> 相关 chunks
```

下一步 Day 19 会把这些 chunks 组装进 Prompt，并生成回答。
