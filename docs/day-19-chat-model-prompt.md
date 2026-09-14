# Day 19：ChatModel 服务与 Prompt 组装

## 今日目标

封装大模型回答服务，并实现 Prompt 构造。

Day 18 已经可以做到：

```text
用户问题 -> 向量检索 -> 返回相关 chunks
```

Day 19 要做的是：

```text
用户问题 + 相关 chunks -> Prompt -> ChatModel -> 答案
```

完整问答接口和聊天记录入库会在 Day 20 完成。

## 为什么需要 PromptBuilder

RAG 不是直接把用户问题发给大模型。

而是把检索到的资料片段一起交给模型：

```text
系统规则
用户问题
检索到的资料片段
回答要求
```

这些内容组合起来，就是 Prompt。

## 新增文件

```text
rag/config/ChatModelProperties.java
rag/model/ChatModelService.java
rag/model/MockChatModelService.java
rag/prompt/PromptBuilder.java
rag/config/ChatModelPropertiesTests.java
rag/model/MockChatModelServiceTests.java
rag/prompt/PromptBuilderTests.java
```

## 修改文件

```text
application.yml
```

新增配置：

```yaml
app:
  chat-model:
    provider: ${CHAT_MODEL_PROVIDER:mock}
    model: ${CHAT_MODEL_NAME:mock-chat-model}
    temperature: ${CHAT_MODEL_TEMPERATURE:0.2}
```

## ChatModelService

统一的大模型接口：

```java
public interface ChatModelService {

    String generate(String prompt);
}
```

业务代码只依赖 `ChatModelService`。

以后可以替换不同实现：

```text
MockChatModelService
OpenAIChatModelService
LocalChatModelService
```

## MockChatModelService

第一版 Mock 实现。

它不调用真实模型，只根据 Prompt 返回固定格式的模拟回答。

如果 Prompt 中没有资料片段，会返回兜底答案：

```text
根据当前知识库资料，我无法回答这个问题。
```

## PromptBuilder

负责把问题和 chunks 组装成 Prompt。

Prompt 结构：

```text
你是一个严谨的知识库问答助手。

【回答规则】
只能根据资料片段回答。
资料不足时不要编造。

【用户问题】
...

【资料片段】
[资料 1]
chunkId: ...
documentId: ...
similarity: ...
content: ...

【最终回答】
```

## 为什么要设置兜底规则

RAG 的重点不是让模型什么都答。

而是：

```text
有资料时基于资料回答。
资料不足时明确说不知道。
```

这样可以减少模型幻觉。

## 今日重点

Day 19 最重要的是理解：

```text
检索结果不是最终答案。
检索结果要先拼成 Prompt。
ChatModel 根据 Prompt 生成最终回答。
```

到目前为止，RAG 主链路已经准备到：

```text
问题 -> 检索 chunks -> 构造 Prompt -> 调用 ChatModel
```

下一步 Day 20 会打通完整问答流程：

```text
创建会话
保存用户问题
检索 chunks
构造 Prompt
生成回答
保存 assistant 消息
保存引用来源
返回答案
```

## 测试结果

```text
mvn test
Tests run: 86, Failures: 0, Errors: 0, Skipped: 0
```
