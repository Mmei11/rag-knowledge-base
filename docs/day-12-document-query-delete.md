# Day 12：文档列表、状态查询与删除

## 今日目标

在 Day 11 文档上传的基础上，补齐文档查询和删除能力。

## 新增接口

```text
GET    /api/knowledge-bases/{knowledgeBaseId}/documents
GET    /api/documents/{documentId}
GET    /api/documents/{documentId}/status
DELETE /api/documents/{documentId}
```

## 新增文件

```text
document/dto/DocumentStatusResponse.java
```

## 修改文件

```text
document/repository/DocumentRepository.java
document/service/DocumentService.java
document/controller/DocumentController.java
knowledgebase/entity/KnowledgeBase.java
document/controller/DocumentControllerTests.java
document/service/DocumentServiceTests.java
```

## 接口说明

### 查询知识库下文档列表

```text
GET /api/knowledge-bases/{knowledgeBaseId}/documents
```

作用：

```text
查询当前用户某个知识库下的所有文档。
```

权限规则：

```text
只能查询自己的知识库。
```

### 查询文档详情

```text
GET /api/documents/{documentId}
```

作用：

```text
查看某个文档的文件名、大小、类型、状态、创建时间等信息。
```

权限规则：

```text
只能查询自己的文档。
```

### 查询文档状态

```text
GET /api/documents/{documentId}/status
```

作用：

```text
只返回文档解析状态、错误信息和更新时间。
```

当前 Day 12 状态仍主要是：

```text
PENDING
```

后面异步解析时会变成：

```text
PROCESSING
COMPLETED
FAILED
```

### 删除文档

```text
DELETE /api/documents/{documentId}
```

作用：

```text
删除 documents 表记录，并删除本地 storage 中的原始文件。
```

同时会让：

```text
knowledge_bases.document_count - 1
```

## Service 主线

```text
listByKnowledgeBase()
  ↓
校验知识库属于当前用户
  ↓
按 knowledgeBaseId + userId 查询文档列表

getMine()
  ↓
按 documentId + userId 查询文档

getStatus()
  ↓
按 documentId + userId 查询文档
  ↓
返回轻量状态 DTO

delete()
  ↓
按 documentId + userId 查询文档
  ↓
确认知识库属于当前用户
  ↓
删除数据库记录
  ↓
knowledgeBase.documentCount - 1
  ↓
删除本地文件
```

## 今日重点

- 查询接口也必须做资源归属校验。
- 不要只用 `findById(id)` 查文档，要带上 `userId`。
- 删除文档时要同时处理数据库记录和本地文件。
- 状态查询接口返回轻量 DTO，方便后续前端轮询。

## 测试验证

```text
mvn test
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
```
