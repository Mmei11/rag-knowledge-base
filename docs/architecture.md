# 系统架构

## 总览

本项目采用前后端分离架构，后端负责业务接口、鉴权、文档处理、RAG 检索与问答；前端负责页面交互；PostgreSQL + pgvector 保存业务数据和向量；Redis 负责缓存和限流。

```mermaid
flowchart LR
    Browser[Browser] --> Frontend[React + Nginx]
    Frontend --> Backend[Spring Boot API]
    Backend --> PostgreSQL[(PostgreSQL + pgvector)]
    Backend --> Redis[(Redis)]
    Backend --> Storage[(Document Storage)]
```

## 容器架构

```mermaid
flowchart TB
    Compose[Docker Compose] --> Postgres[postgres]
    Compose --> Redis[redis]
    Compose --> Backend[backend]
    Compose --> Frontend[frontend]

    Backend -->|JDBC| Postgres
    Backend -->|RedisTemplate / CacheManager| Redis
    Backend -->|file path| BackendStorage[backend_storage volume]
    Frontend -->|HTTP API| Backend
```

## 后端分层

```text
controller  接收 HTTP 请求，处理参数校验和认证用户
service     业务逻辑，例如注册、知识库管理、文档处理、聊天
repository  数据库访问，基于 Spring Data JPA 或 JdbcTemplate
entity      数据库表对应的领域对象
dto         接口入参和出参
config      安全、CORS、Redis、异步线程池等配置
common      通用响应、异常、分页、缓存、限流、日志
```

## 后端请求链路

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant C as Controller
    participant S as Service
    participant DB as PostgreSQL
    participant R as Redis

    U->>F: 操作页面
    F->>C: HTTP API + JWT
    C->>S: 当前用户 + 请求 DTO
    S->>R: 读/写缓存或限流计数
    S->>DB: 查询或写入业务数据
    DB-->>S: 返回数据
    S-->>C: 返回 Response DTO
    C-->>F: ApiResponse
    F-->>U: 更新页面
```

## 关键设计

```text
JWT 鉴权：登录后前端保存 token，后续请求带 Authorization: Bearer token。
用户隔离：查询资源时都带 userId 条件，避免用户访问他人数据。
统一响应：所有接口返回 ApiResponse，前端处理逻辑一致。
统一异常：BusinessException 和参数校验异常由 GlobalExceptionHandler 转成标准响应。
缓存策略：知识库列表、详情、文档列表、详情、状态使用 Redis 缓存。
缓存失效：创建、更新、删除、解析文档后主动删除相关缓存。
异步处理：上传文档后立即返回，后台执行解析、切分、向量化。
限流：聊天接口按用户维度限制请求频率。
```

## 部署入口

```text
docker-compose.yml
backend/Dockerfile
frontend/Dockerfile
frontend/nginx.conf
.env.example
```
