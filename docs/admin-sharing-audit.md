# 管理员、知识库共享与操作审计

## 角色模型

系统角色与知识库角色分开：

- 系统角色：`USER` / `ADMIN` / `SUPER_ADMIN`，权限层级为 `SUPER_ADMIN > ADMIN > USER`
- 知识库角色：`OWNER` / `EDITOR` / `VIEWER`
- `OWNER` 不写入成员表，隐式来自 `knowledge_bases.user_id`
- `knowledge_base_members.role` 只允许 `EDITOR` / `VIEWER`

### 权限矩阵

| 操作 | OWNER | EDITOR | VIEWER | 无关用户 |
|---|---|---|---|---|
| 知识库列表/详情 | 是 | 是 | 是 | 否 |
| 修改/删除知识库 | 是 | 否 | 否 | 否 |
| 成员管理 | 是 | 否 | 否 | 否 |
| 文档列表/详情/状态 | 是 | 是 | 是 | 否 |
| 上传/重解析/删除/同步解析文档 | 是 | 是 | 否 | 否 |
| 创建会话 / RAG 检索 | 是 | 是 | 是 | 否 |
| 读写聊天会话 | 仅会话本人 | 仅本人 | 仅本人 | 否 |
| `/api/admin/**` | 仅系统 ADMIN / SUPER_ADMIN | — | — | — |

## 管理员接口

所有接口返回 `ApiResponse` 包装。

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/admin/users?keyword=&status=&role=&page=&size=` | 用户列表/搜索 |
| PATCH | `/api/admin/users/{id}/status` | `{"status":"ENABLED"\|"DISABLED"}` |
| PATCH | `/api/admin/users/{id}/role` | `{"role":"USER"\|"ADMIN"\|"SUPER_ADMIN"}` |
| GET | `/api/admin/operation-logs?userId=&action=&resourceType=&resourceId=&from=&to=&page=&size=` | 审计查询 |

保护规则：

- `SUPER_ADMIN`（主管理员）可查看所有用户，可把 `USER` / `ADMIN` 调整为更低角色
- `ADMIN`（子管理员）只能看到自己和 `USER`，不能修改任何人的角色
- 不能修改自己的角色
- 不能禁用自己的账号
- 只能操作层级严格低于自己的用户，不能操作同级或更高级用户
- 不能把用户提升为 `SUPER_ADMIN`
- 所有变更写审计日志

### 首个管理员

设置环境变量后启动：

```text
ADMIN_BOOTSTRAP_USERNAME=meijinchao
```

`AdminBootstrapRunner` 会把已存在的该用户提升为 `SUPER_ADMIN`；用户不存在只记 warn，不自动创建账号。
首次完成 bootstrap 后可以把该变量清空。

### 立即生效

`JwtAuthenticationFilter` 每次请求都会按 userId 从数据库重新加载用户：

- 用户被禁用或删除 → 不建立认证，接口返回 401
- 用户角色变更 → 使用数据库中的最新角色
- 不再依赖 token 中可能过期的 role 声明

代价是每个认证请求多一次 `users` 主键查询；当前规模可接受。

## 知识库共享

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/knowledge-bases/{id}/members` | 成员列表（OWNER） |
| POST | `/api/knowledge-bases/{id}/members` | `{"username":"bob","role":"VIEWER"}` |
| PUT | `/api/knowledge-bases/{id}/members/{userId}` | `{"role":"EDITOR"}` |
| DELETE | `/api/knowledge-bases/{id}/members/{userId}` | 移除成员 |

规则：

- 只有 OWNER 能管理成员
- 不能把 OWNER 加为成员
- 不能添加自己
- 目标用户必须存在且为 `ENABLED`
- 重复添加返回 `RESOURCE_CONFLICT`
- 成员角色只允许 `EDITOR` / `VIEWER`

共享后：

- `GET /api/knowledge-bases` 会同时返回 owned + shared，并带 `role` 字段
- 文档列表按知识库查询，不再按上传者过滤
- 聊天会话仍然只属于创建者本人
- RAG 检索只要对知识库可读即可使用

## 操作审计

`operation_logs` 记录：

- `USER_REGISTER` / `USER_LOGIN`
- `USER_ENABLE` / `USER_DISABLE` / `USER_ROLE_CHANGE`
- `KB_CREATE` / `KB_UPDATE` / `KB_DELETE`
- `KB_MEMBER_ADD` / `KB_MEMBER_ROLE_CHANGE` / `KB_MEMBER_REMOVE`
- `DOCUMENT_UPLOAD` / `DOCUMENT_DELETE` / `DOCUMENT_REPROCESS`

字段：

```text
user_id
action
resource_type
resource_id
ip
created_at
```

IP 优先取 `X-Forwarded-For` 第一段，否则取 `getRemoteAddr()`；`ip` 字段为 `VARCHAR(45)`。

审计写入与业务在同一个事务中，业务失败时审计一起回滚；管理员通过
`GET /api/admin/operation-logs` 查询，返回结果带 `username`。

## 数据库 migration

已有数据库执行：

```bash
docker exec -i rag-kb-postgres psql -U rag_user -d rag_kb \
  < docker/postgres/migrations/20261109_admin_sharing_audit.sql
```

migration 内容：

- `operation_logs.ip` 从 `INET` 改为 `VARCHAR(45)`
- 新增 `knowledge_base_members` 表
- 新增成员与审计查询索引

新库的 `docker/postgres/init/02-schema.sql` 已同步。

## 缓存

共享后，知识库/文档缓存不再是单用户视角。为避免成员看到旧列表或旧详情：

- 知识库更新/删除、成员变更 → 清空 KB 列表/详情缓存
- 文档上传/删除/重解析 → 清空文档列表/详情/状态缓存

这是有意用缓存命中率换一致性；数据量增大后可以改成按 audience 精确失效。

## 前端

本次只提供 API，不包含管理员后台和成员管理页面。知识库列表新增 `role` 字段，
现有前端可以忽略，不影响兼容性。
