package com.example.ragkb.rag.prompt;

import com.example.ragkb.chat.entity.ChatMessage;
import com.example.ragkb.chat.entity.ChatRole;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String build(String question, List<RetrievedChunkResponse> chunks) {
        return build(question, chunks, PromptMode.KB_FIRST);
    }

    public String build(String question, List<RetrievedChunkResponse> chunks, PromptMode promptMode) {
        return build(question, chunks, promptMode, List.of());
    }

    public String build(String question, List<RetrievedChunkResponse> chunks, PromptMode promptMode,
            List<ChatMessage> history) {
        String normalizedQuestion = normalizeQuestion(question);
        List<RetrievedChunkResponse> safeChunks = chunks == null ? List.of() : List.copyOf(chunks);
        PromptMode safePromptMode = promptMode == null ? PromptMode.KB_FIRST : promptMode;

        return """
                %s

                【回答规则】
                %s

                【对话历史】
                %s

                【用户问题】
                %s

                【资料片段】
                %s

                【最终回答】
                """.formatted(roleInstruction(safePromptMode), rules(safePromptMode),
                        formatHistory(history), normalizedQuestion, formatChunks(safeChunks)).trim();
    }

    private String formatHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder("以下内容仅用于理解追问中的指代，不可替代当前检索资料或作为引用依据。\n");
        for (ChatMessage message : history) {
            String content = normalizeContent(message.getContent());
            builder.append(message.getRole() == ChatRole.USER ? "用户：" : "助手：")
                    .append(content, 0, Math.min(content.length(), 1200))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return question.trim();
    }

    private String formatChunks(List<RetrievedChunkResponse> chunks) {
        if (chunks.isEmpty()) {
            return "无";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunkResponse chunk = chunks.get(i);
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append("[资料 ").append(i + 1).append("]\n")
                    .append("chunkId: ").append(chunk.chunkId()).append('\n')
                    .append("documentId: ").append(chunk.documentId()).append('\n')
                    .append("chunkIndex: ").append(chunk.chunkIndex()).append('\n')
                    .append("similarity: ").append(formatSimilarity(chunk.similarity())).append('\n')
                    .append("content: ").append(normalizeContent(chunk.content()));
        }
        return builder.toString();
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    private String formatSimilarity(double similarity) {
        return String.format(Locale.ROOT, "%.4f", similarity);
    }

    private String roleInstruction(PromptMode promptMode) {
        return switch (promptMode) {
            case STRICT_KB -> "你是一个严谨的知识库问答助手。";
            case KB_FIRST -> "你是一个知识库优先的问答助手。";
            case GENERAL -> "你是一个通用 AI 问答助手。";
            case LEARNING -> "你是一个擅长教学讲解的知识库学习助手。";
        };
    }

    private String rules(PromptMode promptMode) {
        return switch (promptMode) {
            case STRICT_KB -> """
                    1. 只能根据【资料片段】回答问题。
                    2. 如果资料不足，必须回答：根据当前知识库资料，我无法回答这个问题。
                    3. 不要编造资料片段中没有的信息。
                    4. 回答要简洁、准确，并尽量说明依据来自哪些资料片段。""";
            case KB_FIRST -> """
                    1. 如果【资料片段】足以回答问题，优先根据资料片段回答，并尽量说明依据来自哪些资料片段。
                    2. 如果问题与资料片段无关，或者资料片段不足以回答，可以使用你的通用知识回答。
                    3. 使用通用知识回答时，需要明确说明“以下回答未使用知识库资料”。
                    4. 不要把通用知识伪装成资料片段内容，也不要编造资料片段中没有的引用。
                    5. 对天气、股价、新闻等实时信息，如果你无法获取实时数据，需要说明无法实时查询，并给出合理的查询建议。
                    6. 回答要简洁、准确，优先使用中文。""";
            case GENERAL -> """
                    1. 可以直接使用你的通用知识回答问题。
                    2. 如果【资料片段】与问题相关，可以结合资料片段补充回答。
                    3. 如果使用了资料片段，需要说明依据来自哪些资料片段。
                    4. 对天气、股价、新闻等实时信息，如果你无法获取实时数据，需要说明无法实时查询，并给出合理的查询建议。
                    5. 回答要简洁、准确，优先使用中文。""";
            case LEARNING -> """
                    1. 优先结合【资料片段】进行讲解。
                    2. 先用通俗语言解释核心概念，再补充关键细节。
                    3. 如果资料片段不足，可以使用通用知识补充，但需要明确说明“以下内容为通用知识补充”。
                    4. 不要把通用知识伪装成资料片段内容，也不要编造资料片段中没有的引用。
                    5. 回答要适合学习复习，结构清晰，优先使用中文。""";
        };
    }
}
