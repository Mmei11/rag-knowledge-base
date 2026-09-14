package com.example.ragkb.rag.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptBuilderTests {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void shouldBuildPromptWithRetrievedChunks() {
        String prompt = promptBuilder.build(
                " Spring Boot 自动配置是什么？ ",
                List.of(new RetrievedChunkResponse(
                        200L,
                        100L,
                        0,
                        "Spring Boot 自动配置会根据 classpath 创建 Bean。",
                        0.87321)));

        assertThat(prompt).contains("你是一个知识库优先的问答助手");
        assertThat(prompt).contains("优先根据资料片段回答");
        assertThat(prompt).contains("可以使用你的通用知识回答");
        assertThat(prompt).contains("Spring Boot 自动配置是什么？");
        assertThat(prompt).contains("[资料 1]");
        assertThat(prompt).contains("chunkId: 200");
        assertThat(prompt).contains("documentId: 100");
        assertThat(prompt).contains("similarity: 0.8732");
        assertThat(prompt).contains("Spring Boot 自动配置会根据 classpath 创建 Bean。");
    }

    @Test
    void shouldBuildPromptWithFallbackRuleWhenChunksAreEmpty() {
        String prompt = promptBuilder.build("未知问题", List.of());

        assertThat(prompt).contains("以下回答未使用知识库资料");
        assertThat(prompt).contains("无法实时查询");
        assertThat(prompt).contains("【资料片段】\n无");
    }

    @Test
    void shouldRejectBlankQuestion() {
        assertThatThrownBy(() -> promptBuilder.build(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("question must not be blank");
    }
}
