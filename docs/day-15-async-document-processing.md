# Day 15：异步文档处理

## 今日目标

把文档解析和切分从同步处理改成后台异步任务。

Day 14 的流程是：

```text
请求进来 -> 解析文档 -> 切分 chunk -> 写数据库 -> 返回
```

Day 15 调整后：

```text
请求进来 -> 立即返回 -> 后台解析文档 -> 切分 chunk -> 写数据库
```

## 为什么需要异步

文档处理可能比较慢，尤其是：

- PDF 页数较多。
- 文档内容很长。
- 后续还要调用 Embedding 模型。

如果一直让 HTTP 请求等待，用户会感觉页面卡住，也容易发生接口超时。

异步处理后，前端可以通过状态接口轮询：

```text
GET /api/documents/{documentId}/status
```

## 新增接口

```text
POST /api/documents/{documentId}/reprocess
```

作用：

```text
重新触发某个文档的后台处理。
```

典型场景：

- 文档解析失败后重试。
- 后续切片规则变化后重新生成 chunks。
- 后续向量化规则变化后重新处理。

## 状态流转

```text
PENDING -> PROCESSING -> COMPLETED
                    └-> FAILED
```

含义：

- `PENDING`：等待处理。
- `PROCESSING`：正在后台处理。
- `COMPLETED`：处理成功。
- `FAILED`：处理失败。

## 新增文件

```text
config/AsyncConfig.java
document/task/DocumentProcessTask.java
document/task/DocumentProcessTaskTests.java
```

## 修改文件

```text
document/entity/Document.java
document/service/DocumentService.java
document/service/DocumentParseService.java
document/controller/DocumentController.java
document/controller/DocumentControllerTests.java
document/service/DocumentServiceTests.java
document/service/DocumentParseServiceTests.java
```

## 核心设计

### AsyncConfig

开启 Spring 异步能力：

```java
@EnableAsync
```

并定义文档任务线程池：

```text
documentTaskExecutor
```

### DocumentProcessTask

后台任务入口。

```text
DocumentProcessTask.process(documentId)
        ↓
DocumentParseService.process(documentId)
```

它只负责启动后台处理，不负责具体解析和切分。

### DocumentParseService

继续负责真实业务：

```text
解析文件
切分文本
删除旧 chunks
保存新 chunks
更新文档状态
```

Day 15 新增了：

```java
process(Long documentId)
```

这个方法给后台任务使用。

### DocumentService

新增：

```java
reprocess(CurrentUser currentUser, Long documentId)
```

它负责：

```text
校验这个文档是不是当前用户的
把文档状态重置为 PENDING
返回当前状态
```

### DocumentController

上传文档后：

```text
先保存 documents 记录
再触发后台处理任务
最后立即返回上传结果
```

重新处理文档时：

```text
先校验权限并重置状态
再触发后台处理任务
最后立即返回 PENDING 状态
```

## 为什么异步任务只传 documentId

后台任务不应该依赖 HTTP 请求上下文。

所以它不传：

```text
Authentication
CurrentUser
MultipartFile
```

而是只传：

```text
documentId
```

后台任务再根据 `documentId` 从数据库重新查询文档。

## 为什么上传后在 Controller 里触发任务

上传保存文档时有数据库事务。

如果在事务提交前就启动后台线程，后台线程可能读不到刚插入的文档。

所以当前实现是：

```text
DocumentService.upload 事务完成并返回
        ↓
DocumentController 调用 DocumentProcessTask.process
```

这样后台任务读取数据库时，文档记录已经存在。

## 验收标准

- 上传接口可以立即返回。
- 上传后后台自动处理文档。
- 可以通过状态接口查看 `PROCESSING`、`COMPLETED` 或 `FAILED`。
- 失败后可以调用 `/reprocess` 重新处理。
- 原有同步 `/parse` 接口仍然保留，方便调试。

## 测试结果

```text
mvn test
Tests run: 63, Failures: 0, Errors: 0, Skipped: 0
```

## 今日重点

Day 15 最重要的是理解：

```text
HTTP 请求线程只负责接收请求和立即响应。
耗时的文档处理交给后台任务线程。
文档处理进度通过 status 字段表达。
```
