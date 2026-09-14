# Day 16：Embedding 服务抽象

## 今日目标

定义统一的 Embedding 服务接口，并先用 Mock 实现跑通生成向量的能力。

Day 16 暂时不把向量写入 `document_chunks.embedding`，真正的 pgvector 入库会在 Day 17 完成。

## Embedding 是什么

Embedding 是把文本转换成一组数字向量。

例如：

```text
"Spring Boot 是一个 Java Web 框架"
        ↓
[0.12, -0.03, 0.44, ...]
```

这些数字可以用来计算文本之间的语义相似度。

在 RAG 中，后续流程会变成：

```text
用户问题 -> 生成 query embedding
文档 chunk -> 生成 chunk embedding
比较两个向量的相似度
找出最相关的 chunks
```

## 为什么要抽象 EmbeddingService

真实项目里，Embedding 可能来自不同来源：

```text
MockEmbeddingService
OpenAIEmbeddingService
LocalEmbeddingService
```

业务代码不应该直接依赖某一个具体模型。

所以我们先定义统一接口：

```java
public interface EmbeddingService {

    EmbeddingVector embedText(String text);

    default List<EmbeddingVector> embedBatch(List<String> texts) {
        return texts.stream()
                .map(this::embedText)
                .toList();
    }
}
```

后续无论换成什么模型，只要实现这个接口即可。

## 新增文件

```text
rag/config/EmbeddingProperties.java
rag/model/EmbeddingVector.java
rag/model/EmbeddingService.java
rag/model/MockEmbeddingService.java
rag/model/MockEmbeddingServiceTests.java
```

## 修改文件

```text
application.yml
```

新增配置：

```yaml
app:
  embedding:
    provider: ${EMBEDDING_PROVIDER:mock}
    dimension: ${EMBEDDING_DIMENSION:384}
```

## 核心类说明

### EmbeddingVector

表示一条向量。

```java
public record EmbeddingVector(List<Double> values)
```

它有一个辅助方法：

```java
public int dimension() {
    return values.size();
}
```

用于快速知道向量维度。

### EmbeddingService

Embedding 服务接口。

定义两个能力：

```text
embedText   单条文本向量化
embedBatch  批量文本向量化
```

### MockEmbeddingService

第一版 Mock 实现。

特点：

- 不调用外部模型。
- 根据文本内容生成稳定的伪随机向量。
- 同一段文本每次生成的向量相同。
- 不同文本通常生成不同向量。
- 向量维度由配置控制，默认 384。

## 为什么 Mock 也要稳定

如果 Mock 每次都随机生成不同结果，测试会不稳定。

所以当前实现使用：

```text
文本 -> SHA-256 -> seed -> SplittableRandom -> 固定维度向量
```

这样同一段文本会得到同一条向量。

## 为什么先不接真实模型

真实 Embedding 模型会引入：

- API Key
- 网络请求
- 调用费用
- 限流
- 失败重试
- 模型维度差异

Day 16 先把业务接口抽象出来，后面切换真实模型时，只需要新增一个实现类。

## 测试验证

测试覆盖：

- 可以生成配置指定维度的向量。
- 同一文本生成稳定结果。
- 不同文本生成不同结果。
- 批量 embedding 能按顺序返回。
- 非法维度会被拒绝。

测试结果：

```text
mvn test
Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
```

## 今日重点

Day 16 最重要的是理解：

```text
EmbeddingService 是模型能力的抽象。
MockEmbeddingService 是为了先跑通业务链路。
后续真实模型只需要实现同一个接口。
```

到目前为止，RAG 数据处理链路走到：

```text
上传文件 -> 解析文本 -> 切分 chunk -> 生成 embedding
```

下一步 Day 17 会把 embedding 写入 PostgreSQL pgvector。
