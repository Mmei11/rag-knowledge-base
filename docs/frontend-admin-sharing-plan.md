# 前端改造交接：管理员、知识库共享与操作审计

## 背景

后端已经完成管理员用户管理、知识库成员共享和操作审计，接口经过完整测试和端到端验证。
前端尚未接入这些能力，目前只有登录、知识库、文档面板和聊天页面。

本文件用于在另一个对话中继续完成前端改造，尽量不依赖之前的对话上下文。

## 当前状态

- 后端所有改动**尚未提交**，仍在当前工作区；开始前先执行 `git status`，不要丢失已有改动。
- 数据库 migration 已应用：
  - `knowledge_base_members` 表已创建
  - `operation_logs.ip` 已改为 `VARCHAR(45)`
- 本地用户 `meijinchao` 已被提升为 `ADMIN`。
- 后端运行在 `http://localhost:8080`，前端开发端口是 `5175`。
- 前端已有 `UserRole = 'USER' | 'ADMIN'` 和 `session.user.role`，可直接用于管理员入口判断。

## 权限模型

系统角色：

```text
USER
ADMIN
```

知识库角色：

```text
OWNER    知识库创建者，隐式来自 knowledge_bases.user_id
EDITOR   可上传/重解析/删除文档，可检索和聊天
VIEWER   只读：可看文档、检索、聊天，不能写
```

权限矩阵：

| 操作 | OWNER | EDITOR | VIEWER | 无关用户 |
|---|---|---|---|---|
| 知识库列表/详情 | 是 | 是 | 是 | 否 |
| 修改/删除知识库 | 是 | 否 | 否 | 否 |
| 成员管理 | 是 | 否 | 否 | 否 |
| 文档列表/详情/状态 | 是 | 是 | 是 | 否 |
| 上传/重解析/删除文档 | 是 | 是 | 否 | 否 |
| 创建会话 / RAG 检索 | 是 | 是 | 是 | 否 |
| 聊天会话读写 | 仅会话本人 | 仅本人 | 仅本人 | 否 |
| `/api/admin/**` | 仅系统 ADMIN | — | — | — |

`OWNER` 不写入成员表；`knowledge_base_members` 只存 `EDITOR` / `VIEWER`。

## 后端接口

所有响应都包在：

```ts
type ApiResponse<T> = { code: number; message: string; data?: T }
```

分页响应：

```ts
type PageResponse<T> = {
  items: T[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}
```

### 知识库列表

`GET /api/knowledge-bases`

返回 owned + shared 知识库，每个元素新增 `role`：

```ts
type KnowledgeBaseRole = 'OWNER' | 'EDITOR' | 'VIEWER'

type KnowledgeBaseResponse = {
  id: number
  userId: number
  name: string
  description: string | null
  documentCount: number
  createdAt: string
  updatedAt: string
  role: KnowledgeBaseRole  // 新增
}
```

### 知识库成员

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/knowledge-bases/{id}/members` | — | `KnowledgeBaseMemberResponse[]` |
| POST | `/api/knowledge-bases/{id}/members` | `{ username, role }` | `KnowledgeBaseMemberResponse` |
| PUT | `/api/knowledge-bases/{id}/members/{userId}` | `{ role }` | `KnowledgeBaseMemberResponse` |
| DELETE | `/api/knowledge-bases/{id}/members/{userId}` | — | `void` |

```ts
type KnowledgeBaseMemberRole = 'EDITOR' | 'VIEWER'

type KnowledgeBaseMemberResponse = {
  userId: number
  username: string
  nickname: string | null
  role: KnowledgeBaseMemberRole
  createdAt: string
}

type AddKnowledgeBaseMemberRequest = {
  username: string
  role: KnowledgeBaseMemberRole
}

type UpdateKnowledgeBaseMemberRoleRequest = {
  role: KnowledgeBaseMemberRole
}
```

成员规则：

- 只有 OWNER 能管理成员
- 不能把 OWNER 加为成员
- 不能添加自己
- 目标用户必须存在且为 ENABLED
- 重复添加返回 409（`RESOURCE_CONFLICT`）
- 用户不存在返回 404
- 非 OWNER 调用成员接口返回 403

### 管理员用户

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/admin/users?keyword=&status=&role=&page=&size=` | — | `PageResponse<UserResponse>` |
| PATCH | `/api/admin/users/{id}/status` | `{ status: 'ENABLED' \| 'DISABLED' }` | `UserResponse` |
| PATCH | `/api/admin/users/{id}/role` | `{ role: 'USER' \| 'ADMIN' }` | `UserResponse` |

已有类型：

```ts
type UserResponse = {
  id: number
  username: string
  nickname: string | null
  role: UserRole
  status: UserStatus
  createdAt: string
}
```

管理员保护规则：

- 不能禁用自己
- 不能把自己降级为 USER
- 不能禁用/降级最后一个启用中的 ADMIN
- 违反时返回 400/403，带中文 message

### 操作审计

`GET /api/admin/operation-logs?userId=&action=&resourceType=&resourceId=&from=&to=&page=&size=`

- `from` / `to` 格式：`YYYY-MM-DD`
- `page` 从 1 开始
- 返回 `PageResponse<OperationLogResponse>`

```ts
type OperationAction =
  | 'USER_REGISTER'
  | 'USER_LOGIN'
  | 'USER_ENABLE'
  | 'USER_DISABLE'
  | 'USER_ROLE_CHANGE'
  | 'KB_CREATE'
  | 'KB_UPDATE'
  | 'KB_DELETE'
  | 'KB_MEMBER_ADD'
  | 'KB_MEMBER_ROLE_CHANGE'
  | 'KB_MEMBER_REMOVE'
  | 'DOCUMENT_UPLOAD'
  | 'DOCUMENT_DELETE'
  | 'DOCUMENT_REPROCESS'

type OperationResourceType = 'USER' | 'KNOWLEDGE_BASE' | 'DOCUMENT'

type OperationLogResponse = {
  id: number
  userId: number | null
  username: string | null
  action: OperationAction
  resourceType: OperationResourceType | null
  resourceId: number | null
  ip: string | null
  createdAt: string
}
```

### 安全行为

- `/api/admin/**` 仅 `ADMIN` 可访问：
  - 匿名 → 401
  - 普通 USER → 403
- JWT filter 每次请求从数据库加载用户状态和角色：
  - 被禁用/删除的用户立即 401
  - 角色变更立即生效
- 前端收到 403 时应展示后端 message，不要静默失败。

## 现有前端结构

```text
frontend/src/
├── App.tsx                         # 路由
├── api/http.ts                     # axios 封装 + 各 API
├── auth/
│   ├── AuthContext.tsx
│   ├── auth-context.ts
│   ├── session.ts                  # session.user.role 可用
│   └── useAuth.ts
├── components/
│   ├── DocumentPanel.tsx           # 上传/重解析/删除按钮
│   ├── ProtectedRoute.tsx          # 仅判断是否登录
│   └── StatusMessage.tsx
├── layout/AppLayout.tsx            # 顶部导航
├── pages/
│   ├── AuthPage.tsx
│   ├── ChatPage.tsx
│   └── KnowledgeBasePage.tsx       # 知识库列表/创建/编辑/删除
├── types/api.ts
└── utils/date.ts
```

关键位置：

- `frontend/src/types/api.ts:38`：`KnowledgeBaseResponse` 需要加 `role`
- `frontend/src/api/http.ts:302`：`knowledgeBaseApi`，需要补成员方法
- `frontend/src/App.tsx:21-25`：路由，需要加 `/admin`
- `frontend/src/layout/AppLayout.tsx:18-21`：导航，需要按 ADMIN 显示管理员入口
- `frontend/src/pages/KnowledgeBasePage.tsx:242-249`：编辑/删除按钮
- `frontend/src/components/DocumentPanel.tsx:226-237`：上传表单
- `frontend/src/components/DocumentPanel.tsx:297-306`：重新解析/删除按钮

## 改造清单

### 1. 类型

修改 `frontend/src/types/api.ts`：

- 给 `KnowledgeBaseResponse` 增加 `role: KnowledgeBaseRole`
- 新增 `KnowledgeBaseRole`、`KnowledgeBaseMemberRole`
- 新增 `KnowledgeBaseMemberResponse`、`AddKnowledgeBaseMemberRequest`、
  `UpdateKnowledgeBaseMemberRoleRequest`
- 新增 `PageResponse<T>`、`OperationAction`、`OperationResourceType`、
  `OperationLogResponse`
- 新增 `UpdateUserStatusRequest`、`UpdateUserRoleRequest`

### 2. API client

修改 `frontend/src/api/http.ts`：

```ts
export const knowledgeBaseMemberApi = {
  list(knowledgeBaseId: number) { ... GET },
  add(knowledgeBaseId: number, request: AddKnowledgeBaseMemberRequest) { ... POST },
  updateRole(knowledgeBaseId: number, userId: number, request: UpdateKnowledgeBaseMemberRoleRequest) { ... PUT },
  remove(knowledgeBaseId: number, userId: number) { ... DELETE },
}

export const adminApi = {
  listUsers(params: {
    keyword?: string
    status?: UserStatus
    role?: UserRole
    page?: number
    size?: number
  }) { ... GET /api/admin/users },

  updateUserStatus(id: number, status: UserStatus) { ... PATCH },
  updateUserRole(id: number, role: UserRole) { ... PATCH },

  listOperationLogs(params: {
    userId?: number
    action?: OperationAction
    resourceType?: OperationResourceType
    resourceId?: number
    from?: string
    to?: string
    page?: number
    size?: number
  }) { ... GET /api/admin/operation-logs },
}
```

建议用一个小的 `buildQuery` 工具，跳过 `undefined` / 空字符串，避免把 `?keyword=undefined` 发到后端。

### 3. 知识库页

修改 `frontend/src/pages/KnowledgeBasePage.tsx`：

- 在知识库名称旁显示角色标签：
  - `OWNER`：所有者
  - `EDITOR`：可编辑
  - `VIEWER`：只读
- `编辑` / `删除` 按钮只在 `selectedKnowledgeBase.role === 'OWNER'` 时显示。
- OWNER 额外显示“成员管理”按钮，打开新组件 `KnowledgeBaseMemberDialog`。
- `DocumentPanel` 传入 `canWrite={role === 'OWNER' || role === 'EDITOR'}`。
- VIEWER 选中知识库时显示一句只读提示，例如“你是该知识库的只读成员”。

注意：知识库创建入口对所有登录用户保留；创建者自动成为 OWNER。

### 4. 文档面板

修改 `frontend/src/components/DocumentPanel.tsx`：

- 增加 prop：`canWrite: boolean`
- `canWrite === false` 时：
  - 隐藏上传表单
  - 隐藏“重新解析”和“删除”按钮
  - 可显示“只读成员，无法修改文档”的提示
- 文档轮询、列表、状态展示保持不变。

### 5. 成员管理弹窗

新增 `frontend/src/components/KnowledgeBaseMemberDialog.tsx`：

- props：`knowledgeBaseId: number`、`open: boolean`、`onClose: () => void`
- 打开时 `knowledgeBaseMemberApi.list(knowledgeBaseId)`
- 表格列：用户名、昵称、角色、加入时间、操作
- 添加表单：
  - 用户名输入
  - 角色下拉：`EDITOR` / `VIEWER`
  - 提交后刷新列表
- 每行：
  - 角色下拉修改
  - 移除按钮，`window.confirm` 后调用 remove
- 错误处理：
  - 409：用户已经是成员
  - 404：用户不存在
  - 400：不能添加所有者/禁用用户
  - 403：只有所有者可以管理成员
- 成功后提示，并让父页面刷新知识库列表（成员变更可能影响缓存/角色）。

### 6. 管理员页面

新增 `frontend/src/pages/AdminPage.tsx`：

页面内两个 tab：

**用户管理**

- 筛选：关键字、状态、角色
- 表格列：ID、用户名、昵称、角色、状态、创建时间、操作
- 操作：
  - 启用/禁用按钮，`window.confirm` 后调用 `adminApi.updateUserStatus`
  - 角色下拉切换 USER/ADMIN，调用 `adminApi.updateUserRole`
- 分页：上一页/下一页，显示 `page / totalPages`
- 受限操作失败时展示后端 message（自己、最后一个管理员等）

**操作审计**

- 筛选：用户 ID、动作、资源类型、资源 ID、开始日期、结束日期
- 表格列：时间、用户、动作、资源类型、资源 ID、IP
- 分页同上
- 动作和资源类型用中文映射展示，例如：
  - `USER_LOGIN` → 登录
  - `KB_CREATE` → 创建知识库
  - `DOCUMENT_UPLOAD` → 上传文档

### 7. 路由与导航

修改 `frontend/src/App.tsx`：

```tsx
<Route path="/admin" element={<AdminRoute><AdminPage /></AdminRoute>} />
```

修改 `frontend/src/components/ProtectedRoute.tsx` 或新增 `AdminRoute`：

```tsx
export function AdminRoute({ children }: { children: React.ReactNode }) {
  const { session } = useAuth()
  if (!session) return <Navigate to="/login" replace />
  if (session.user.role !== 'ADMIN') return <Navigate to="/knowledge-bases" replace />
  return children
}
```

修改 `frontend/src/layout/AppLayout.tsx`：

- 仅当 `session?.user.role === 'ADMIN'` 时显示“管理后台” NavLink。

### 8. 样式

在 `frontend/src/App.css` 补充：

- 角色 badge：`.role-badge.owner` / `.editor` / `.viewer`
- 管理员页：筛选栏、表格、分页、tab
- 成员弹窗：遮罩、dialog、表格、表单
- 只读提示

保持与现有页面一致的视觉风格，不要引入新的 UI 框架。

## 验收清单

- [ ] `npm run build` 通过
- [ ] `npm run lint` 通过
- [ ] ADMIN 登录后能看到“管理后台”导航
- [ ] 普通 USER 看不到管理员导航，直接访问 `/admin` 会跳回知识库页
- [ ] 管理员用户列表支持搜索、状态筛选、角色筛选、分页
- [ ] 禁用用户后，该用户旧 token 再请求接口返回 401
- [ ] 管理员能查看操作审计日志，筛选和分页正常
- [ ] OWNER 能看到编辑/删除知识库、成员管理
- [ ] EDITOR 看不到知识库编辑/删除和成员管理，但能上传/重解析/删除文档
- [ ] VIEWER 看不到上传/重解析/删除文档，也不能编辑/删除知识库
- [ ] OWNER 能添加/改角色/移除成员
- [ ] 重复添加、添加所有者、添加禁用用户、非 OWNER 操作都有明确错误提示
- [ ] 共享知识库出现在成员的知识库列表中，并显示正确角色
- [ ] 聊天页能选择共享知识库；聊天会话仍然只属于创建者本人

## 本地验证建议

```bash
cd frontend
npm run build
npm run lint
npm run dev
```

后端需要运行在 `http://localhost:8080`。

本地数据库中 `meijinchao` 已经是 `ADMIN`。如果不知道它的密码，可以：

1. 注册一个测试用户
2. 用 SQL 临时提升为 ADMIN：

```sql
UPDATE users SET role = 'ADMIN' WHERE username = '你的测试用户';
```

验证完成后记得把测试用户禁用或删除，避免留下多余管理员。

## 注意事项

- 现有前端 `ApiError` 已处理 401；403 会作为普通错误抛出，需要在页面展示 message。
- 知识库列表新增的 `role` 字段是向后兼容的，旧代码忽略它不会出问题。
- 文档面板、知识库页、聊天页都可能调用知识库接口；改类型后跑一次 `tsc -b` 确认没有遗漏。
- 后端成员变更会清空知识库/文档缓存，前端在成员操作成功后重新拉列表即可。
- 本次只做前端界面，不改后端接口；如果后端行为需要调整，先确认后端当前工作区是否已提交。
