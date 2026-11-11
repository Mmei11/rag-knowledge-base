# 智能知识库 RAG 平台

一个面向开发岗实习作品集的智能知识库平台。用户可以注册登录、创建知识库、上传文档，系统会解析文档、切分文本、生成向量并存入 PostgreSQL + pgvector，最后基于检索结果完成带引用来源的问答。

## 核心功能

```text
用户注册与登录
JWT 鉴权
知识库 CRUD
文档上传、查询、删除
PDF/TXT/Markdown/Word/Excel 文档解析
文本切分与重叠窗口
异步文档处理
Embedding 抽象与 mock/OpenAI/Qwen 实现
pgvector 向量存储
相似度检索
混合检索（向量 + 关键词 + RRF）
中文分词关键词检索（jieba + PostgreSQL GIN）
Cross-Encoder 重排（DashScope qwen3-rerank，默认关闭）
管理员用户管理（启用/禁用/角色）
知识库成员共享（OWNER/EDITOR/VIEWER）
操作审计日志
RAG Prompt 构建
OpenAI/Qwen API 问答生成
聊天会话、消息、引用记录
Redis 缓存
Redis 聊天限流
请求日志与异常统一处理
Docker Compose 一键启动
```

## 技术栈

后端：

```text
Java 21
Spring Boot 3.5.16
Spring Web   //http接口
Spring Security
Spring Data JPA   //负责操纵数据库表
Spring Data Redis
Spring AOP
Spring Boot Actuator    //负责应用监控和健康检查
PostgreSQL Driver   //数据库驱动
Apache PDFBox
Apache POI
JUnit 5    //单元测试框架
Mockito    //mock工具，模拟依赖对象
MockMvc    //接口测试工具
```

前端：

```text
React
TypeScript
Vite    //前端开发和构建工具
Axios   //前端发送http请求的库
React Router   //前端路由库 login chat knowledge-bases
Nginx
```

基础设施：

```text
PostgreSQL 16 + pgvector
Redis 7
Docker Compose
```

## 项目结构

```text
rag-knowledge-base/
  backend/                 Spring Boot 后端
  frontend/                React 前端
  docker/postgres/init/    PostgreSQL 初始化 SQL
  docs/                    项目文档、每日学习记录、接口测试材料
  docker-compose.yml       多容器编排
  .env.example             环境变量模板
```

## 快速启动

前置要求：

```text
Docker Desktop
```

在项目根目录执行：

```powershell
cd D:\MAIN\rag-knowledge-base
docker compose up -d --build
```


代理地址：

```text
$env:HTTP_PROXY='http://127.0.0.1:12450'
$env:HTTPS_PROXY='http://127.0.0.1:12450'
$env:http_proxy='http://127.0.0.1:12450'
$env:https_proxy='http://127.0.0.1:12450'
```

访问地址：

```text
前端页面：http://localhost:5175
后端健康检查：http://localhost:8080/actuator/health
```

停止服务：

```powershell
docker compose down
```

如果需要清空数据库、Redis 和上传文件 volume，从零启动：

```powershell
docker compose down -v
docker compose up -d --build
```

注意：`docker compose down -v` 会删除本项目的本地数据。

## 本地开发启动

启动 PostgreSQL 和 Redis：

```powershell
cd D:\MAIN\rag-knowledge-base
docker compose up -d postgres redis
```

启动后端：

```powershell
cd D:\MAIN\rag-knowledge-base\backend
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
```

启动前端：

```powershell
cd D:\MAIN\rag-knowledge-base\frontend
npm.cmd run dev
```

## OpenAI 接入

项目默认仍使用 mock 模型，方便本地无成本跑通完整链路：

```text
EMBEDDING_PROVIDER=mock
CHAT_MODEL_PROVIDER=mock
```

如果要调用真实 OpenAI API，在本地 `.env` 中改成：

```text
EMBEDDING_PROVIDER=openai
CHAT_MODEL_PROVIDER=openai
OPENAI_API_KEY=你的 OpenAI API Key
OPENAI_CHAT_MODEL=gpt-5-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSION=1024
```

说明：真实 API Key 只放本地 `.env`，不要提交到 GitHub。详细说明见 `docs/openai-integration.md`。

也可以使用阿里云百炼 / DashScope 的 Qwen：

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

# 可选：开启 Cross-Encoder 重排；默认 none
RERANK_PROVIDER=dashscope
RERANK_MODEL=qwen3-rerank
RERANK_CANDIDATE_LIMIT=30

# 首次启动把已有用户提升为管理员；完成后可清空
ADMIN_BOOTSTRAP_USERNAME=
```

详细说明见 `docs/qwen-integration.md`。

## 测试

后端测试：

```powershell
cd D:\MAIN\rag-knowledge-base\backend
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd test
```

当前结果：

```text
Tests run: 115
Failures: 0
Errors: 0
Skipped: 0
```
  
前端构建：

```powershell
cd D:\MAIN\rag-knowledge-base\frontend
npm.cmd run build
```

## 文档

```text
docs/architecture.md       系统架构说明
docs/rag-flow.md           RAG 流程说明
docs/database.md           数据库设计说明
docs/api-test.md           手动接口测试指南
docs/api-requests.http     可执行 HTTP 请求集合
docs/test-result.md        自动化测试结果
```

## 项目截图

截图目录：

```text
docs/screenshots/
```

建议补充的截图：

```text
登录/注册页面
知识库管理页面
文档上传与状态页面
智能问答页面
Docker Desktop 容器运行状态
DBeaver 数据库表结构
```

## 核心链路

```text
用户上传文档
  -> 后端保存文件和 document 记录
  -> 异步任务解析文档文本
  -> TextChunker 切分文本
  -> EmbeddingService 生成向量
  -> pgvector 保存 chunk embedding
  -> 用户提问
  -> 向量检索 topK 相关 chunk
  -> PromptBuilder 拼接上下文
  -> ChatModelService 生成回答
  -> 保存聊天消息和引用来源
```

## 项目亮点

```text
完整 RAG 主链路：上传、解析、切分、向量化、检索、问答、引用。
工程化结构清晰：controller/service/repository/dto/entity 分层明确。
安全基础完整：JWT 鉴权、用户资源隔离、统一异常响应。
性能意识：Redis 缓存知识库和文档查询结果。
稳定性意识：异步文档处理、限流、请求日志、健康检查。
可交付性：Docker Compose 一键启动，接口测试文档可复现主流程。
测试覆盖：核心 service/controller/repository/cache/rate-limit 均有测试。
```
