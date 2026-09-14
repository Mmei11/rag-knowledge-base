# Day 11：文档上传接口

## 今日目标

实现文档上传入口，让用户可以把 PDF / TXT / Markdown 文件上传到自己的知识库中。

本日只负责：

- 校验文件是否合法。
- 保存原始文件到本地 `storage/documents`。
- 在 `documents` 表中保存文档元数据。
- 文档初始状态为 `PENDING`。

暂不负责文档解析、文本切分、Embedding 和向量入库。

## 新增接口

```text
POST /api/knowledge-bases/{knowledgeBaseId}/documents
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

请求字段：

```text
file: 上传文件
```

成功响应核心字段：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "knowledgeBaseId": 7,
    "userId": 4,
    "fileName": "day11-upload-sample.txt",
    "fileType": "txt",
    "fileSize": 40,
    "status": "PENDING"
  }
}
```

## 新增后端文件

```text
document/entity/Document.java
document/entity/DocumentStatus.java
document/dto/DocumentResponse.java
document/repository/DocumentRepository.java
document/config/DocumentStorageProperties.java
document/service/DocumentService.java
document/controller/DocumentController.java
```

## 修改文件

```text
knowledgebase/entity/KnowledgeBase.java
common/exception/GlobalExceptionHandler.java
src/main/resources/application.yml
```

## 核心流程

```text
前端或 Postman 选择文件
        ↓
DocumentController 接收 MultipartFile
        ↓
DocumentService 校验知识库归属
        ↓
校验文件是否为空、大小、扩展名、MIME Type
        ↓
生成服务端文件名
        ↓
保存到 storage/documents/{knowledgeBaseId}/
        ↓
documents 表插入记录，状态为 PENDING
        ↓
knowledge_bases.document_count + 1
        ↓
返回 DocumentResponse
```

## 文件安全策略

当前实现做了这些限制：

- 空文件不能上传。
- 单文件最大 10MB。
- 只允许 `pdf`、`txt`、`md`、`markdown`。
- 只允许配置中的 MIME Type。
- 不直接使用用户上传的文件名作为保存文件名。
- 使用 UUID 生成服务端文件名。
- 保存路径会做 `normalize()`，避免路径穿越。

## 验证结果

已完成真实 HTTP 验证：

```text
注册临时用户
登录获取 token
创建知识库
上传 TXT 文件
documents 表写入记录
本地 storage/documents 下保存文件
上传 exe 被拒绝
```

数据库验证：

```text
id | knowledge_base_id | user_id | file_name               | file_type | file_size | status
1  | 7                 | 4       | day11-upload-sample.txt | txt       | 40        | PENDING
```

测试验证：

```text
mvn test
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
```

## 今天要掌握的点

- `MultipartFile` 是 Spring Boot 接收上传文件的核心类型。
- 文件上传请求使用 `multipart/form-data`，不是 JSON。
- 后端不能信任用户上传的原始文件名。
- 上传接口要同时考虑业务权限和文件安全。
- `PENDING` 表示文档已经进入系统，但还没有开始解析。
