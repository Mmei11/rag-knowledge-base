# Qwen 接入说明

本项目支持通过阿里云百炼 / DashScope 的 OpenAI 兼容接口接入 Qwen。

推荐模式：

```text
EMBEDDING_PROVIDER=qwen
CHAT_MODEL_PROVIDER=qwen
```

这样文档向量化和问答生成都使用真实模型，不再依赖 mock embedding。

## 配置方式

复制 `.env.example` 为 `.env` 后，按需修改：

```text
EMBEDDING_PROVIDER=qwen
CHAT_MODEL_PROVIDER=qwen
QWEN_API_KEY=你的百炼 API Key
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_CHAT_MODEL=qwen-plus
QWEN_EMBEDDING_MODEL=qwen3.7-text-embedding-flash
QWEN_EMBEDDING_BATCH_SIZE=20
QWEN_EMBEDDING_CONCURRENCY=3
EMBEDDING_DIMENSION=1024
```

说明：

```text
QWEN_API_KEY 只放本地 .env，不要提交到 GitHub。
QWEN_BASE_URL 必须和 API Key 所属地域匹配。
北京地域可先用 https://dashscope.aliyuncs.com/compatible-mode/v1。
生产环境建议使用百炼业务空间专属域名。
```

## 后端实现位置

```text
backend/src/main/java/com/example/ragkb/rag/config/QwenProperties.java
backend/src/main/java/com/example/ragkb/rag/model/QwenEmbeddingService.java
backend/src/main/java/com/example/ragkb/rag/model/QwenChatModelService.java
```

`QwenProperties` 负责读取 `app.qwen.*` 配置。

`QwenEmbeddingService` 在 `EMBEDDING_PROVIDER=qwen` 时生效，调用 `/embeddings` 接口生成文本向量。

`QWEN_EMBEDDING_BATCH_SIZE` 控制单个向量请求包含的文本片段数；
`QWEN_EMBEDDING_CONCURRENCY` 限制同时发出的向量请求数。对
`qwen3.7-text-embedding-flash`，推荐保持 `20` 和 `3`，在吞吐与限流之间取得平衡。
系统使用滑动窗口调度：每完成一个请求批次才提交下一个，因此超大文档也只会保留
最多 `QWEN_EMBEDDING_CONCURRENCY` 个向量任务，不会撑满任务队列。

`QwenChatModelService` 在 `CHAT_MODEL_PROVIDER=qwen` 时生效，调用 `/chat/completions` 接口生成回答。

## 向量维度

本项目当前数据库字段为：

```sql
embedding VECTOR(1024)
```

因此运行 Qwen embedding 时应保持：

```text
EMBEDDING_DIMENSION=1024
```

切换 embedding 模型时，即使维度相同，也必须重新解析已有文档以生成同一语义空间的向量；
不要将不同模型生成的文档向量混用于同一个知识库检索。

如果你的本地 PostgreSQL 容器是在旧版本 schema 下创建的，里面可能还是 `vector(384)`。切换 Qwen 前需要重建本地数据卷：

```powershell
docker compose down -v
docker compose up -d --build
```

注意：`docker compose down -v` 会删除本地 PostgreSQL、Redis 和上传文件数据。

## 启动后验证

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/actuator/health
```

然后重新注册/登录，创建知识库，上传文档，等文档状态变成 `COMPLETED` 后进入智能问答测试。

## Cross-Encoder Rerank

检索链路在 RRF 融合后可选接入 DashScope 的文本重排序接口，对候选 chunk 做精排。
默认关闭，不影响现有行为。

配置：

```text
RERANK_PROVIDER=dashscope
RERANK_ENDPOINT=https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
RERANK_MODEL=qwen3-rerank
# 留空时复用 QWEN_API_KEY
RERANK_API_KEY=
RERANK_CANDIDATE_LIMIT=30
RERANK_TIMEOUT_MS=3000
RERANK_MAX_DOCUMENT_CHARS=2000
```

已验证可用模型：`qwen3-rerank`、`gte-rerank-v2`、`qwen3-vl-rerank`。
`qwen3-rerank` 是纯文本重排，默认使用它。

实现位置：

```text
backend/src/main/java/com/example/ragkb/rag/config/RerankProperties.java
backend/src/main/java/com/example/ragkb/rag/model/RerankService.java
backend/src/main/java/com/example/ragkb/rag/model/NoopRerankService.java
backend/src/main/java/com/example/ragkb/rag/model/MockRerankService.java
backend/src/main/java/com/example/ragkb/rag/model/DashScopeRerankService.java
```

说明：

```text
RERANK_PROVIDER=none（默认）：直接使用 RRF 排序。
RERANK_PROVIDER=mock：本地词面重叠重排，仅用于链路测试。
RERANK_PROVIDER=dashscope：调用真实 rerank API。
超时、HTTP 错误或响应解析失败时自动回退到 RRF 顺序，不阻断问答。
```

接口探测：

```bash
curl -X POST "$RERANK_ENDPOINT" \
  -H "Authorization: Bearer $QWEN_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen3-rerank","input":{"query":"食品安全","documents":["食品应当无毒无害","今天天气很好"]},"parameters":{"top_n":2,"return_documents":false}}'
```
