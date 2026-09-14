# OpenAI 接入说明

本项目的 RAG 主链路现在支持两种模型模式：

```text
mock    本地模拟模型，不需要 API Key，适合开发和测试
openai  调用真实 OpenAI API，适合演示更真实的问答效果
```

## 配置方式

复制 `.env.example` 为 `.env` 后，按需修改：

```text
EMBEDDING_PROVIDER=openai
CHAT_MODEL_PROVIDER=openai
OPENAI_API_KEY=你的 OpenAI API Key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_CHAT_MODEL=gpt-5-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSION=1024
```

如果继续使用 mock，保持：

```text
EMBEDDING_PROVIDER=mock
CHAT_MODEL_PROVIDER=mock
```

## 后端实现位置

```text
backend/src/main/java/com/example/ragkb/rag/config/OpenAiProperties.java
backend/src/main/java/com/example/ragkb/rag/model/OpenAiEmbeddingService.java
backend/src/main/java/com/example/ragkb/rag/model/OpenAiChatModelService.java
```

`OpenAiProperties` 负责读取 `app.openai.*` 配置。

`OpenAiEmbeddingService` 在 `EMBEDDING_PROVIDER=openai` 时生效，调用 `/embeddings` 接口，把文本转换成向量。

`OpenAiChatModelService` 在 `CHAT_MODEL_PROVIDER=openai` 时生效，调用 `/responses` 接口，让模型基于 Prompt 生成回答。

## 为什么保留 mock

真实 OpenAI API 需要网络、Key 和调用费用。保留 mock 有三个好处：

```text
开发时不用依赖外部服务
自动化测试稳定
面试演示时可以快速切换真实模型
```

## 使用 Docker Compose 启动

配置 `.env` 后重新构建启动：

```powershell
cd D:\MAIN\rag-knowledge-base
docker compose up -d --build
```

检查后端健康状态：

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/actuator/health
```

## 注意事项

```text
不要把真实 OPENAI_API_KEY 提交到 GitHub
OpenAI embedding 维度必须和数据库 vector 维度一致
本项目当前默认维度是 1024
如果改成其他维度，需要同步调整数据库 schema 中的 vector 维度
```
