package com.example.ragkb.rag.prompt;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String build(String question, List<RetrievedChunkResponse> chunks) {
        String normalizedQuestion = normalizeQuestion(question);
        List<RetrievedChunkResponse> safeChunks = chunks == null ? List.of() : List.copyOf(chunks);

        return """
                你是一个知识库优先的问答助手。

                【回答规则】
                1. 如果【资料片段】足以回答问题，优先根据资料片段回答，并尽量说明依据来自哪些资料片段。
                2. 如果问题与资料片段无关，或者资料片段不足以回答，可以使用你的通用知识回答。
                3. 使用通用知识回答时，需要明确说明“以下回答未使用知识库资料”。
                4. 不要把通用知识伪装成资料片段内容，也不要编造资料片段中没有的引用。
                5. 对天气、股价、新闻等实时信息，如果你无法获取实时数据，需要说明无法实时查询，并给出合理的查询建议。
                6. 回答要简洁、准确，优先使用中文。

                【用户问题】
                %s

                【资料片段】
                %s

                【最终回答】
                """.formatted(normalizedQuestion, formatChunks(safeChunks)).trim();
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
}
