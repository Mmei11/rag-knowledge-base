package com.example.ragkb.rag.prompt;

import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final String FALLBACK_ANSWER = "根据当前知识库资料，我无法回答这个问题。";

    public String build(String question, List<RetrievedChunkResponse> chunks) {
        String normalizedQuestion = normalizeQuestion(question);
        List<RetrievedChunkResponse> safeChunks = chunks == null ? List.of() : List.copyOf(chunks);

        return """
                你是一个严谨的知识库问答助手。

                【回答规则】
                1. 只能根据【资料片段】回答问题。
                2. 如果资料不足，必须回答：%s
                3. 不要编造资料片段中没有的信息。
                4. 回答要简洁、准确，并尽量说明依据来自哪些资料片段。

                【用户问题】
                %s

                【资料片段】
                %s

                【最终回答】
                """.formatted(FALLBACK_ANSWER, normalizedQuestion, formatChunks(safeChunks)).trim();
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
