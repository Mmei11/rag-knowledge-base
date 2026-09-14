# Day 3 Docker Compose 基础服务记录

日期：2026-07-08

## 今日目标

用 Docker Compose 启动 PostgreSQL、pgvector 和 Redis，并让后端能够连接这些基础服务。

## 已完成

### 1. Docker Compose

新增文件：

```text
docker-compose.yml
```

服务：

| 服务 | 镜像 | 端口 | 说明 |
| --- | --- | --- | --- |
| postgres | pgvector/pgvector:pg16 | 5432 | PostgreSQL 16 + pgvector |
| redis | redis:7-alpine | 6379 | Redis 缓存服务 |

数据库配置：

```text
database: rag_kb
user: rag_user
password: rag_password
```

### 2. pgvector 初始化

新增初始化脚本：

```text
docker/postgres/init/01-create-vector-extension.sql
```

内容：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

该脚本会在 PostgreSQL 数据卷首次初始化时自动执行。

### 3. 后端配置

将后端配置从：

```text
backend/src/main/resources/application.properties
```

替换为：

```text
backend/src/main/resources/application.yml
```

已配置：

- Spring 应用名
- PostgreSQL 数据源
- JPA 基础配置
- Redis 连接
- server port 8080
- actuator health/info

### 4. 后端依赖

在 `backend/pom.xml` 中新增 Redis 依赖：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

## 验证结果

### 1. 后端编译

命令：

```powershell
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd -DskipTests package
```

结果：

```text
BUILD SUCCESS
```

### 2. 启动容器

命令：

```powershell
docker compose up -d
```

结果：

```text
rag-kb-postgres Up (healthy)
rag-kb-redis    Up (healthy)
```

说明：当前普通 Codex 终端访问 Docker API 仍可能出现权限问题，实际执行时使用了提权方式访问 Docker 引擎。

### 3. 验证 pgvector

命令：

```powershell
docker exec rag-kb-postgres psql -U rag_user -d rag_kb -c "select extname from pg_extension where extname = 'vector';"
```

结果：

```text
extname
-------
vector
```

### 4. 验证 Redis

命令：

```powershell
docker exec rag-kb-redis redis-cli ping
```

结果：

```text
PONG
```

### 5. 验证后端连接数据库和 Redis

短暂启动后端 jar：

```powershell
java -jar target\ragkb-0.0.1-SNAPSHOT.jar
```

访问：

```text
http://127.0.0.1:8080/actuator/health
```

结果：

```text
status: UP
db: UP
redis: UP
```

验证完成后已停止临时后端进程，8080 端口未被占用。

## 当前基础服务状态

截至本记录创建时：

```text
rag-kb-postgres healthy
rag-kb-redis healthy
```

## 常用命令

启动服务：

```powershell
docker compose up -d
```

查看状态：

```powershell
docker compose ps
```

查看日志：

```powershell
docker compose logs postgres
docker compose logs redis
```

停止服务：

```powershell
docker compose down
```

停止并删除数据卷：

```powershell
docker compose down -v
```

注意：`down -v` 会删除数据库和 Redis 数据，只有需要重置环境时再执行。

## 下一步

进入 Day 4：

- 设计数据库建表 SQL
- 创建 users、knowledge_bases、documents、document_chunks 等核心表
- 创建普通索引
- 创建向量索引预留方案
