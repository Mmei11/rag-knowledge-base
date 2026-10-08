package com.example.ragkb.rag.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.chat.entity.ChatMessage;
import com.example.ragkb.chat.entity.ChatRole;
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
    void shouldBuildStrictKnowledgeBasePrompt() {
        String prompt = promptBuilder.build("你是什么模型？", List.of(), PromptMode.STRICT_KB);

        assertThat(prompt).contains("你是一个严谨的知识库问答助手");
        assertThat(prompt).contains("只能根据【资料片段】回答问题");
        assertThat(prompt).contains("根据当前知识库资料，我无法回答这个问题。");
    }

    @Test
    void shouldBuildKnowledgeBaseFirstPromptWhenChunksAreEmpty() {
        String prompt = promptBuilder.build("未知问题", List.of(), PromptMode.KB_FIRST);

        assertThat(prompt).contains("以下回答未使用知识库资料");
        assertThat(prompt).contains("无法实时查询");
        assertThat(prompt).contains("【资料片段】\n无");
    }

    @Test
    void shouldBuildGeneralPrompt() {
        String prompt = promptBuilder.build("今天上海天气怎么样？", List.of(), PromptMode.GENERAL);

        assertThat(prompt).contains("你是一个通用 AI 问答助手");
        assertThat(prompt).contains("可以直接使用你的通用知识回答问题");
        assertThat(prompt).contains("无法实时查询");
    }

    @Test
    void shouldBuildLearningPrompt() {
        String prompt = promptBuilder.build("解释一下 MOT", List.of(), PromptMode.LEARNING);

        assertThat(prompt).contains("你是一个擅长教学讲解的知识库学习助手");
        assertThat(prompt).contains("先用通俗语言解释核心概念");
        assertThat(prompt).contains("以下内容为通用知识补充");
    }

    @Test
    void shouldRejectBlankQuestion() {
        assertThatThrownBy(() -> promptBuilder.build(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("question must not be blank");
    }

    @Test
    void shouldIncludeConversationHistoryBeforeFollowUp() {
        String prompt = promptBuilder.build("它有什么限制？", List.of(), PromptMode.STRICT_KB, List.of(
                new ChatMessage(1L, 1L, ChatRole.USER, "介绍食品安全法"),
                new ChatMessage(1L, 1L, ChatRole.ASSISTANT, "食品安全法规定了相关责任。")));

        assertThat(prompt).contains("【对话历史】", "用户：介绍食品安全法", "助手：食品安全法规定了相关责任。", "【用户问题】\n它有什么限制？");
        assertThat(prompt.indexOf("【对话历史】")).isLessThan(prompt.indexOf("【用户问题】"));
    }
}
