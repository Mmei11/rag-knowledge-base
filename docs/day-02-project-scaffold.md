# Day 2 项目骨架创建记录

日期：2026-07-08

## 今日目标

初始化后端、前端和基础配置。

## 已完成

### 后端

使用 Spring Initializr 创建 Spring Boot 3 后端项目。

后端路径：

```text
rag-knowledge-base/backend
```

使用版本：

```text
Spring Boot 3.5.16
Java 21
Maven
```

已选择依赖：

- Spring Web
- Spring Validation
- Spring Security
- Spring Data JPA
- PostgreSQL Driver
- Lombok
- Spring Boot Actuator

注意：Spring Initializr metadata 返回的版本 ID 是 `3.5.16.RELEASE`，但 Maven Central 中实际可解析的版本是 `3.5.16`，因此已将 `backend/pom.xml` 中的父版本修正为 `3.5.16`。

### 前端

使用 Vite 创建 React + TypeScript 项目。

前端路径：

```text
rag-knowledge-base/frontend
```

已安装 npm 依赖。

## 验证结果

### 后端编译

执行命令：

```powershell
cd backend
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd -DskipTests package
```

结果：

```text
BUILD SUCCESS
```

说明：由于 Day 3 才会配置 PostgreSQL 和 Docker Compose，目前不直接启动后端应用，只验证编译和打包。

### 前端构建

执行命令：

```powershell
cd frontend
npm.cmd run build
```

结果：

```text
vite build success
```

说明：第一次在受控终端中执行 Vite 构建时出现 `spawn EPERM`，提权重跑后构建通过。

## 当前项目结构

```text
rag-knowledge-base/
  backend/
    pom.xml
    src/main/java/com/example/ragkb/RagkbApplication.java
    src/main/resources/application.properties
  frontend/
    package.json
    src/
    vite.config.ts
  docs/
    day-01-environment-check.md
    day-02-project-scaffold.md
  README.md
```

## 下一步

进入 Day 3：

- 编写 `docker-compose.yml`
- 启动 PostgreSQL + pgvector
- 启动 Redis
- 配置后端连接数据库和 Redis
- 验证 `create extension vector;`
