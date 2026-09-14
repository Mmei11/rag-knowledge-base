# Day 05 - 后端基础规范

## 1. 今日目标

建立后续业务接口统一复用的响应、异常、校验、分页和跨域能力。

## 2. 代码结构

```text
backend/src/main/java/com/example/ragkb/
├── common/
│   ├── api/
│   │   └── ApiResponse.java
│   ├── exception/
│   │   ├── BusinessException.java
│   │   ├── ErrorCode.java
│   │   └── GlobalExceptionHandler.java
│   └── page/
│       ├── PageRequest.java
│       └── PageResponse.java
└── config/
    ├── CorsConfig.java
    └── SecurityConfig.java
```

## 3. 统一响应

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

参数错误：

```json
{
  "code": 40000,
  "message": "请求参数错误",
  "data": {
    "name": "名称不能为空"
  }
}
```

业务冲突：

```json
{
  "code": 40900,
  "message": "用户名已存在",
  "data": null
}
```

接口同时使用正确的 HTTP 状态码，例如参数错误返回 `400`，资源不存在返回 `404`，资源冲突返回 `409`。

## 4. 异常处理

`GlobalExceptionHandler` 当前统一处理：

- `BusinessException`：可预期的业务错误。
- `MethodArgumentNotValidException`：JSON 请求体校验失败。
- `BindException`：查询参数或表单绑定失败。
- `ConstraintViolationException`：方法参数约束失败。
- 请求参数缺失或类型错误。
- JSON 无法解析。
- API 路径不存在。
- 未预期异常。

未预期异常只向客户端返回通用错误，完整堆栈记录在后端日志中。

## 5. 分页约定

分页从 `1` 开始：

```text
page = 1
size = 20
```

`size` 范围为 `1` 到 `100`。`PageResponse` 将 Spring Data 的零基页码转换为对外的一基页码。

## 6. CORS 与临时安全策略

默认允许以下前端来源访问 `/api/**`：

```text
http://localhost:5173
```

可以通过环境变量覆盖：

```text
CORS_ALLOWED_ORIGINS
```

Day 5 的 `SecurityConfig` 暂时允许所有请求，用于开发公共接口。Day 7 实现 JWT 后再配置公开接口和受保护接口。

## 7. 验证

`CommonWebSupportTests` 覆盖：

- 成功响应。
- Bean Validation 参数错误。
- 业务异常。
- 错误 JSON。
- 未知异常隐藏。
- 不存在的 API 路径。
- CORS 预检请求。
- 分页参数和分页响应。

运行命令：

```powershell
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd test
```
