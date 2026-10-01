package com.example.ragkb.rag.model;

import com.example.ragkb.rag.config.ChatModelProperties;
import java.util.function.Consumer;
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
            return "以下回答未使用知识库资料：这是通用模拟回答。";
        }
        return "这是 " + properties.getModel() + " 的模拟回答：已根据检索到的资料片段生成答案。";
    }

    @Override
    public void generateStream(String prompt, Consumer<String> onDelta) {
        String answer = generate(prompt);
        for (int start = 0; start < answer.length(); start += 12) {
            onDelta.accept(answer.substring(start, Math.min(start + 12, answer.length())));
        }
    }
}
