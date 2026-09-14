package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.ChatModelProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.chat-model", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockChatModelService implements ChatModelService {

    private final ChatModelProperties properties;

    public MockChatModelService(ChatModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (!prompt.contains("[资料 1]")) {
            return "根据当前知识库资料，我无法回答这个问题。";
        }
        return "这是 " + properties.getModel() + " 的模拟回答：已根据检索到的资料片段生成答案。";
    }
}
