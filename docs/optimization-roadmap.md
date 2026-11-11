# 智能知识库 RAG 平台后续优化路线图

本文档用于记录当前 MVP 跑通之后的后续优化方向。项目当前已经完成基础链路：

```text
上传文档 -> 解析文本 -> 文本切块 -> Qwen Embedding -> pgvector 检索
-> Prompt 拼接 -> Qwen Plus 回答 -> 前端展示答案与引用
```

后续优化可以按“产品体验、RAG 效果、工程质量”三条线推进。

## 1. Prompt 模式配置化

### 目标

将当前写死在后端 `PromptBuilder.java` 里的回答规则，改成可配置、可切换的回答模式。

### 可支持模式

| 模式 | 说明 | 适合场景 |
| --- | --- | --- |
| 严格知识库模式 | 只能根据知识库资料回答，资料不足就拒答 | 企业知识库、制度问答 |
| 知识库优先模式 | 优先使用知识库，资料不足时允许使用通用知识补充 | 学习助手、个人知识库 |
| 通用问答模式 | 不强制依赖知识库，可作为普通 AI 助手使用 | 闲聊、通用问题 |
| 学习讲解模式 | 用通俗语言解释，再补充知识库依据 | 学习、复习、课程笔记 |

### 技术改造点

- 后端增加 `PromptMode` 枚举。
- 请求 DTO 增加 `promptMode` 字段。
- `PromptBuilder` 根据不同模式生成不同 Prompt。
- 前端聊天页增加模式选择控件。
- 测试覆盖不同 Prompt 模式。

### 当前状态

基础实现已完成：后端支持四种 Prompt 模式，前端聊天页支持发送问题时选择回答模式。

### 简历亮点

```text
支持多种 RAG Prompt 策略，可在严格知识库问答、知识库优先问答和学习讲解模式之间切换。
```

## 2. 引用体验优化

### 目标

让用户更清楚地知道答案来自哪些文档、哪些片段，提升可信度和可解释性。

### 可优化点

- 引用显示文件名，而不是只显示文档 ID。
- 引用显示页码。
- 引用显示相似度。
- 点击引用后，在右侧定位对应 chunk。
- 高亮引用原文中的关键句。
- 支持“展开/收起引用内容”。

### 技术改造点

- `document_chunks` 表增加 `page_number` 或 `metadata` 字段。
- PDF 解析时记录 chunk 所属页码。
- `RetrievedChunkResponse` 增加 `fileName`、`pageNumber` 等字段。
- 前端引用面板优化展示结构。

### 简历亮点

```text
实现答案引用溯源，支持文档片段预览、相似度展示和引用定位。
```

## 3. 检索质量优化

### 目标

提升 RAG 回答的准确性，让系统更容易找到真正相关的资料片段。

### 可优化点

- 增加相似度阈值，过滤明显无关的 chunk。
- 支持 TopK 参数调节。
- 增加关键词检索。
- 实现向量检索 + 关键词检索的混合检索。
- 增加 rerank 重排序。
- 支持按知识库、文档、时间范围过滤。

### 技术改造点

- `RagRetrievalRepository` 增加相似度阈值过滤。
- PostgreSQL 增加全文检索索引。
- 后端增加 Hybrid Search 服务。
- 可选接入 rerank 模型。

### 当前状态

基础实现已完成：支持按请求调节 TopK 和最低相关度；后端使用向量检索与关键词检索生成候选集，
再通过 RRF（倒数排名融合）合并排序。PostgreSQL 增加全文索引和 `pg_trgm` 子串索引；目前已在
应用侧接入 jieba 中文分词，把 chunk 分词结果写入 `search_text`，并用
`GIN(to_tsvector('simple', search_text))` 建立中文全文索引。

当前版本支持按当前知识库内的文档多选，以及按文档上传日期范围筛选。已接入 DashScope
Cross-Encoder rerank（`qwen3-rerank`），默认 `RERANK_PROVIDER=none`，不改变现有行为；
设置 `RERANK_PROVIDER=dashscope` 后在 RRF 候选池上精排。

已接入应用侧中文分词：`DocumentParseService` 入库时写入 `search_text`，关键词 SQL 使用
`to_tsvector('simple', search_text) @@ plainto_tsquery('simple', ?)`，中文关键词检索真正走 GIN 索引。
46 条评测集（rerank 关闭）上 Hit@5=0.9130、Recall@5=0.9130、nDCG@10=0.8134、MRR=0.7870；
多片段类 MRR 从 0.6333 提升到 0.8667，同义改写类 MRR 从 0.6813 提升到 0.7292。

已建立离线检索评测体系：评测集包含 46 条标注问答，覆盖原文措辞、同义改写、跨语言、多 chunk、
难负例和元数据过滤六类问题，直接调用 `RagRetrievalService.search` 计算 HitRate@K、Recall@K、
Precision@K、nDCG@K、MRR@K 和平均检索耗时，并输出带分类/难度分层的 JSON / Markdown 报告。
当前 baseline 为 Hit@5=0.9130、Recall@5=0.9130、MRR=0.7623、nDCG@10=0.7994，平均检索耗时 754ms。
其中原文类 Hit@5=0.9444、同义改写类 0.8750、跨语言类 0.8333、难负例类 0.8333，多 chunk 类虽然全部命中
但 MRR 仅 0.6333；英文 FDA 资料的中文问题仍有明显漏召回。阈值消融显示 0.35 与 0.20 的指标完全一致，
当前瓶颈在排序与跨语言召回，不在相似度阈值。开启 rerank 后 Hit@5=0.9565、Recall@5=0.9348、
MRR=0.8207、nDCG@10=0.8326，同义改写和难负例提升明显；2 条 hard 漏召回被救回，另外 2 条因未进入
候选池仍需 Query 改写。详见 `docs/retrieval-evaluation.md`、`docs/retrieval-eval-baseline.md`、
`docs/retrieval-eval-rerank.md`、`docs/retrieval-eval-keyword.md` 与 `docs/chinese-keyword-search.md`。

### 简历亮点

```text
接入 Cross-Encoder 重排序（DashScope qwen3-rerank），在 RRF 召回候选上精排，Hit@5 从 0.9130 提升到 0.9565、MRR 从 0.7623 提升到 0.8207。
接入 jieba 中文分词与 search_text GIN 索引，让中文关键词检索真正走全文索引，MRR 从 0.7623 提升到 0.7870。
构建 RAG 离线评测体系，以 Recall@K / nDCG@K / MRR 量化检索效果，为 Rerank、中文分词和融合参数调优提供基线。
基于 pgvector 实现语义检索，并扩展相似度阈值、TopK 控制和混合检索策略。
```

## 4. 文档解析能力增强

### 目标

让知识库支持更多格式和更高质量的文档解析。

### 可优化点

- 支持 Word 文档：`.docx`
- 支持 Excel：`.xlsx`
- 支持网页 URL 导入。
- 支持 Markdown 标题结构识别。
- 支持 PDF 页码记录。
- 支持表格解析。
- 支持图片 OCR。

### 技术改造点

- 引入 Apache POI 解析 Word/Excel。
- 设计统一的 `DocumentParser` 接口扩展。
- 增加文档元数据字段。
- 对 Markdown 按标题层级切块。
- 对 PDF 按页提取文本。

### 当前进展

当前已支持 TXT、PDF、Markdown、Word（`.docx`）和 Excel（`.xlsx`）上传与解析：

- PDF 按物理页提取文本并记录展示页码。
- Markdown 按标题层级分段，避免切块跨越不同主题。
- Word 段落与表格会转换为可检索文本。
- Excel 会按工作表、表头和数据行转换为带字段名的可检索文本。

网页 URL 导入与图片 OCR 仍待后续选定抓取安全策略和 OCR 引擎后实现。

### 简历亮点

```text
设计可扩展文档解析体系，支持 PDF、Markdown、纯文本、Word、Excel 等多格式文档接入，并保留标题、页码和表格语义。
```

## 5. 流式回答

### 目标

让 AI 回答像 ChatGPT 一样逐步输出，而不是等待完整答案生成后一次性返回。

### 可优化点

- 前端展示打字机式回答。
- 后端使用 SSE 推送模型输出。
- 支持回答中断。
- 支持加载状态和错误恢复。

### 技术改造点

- 后端新增 SSE 接口。
- Qwen 调用开启 stream 模式。
- 前端用 `EventSource` 或 Fetch Stream 接收输出。
- 聊天消息保存从“一次性保存”改为“生成完成后保存”。

### 当前进展

当前已实现完整的流式问答链路：

- 后端新增 `POST /api/chat/sessions/{sessionId}/messages/stream`，以 SSE 依次推送用户消息、回答分片、完成结果或错误。
- Qwen 和 OpenAI 均以 `stream: true` 调用模型；Mock 模型也会模拟分片输出，便于本地验证。
- 前端使用 Fetch Stream（便于携带 POST 请求体和登录令牌）实时拼接回答，并显示闪烁光标、加载状态和失败提示。
- 生成完成后才保存助手回答及引用；用户点击“停止生成”或连接中断时，不会保存未完成的助手回答。

### 简历亮点

```text
实现基于 SSE 的大模型流式问答，优化 RAG 系统交互体验。
```

## 6. 对话体验优化

### 目标

让智能问答从“单轮问答”升级为更完整的对话系统。

### 可优化点

- 删除会话。
- 重命名会话。
- 清空聊天记录。
- 追问时带历史上下文。
- 根据知识库自动生成推荐问题。
- 会话按更新时间排序。

### 技术改造点

- Chat Session 增加更新接口。
- Chat Session 增加删除接口。
- PromptBuilder 支持拼接历史消息。
- 前端增加会话管理按钮。

### 当前进展

已实现会话重命名、删除、清空记录，所有操作均校验当前用户的会话所有权。删除和清空记录会一并移除消息引用。
最近三轮完整问答会作为上下文传给模型；短追问中的指代词也会结合上一轮问题用于检索。推荐问题根据已解析文档标题生成，暂无文档时使用知识库名称生成。会话列表按 `updated_at` 倒序展示，完成回答、重命名或清空记录后更新时间会刷新。

### 简历亮点

```text
实现多轮对话管理，支持历史上下文追问、会话持久化和会话管理。
```

## 7. 权限和用户体系增强

### 目标

让系统更接近真实企业级知识库产品。

### 可优化点

- 管理员角色。
- 知识库公开/私有。
- 知识库成员邀请。
- 知识库共享。
- 操作审计日志。
- 用户禁用与权限控制。

### 技术改造点

- 增加 `knowledge_base_members` 表。
- 增加权限校验服务。
- Controller 层增加权限控制。
- 增加操作日志表。

### 当前进展

- 已实现管理员接口：用户列表/搜索、启用/禁用、角色变更；`/api/admin/**` 仅 `ADMIN` 可访问，
  401/403 返回统一 JSON。
- `JwtAuthenticationFilter` 每次请求从数据库重新加载用户状态和角色，禁用/降级立即生效。
- 已实现知识库成员共享：`OWNER` / `EDITOR` / `VIEWER`，OWNER 隐式来自
  `knowledge_bases.user_id`，成员 CRUD 由 OWNER 管理。
- 已实现操作审计：`operation_logs` 记录用户、知识库、成员和文档的关键操作，
  管理员可按用户/动作/资源/时间查询。
- 已实现首个管理员 bootstrap：`ADMIN_BOOTSTRAP_USERNAME`。
- 仍待：知识库公开/私有、邀请通知、审计日志保留策略、管理员/成员前端页面。

### 简历亮点

```text
实现管理员用户管理、知识库成员共享和操作审计；基于 OWNER/EDITOR/VIEWER 权限模型与 JWT 实时状态校验，支持禁用和角色变更即时生效。
```

## 8. 工程化优化

### 目标

提升项目的可维护性、可部署性和专业度。

### 可优化点

- GitHub Actions 自动测试。
- Docker 镜像构建。
- Swagger/OpenAPI 接口文档。
- 统一日志 traceId。
- 统一错误码文档。
- 生产环境配置说明。
- 前端构建检查和代码规范。

### 技术改造点

- 增加 `.github/workflows/backend-test.yml`。
- 接入 Springdoc OpenAPI。
- 增加请求日志拦截器。
- 增加生产部署文档。

### 当前进展

- 已提供 `docker-compose.yml`，一键启动 PostgreSQL + pgvector、Redis、backend 和 frontend；前后端均有 Dockerfile。
- 后端已有单元测试、Controller 测试和 Spring 上下文测试，完整 `mvn test` 可通过；前端 `npm run build` 包含 `tsc -b`，`npm run lint` 使用 oxlint。
- 已实现统一 `ApiResponse`、`ErrorCode`、`GlobalExceptionHandler` 和 `RequestLogAspect`（记录方法、URI、用户和耗时）。
- 已接入 Actuator health/info，Docker healthcheck 使用 `/actuator/health`。
- 已使用 Redis 缓存知识库/文档列表与详情，并实现聊天限流。
- 尚未接入 GitHub Actions、Springdoc OpenAPI、traceId/MDC、统一错误码文档和生产环境部署说明。

### 简历亮点

```text
完成 Docker Compose 一键部署、后端测试体系、统一异常/请求日志、Redis 缓存与聊天限流；后续补 CI、OpenAPI 与 traceId。
```

## 推荐推进顺序

建议后续按以下顺序推进：

1. Prompt 模式配置化
2. 引用体验优化
3. 检索质量优化
4. 对话体验优化
5. 流式回答
6. 文档解析能力增强
7. 权限和用户体系增强
8. 工程化优化

其中前 4 项最适合近期继续做，因为它们和当前 MVP 关联最紧密，改完后能明显提升产品完成度。

## 下一步建议

优先进入“Prompt 模式配置化”。这一项基于当前已经完成的宽松 Prompt 改造，改动范围适中，学习价值也高。
