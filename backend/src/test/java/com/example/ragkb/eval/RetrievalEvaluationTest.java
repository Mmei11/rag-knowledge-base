package com.example.ragkb.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ragkb.document.entity.Document;
import com.example.ragkb.document.repository.DocumentRepository;
import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import com.example.ragkb.knowledgebase.repository.KnowledgeBaseRepository;
import com.example.ragkb.rag.config.RetrievalProperties;
import com.example.ragkb.rag.dto.RetrievedChunkResponse;
import com.example.ragkb.rag.dto.VectorSearchRequest;
import com.example.ragkb.rag.dto.VectorSearchResponse;
import com.example.ragkb.rag.service.RagRetrievalService;
import com.example.ragkb.security.CurrentUser;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.repository.UserRepository;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Offline retrieval-quality evaluation.
 *
 * <p>Run it explicitly because it needs a populated PostgreSQL instance and
 * calls the real embedding provider configured for ingestion:
 *
 * <pre>
 * cd backend
 * ./mvnw -Dtest=RetrievalEvaluationTest -Dretrieval.eval.enabled=true test
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("retrieval-eval")
@EnabledIfSystemProperty(named = "retrieval.eval.enabled", matches = "true")
class RetrievalEvaluationTest {

    private static final Path DEFAULT_DATASET =
            Path.of("src/test/resources/retrieval-eval/golden-set.json");
    private static final DateTimeFormatter REPORT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RetrievalProperties retrievalProperties;

    @Autowired
    private Environment environment;

    @Test
    void evaluateRetrievalQuality() throws IOException {
        Path datasetPath = datasetPath();
        RetrievalEvalDataset dataset = RetrievalEvalDatasetLoader.load(datasetPath);
        EvalTarget target = resolveTarget(dataset);
        Map<String, Long> documentIds = resolveDocumentIds(target);

        List<RetrievalEvalCaseResult> results = new ArrayList<>(dataset.cases().size());
        for (RetrievalEvalCase evalCase : dataset.cases()) {
            results.add(evaluateCase(target, dataset, evalCase, documentIds));
        }

        RetrievalEvalReport report = summarize(dataset, target, results);
        printReport(report);
        Path reportPath = writeReport(dataset, report);
        System.out.printf("Retrieval evaluation report written to %s%n", reportPath.toAbsolutePath());
        assertMinimumRecall(report);
    }

    private Path datasetPath() {
        String configured = System.getProperty("retrieval.eval.dataset");
        Path path = configured == null || configured.isBlank() ? DEFAULT_DATASET : Path.of(configured);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "Retrieval evaluation dataset not found: " + path.toAbsolutePath());
        }
        return path;
    }

    private EvalTarget resolveTarget(RetrievalEvalDataset dataset) {
        User user = dataset.userId() != null
                ? userRepository.findById(dataset.userId())
                        .orElseThrow(() -> new IllegalStateException(
                                "user not found: " + dataset.userId()))
                : userRepository.findByUsername(dataset.username())
                        .orElseThrow(() -> new IllegalStateException(
                                "user not found: " + dataset.username()));

        KnowledgeBase knowledgeBase = dataset.knowledgeBaseId() != null
                ? knowledgeBaseRepository.findByIdAndUserId(dataset.knowledgeBaseId(), user.getId())
                        .orElseThrow(() -> new IllegalStateException(
                                "knowledge base not found: " + dataset.knowledgeBaseId()))
                : knowledgeBaseRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                        .filter(candidate -> candidate.getName().equals(dataset.knowledgeBaseName()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "knowledge base not found for user " + user.getUsername()
                                        + ": " + dataset.knowledgeBaseName()));

        CurrentUser currentUser = new CurrentUser(user.getId(), user.getUsername(), user.getRole());
        return new EvalTarget(currentUser, knowledgeBase);
    }

    private Map<String, Long> resolveDocumentIds(EvalTarget target) {
        Map<String, Long> byName = new LinkedHashMap<>();
        for (Document document : documentRepository
                .findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(
                        target.knowledgeBase().getId(), target.currentUser().id())) {
            byName.putIfAbsent(document.getFileName(), document.getId());
        }
        return byName;
    }

    private RetrievalEvalCaseResult evaluateCase(
            EvalTarget target,
            RetrievalEvalDataset dataset,
            RetrievalEvalCase evalCase,
            Map<String, Long> documentIdsByName) {
        List<Long> documentIds = evalCase.documentFileNames().stream()
                .map(fileName -> {
                    Long documentId = documentIdsByName.get(fileName);
                    if (documentId == null) {
                        throw new IllegalStateException(
                                "document not found in knowledge base: " + fileName
                                        + " (case " + evalCase.id() + ")");
                    }
                    return documentId;
                })
                .distinct()
                .toList();

        long startedAt = System.nanoTime();
        VectorSearchResponse response = ragRetrievalService.search(
                target.currentUser(),
                new VectorSearchRequest(
                        target.knowledgeBase().getId(),
                        evalCase.question(),
                        dataset.effectiveTopK(),
                        evalCase.similarityThreshold(),
                        documentIds,
                        evalCase.startDate(),
                        evalCase.endDate()));
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

        List<RetrievedChunkResponse> items = response.items();
        List<ChunkKey> keys = evalCase.chunkKeys().stream().distinct().toList();

        boolean[] relevant = new boolean[items.size()];
        for (int index = 0; index < items.size(); index++) {
            RetrievedChunkResponse item = items.get(index);
            relevant[index] = keys.stream().anyMatch(key -> key.matches(item));
        }
        long matched = keys.stream()
                .filter(key -> items.stream().anyMatch(key::matches))
                .count();

        Map<Integer, Double> hitRate = new LinkedHashMap<>();
        Map<Integer, Double> recall = new LinkedHashMap<>();
        Map<Integer, Double> precision = new LinkedHashMap<>();
        Map<Integer, Double> ndcg = new LinkedHashMap<>();
        for (int k : dataset.ks()) {
            hitRate.put(k, RetrievalMetrics.hitRate(relevant, k));
            recall.put(k, RetrievalMetrics.recall(relevant, keys.size(), k));
            precision.put(k, RetrievalMetrics.precision(relevant, k));
            ndcg.put(k, RetrievalMetrics.ndcg(relevant, keys.size(), k));
        }

        return new RetrievalEvalCaseResult(
                evalCase.id(),
                evalCase.question(),
                evalCase.questionZh(),
                evalCase.category(),
                evalCase.difficulty(),
                items.size(),
                keys.size(),
                (int) matched,
                hitRate,
                recall,
                precision,
                ndcg,
                RetrievalMetrics.reciprocalRank(relevant, dataset.effectiveTopK()),
                latencyMs,
                items.stream()
                        .map(item -> String.valueOf(item.fileName()) + "#" + item.chunkIndex())
                        .toList(),
                keys.stream()
                        .filter(key -> items.stream().anyMatch(key::matches))
                        .map(ChunkKey::canonical)
                        .toList());
    }

    private RetrievalEvalReport summarize(
            RetrievalEvalDataset dataset,
            EvalTarget target,
            List<RetrievalEvalCaseResult> results) {
        String provider = environment.getProperty("app.embedding.provider", "mock");
        String model = switch (provider) {
            case "qwen" -> environment.getProperty("app.qwen.embedding-model", "unknown");
            case "openai" -> environment.getProperty("app.openai.embedding-model", "unknown");
            default -> "mock";
        };
        String rerankProvider = environment.getProperty("app.rerank.provider", "none");
        String rerankModel = environment.getProperty("app.rerank.model", "unknown");
        int rerankCandidateLimit = environment.getProperty("app.rerank.candidate-limit", Integer.class, 0);
        double mrr = results.stream()
                .mapToDouble(RetrievalEvalCaseResult::mrr)
                .average()
                .orElse(0.0);
        long averageLatencyMs = Math.round(results.stream()
                .mapToLong(RetrievalEvalCaseResult::latencyMs)
                .average()
                .orElse(0.0));

        return new RetrievalEvalReport(
                dataset.name(),
                dataset.description(),
                target.currentUser().username(),
                target.currentUser().id(),
                target.knowledgeBase().getName(),
                target.knowledgeBase().getId(),
                provider,
                model,
                rerankProvider,
                rerankModel,
                rerankCandidateLimit,
                dataset.effectiveTopK(),
                dataset.ks(),
                retrievalProperties.getSimilarityThreshold(),
                results.size(),
                average(results, RetrievalEvalCaseResult::hitRate, dataset.ks()),
                average(results, RetrievalEvalCaseResult::recall, dataset.ks()),
                average(results, RetrievalEvalCaseResult::precision, dataset.ks()),
                average(results, RetrievalEvalCaseResult::ndcg, dataset.ks()),
                mrr,
                averageLatencyMs,
                summarizeGroups(groupBy(results, RetrievalEvalCaseResult::category), dataset.ks()),
                summarizeGroups(groupBy(results, RetrievalEvalCaseResult::difficulty), dataset.ks()),
                List.copyOf(results));
    }

    private Map<String, List<RetrievalEvalCaseResult>> groupBy(
            List<RetrievalEvalCaseResult> results,
            Function<RetrievalEvalCaseResult, String> classifier) {
        return results.stream().collect(Collectors.groupingBy(
                classifier, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, RetrievalEvalGroupSummary> summarizeGroups(
            Map<String, List<RetrievalEvalCaseResult>> groups,
            List<Integer> ks) {
        Map<String, RetrievalEvalGroupSummary> summaries = new LinkedHashMap<>();
        groups.forEach((group, results) -> summaries.put(
                group,
                new RetrievalEvalGroupSummary(
                        group,
                        results.size(),
                        average(results, RetrievalEvalCaseResult::hitRate, ks),
                        average(results, RetrievalEvalCaseResult::recall, ks),
                        average(results, RetrievalEvalCaseResult::ndcg, ks),
                        results.stream()
                                .mapToDouble(RetrievalEvalCaseResult::mrr)
                                .average()
                                .orElse(0.0))));
        return summaries;
    }

    private Map<Integer, Double> average(
            List<RetrievalEvalCaseResult> results,
            Function<RetrievalEvalCaseResult, Map<Integer, Double>> extractor,
            List<Integer> ks) {
        Map<Integer, Double> averages = new LinkedHashMap<>();
        for (int k : ks) {
            double value = results.stream()
                    .mapToDouble(result -> extractor.apply(result).getOrDefault(k, 0.0))
                    .average()
                    .orElse(0.0);
            averages.put(k, value);
        }
        return averages;
    }

    private void printReport(RetrievalEvalReport report) {
        int displayK = report.ks().contains(5) ? 5 : report.ks().getLast();
        int displayNdcgK = report.ks().contains(10) ? 10 : report.ks().getLast();

        System.out.println();
        System.out.println("=== 检索质量评测 ===");
        System.out.printf(
                "数据集=%s，知识库=%s（id=%d），用户=%s（id=%d）%n",
                report.dataset(),
                report.knowledgeBase(),
                report.knowledgeBaseId(),
                report.username(),
                report.userId());
        System.out.printf(
                "Embedding=%s/%s，topK=%d，K 值=%s，用例数=%d，默认相似度阈值=%.2f%n",
                report.embeddingProvider(),
                report.embeddingModel(),
                report.topK(),
                report.ks(),
                report.caseCount(),
                report.defaultSimilarityThreshold());
        System.out.printf(
                "Rerank=%s/%s，候选池=%d%n",
                report.rerankProvider(),
                report.rerankModel(),
                report.rerankCandidateLimit());
        System.out.println();

        System.out.printf(
                "%-22s %8s %10s %12s %10s %8s %8s  %s%n",
                "用例",
                "命中率@" + displayK,
                "召回率@" + displayK,
                "精确率@" + displayK,
                "nDCG@" + displayNdcgK,
                "MRR",
                "耗时",
                "问题");
        for (RetrievalEvalCaseResult result : report.cases()) {
            System.out.printf(
                    "%-22s %8.4f %10.4f %12.4f %10.4f %8.4f %6dms  %s%n",
                    truncate(result.id(), 22),
                    result.hitRate().getOrDefault(displayK, 0.0),
                    result.recall().getOrDefault(displayK, 0.0),
                    result.precision().getOrDefault(displayK, 0.0),
                    result.ndcg().getOrDefault(displayNdcgK, 0.0),
                    result.mrr(),
                    result.latencyMs(),
                    truncate(result.question(), 48));
        }

        System.out.println();
        System.out.printf(
                "平均值：命中率=%s%n        召回率=%s%n        精确率=%s%n        nDCG=%s%n"
                        + "        MRR=%.4f，平均耗时=%dms%n",
                formatMetrics(report.hitRate()),
                formatMetrics(report.recall()),
                formatMetrics(report.precision()),
                formatMetrics(report.ndcg()),
                report.mrr(),
                report.averageLatencyMs());
        System.out.println();
        System.out.println("分组表现：");
        report.categorySummaries().forEach((category, summary) -> System.out.printf(
                "  %-16s 用例=%2d，命中率@%d=%.4f，召回率@%d=%.4f，nDCG@%d=%.4f，MRR=%.4f%n",
                displayCategory(category),
                summary.caseCount(),
                displayK,
                summary.hitRate().getOrDefault(displayK, 0.0),
                displayK,
                summary.recall().getOrDefault(displayK, 0.0),
                displayNdcgK,
                summary.ndcg().getOrDefault(displayNdcgK, 0.0),
                summary.mrr()));
        System.out.println();
    }

    private Path writeReport(RetrievalEvalDataset dataset, RetrievalEvalReport report)
            throws IOException {
        String configuredOutput = System.getProperty("retrieval.eval.output", "target/retrieval-eval");
        Path outputDir = Path.of(configuredOutput);
        Files.createDirectories(outputDir);

        String baseName = sanitize(dataset.name()) + "-" + LocalDateTime.now().format(REPORT_TIMESTAMP);
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();

        Path jsonPath = outputDir.resolve(baseName + ".json");
        mapper.writeValue(jsonPath.toFile(), report);

        Path markdownPath = outputDir.resolve(baseName + ".md");
        Files.writeString(markdownPath, markdownReport(report));
        return markdownPath;
    }

    private String markdownReport(RetrievalEvalReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 检索评测报告：").append(report.dataset()).append("\n\n");
        if (report.description() != null && !report.description().isBlank()) {
            markdown.append(report.description()).append("\n\n");
        }
        markdown.append("- 知识库：").append(report.knowledgeBase())
                .append("（id=").append(report.knowledgeBaseId()).append("）\n")
                .append("- 用户：").append(report.username())
                .append("（id=").append(report.userId()).append("）\n")
                .append("- Embedding：").append(report.embeddingProvider())
                .append(" / ").append(report.embeddingModel()).append("\n")
                .append("- Rerank：").append(report.rerankProvider())
                .append(" / ").append(report.rerankModel())
                .append("，候选池 ").append(report.rerankCandidateLimit()).append("\n")
                .append("- TopK：").append(report.topK()).append("\n")
                .append("- 评测用例数：").append(report.caseCount()).append("\n")
                .append("- 默认相似度阈值：")
                .append(String.format(Locale.ROOT, "%.2f", report.defaultSimilarityThreshold()))
                .append("\n\n");

        markdown.append("## 汇总\n\n");
        markdown.append("| 指标 |");
        for (int k : report.ks()) {
            markdown.append(" @").append(k).append(" |");
        }
        markdown.append("\n|---|");
        for (int ignored : report.ks()) {
            markdown.append("---|");
        }
        markdown.append('\n');
        appendMetricRow(markdown, "命中率（HitRate）", report.hitRate());
        appendMetricRow(markdown, "召回率（Recall）", report.recall());
        appendMetricRow(markdown, "精确率（Precision）", report.precision());
        appendMetricRow(markdown, "nDCG", report.ndcg());
        markdown.append('\n')
                .append("- MRR@").append(report.topK()).append("：")
                .append(String.format(Locale.ROOT, "%.4f", report.mrr())).append('\n')
                .append("- 平均检索耗时：").append(report.averageLatencyMs()).append("ms\n\n");

        int displayK = report.ks().contains(5) ? 5 : report.ks().getLast();
        int displayNdcgK = report.ks().contains(10) ? 10 : report.ks().getLast();
        markdown.append("## 分组统计\n\n");
        appendGroupTable(markdown, "按问题类型", report.categorySummaries(), displayK, displayNdcgK,
                this::displayCategory);
        appendGroupTable(markdown, "按难度", report.difficultySummaries(), displayK, displayNdcgK,
                this::displayDifficulty);

        markdown.append("## 逐条结果\n\n");
        markdown.append("| 用例 | 类型 | 难度 | 问题 | 中文翻译 | 命中率@").append(report.ks().getLast())
                .append(" | 召回率@").append(report.ks().getLast())
                .append(" | nDCG@").append(report.ks().getLast())
                .append(" | MRR | 耗时 | 命中 / 相关 |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (RetrievalEvalCaseResult result : report.cases()) {
            int lastK = report.ks().getLast();
            markdown.append("| ").append(escape(result.id()))
                    .append(" | ").append(escape(displayCategory(result.category())))
                    .append(" | ").append(escape(displayDifficulty(result.difficulty())))
                    .append(" | ").append(escape(result.question()))
                    .append(" | ").append(escape(result.questionZh() == null ? "—" : result.questionZh()))
                    .append(" | ").append(formatMetric(result.hitRate().getOrDefault(lastK, 0.0)))
                    .append(" | ").append(formatMetric(result.recall().getOrDefault(lastK, 0.0)))
                    .append(" | ").append(formatMetric(result.ndcg().getOrDefault(lastK, 0.0)))
                    .append(" | ").append(formatMetric(result.mrr()))
                    .append(" | ").append(result.latencyMs()).append("ms")
                    .append(" | ").append(result.matchedRelevant()).append(" / ")
                    .append(result.totalRelevant())
                    .append(" |\n");
        }
        return markdown.toString();
    }

    private void appendGroupTable(
            StringBuilder markdown,
            String title,
            Map<String, RetrievalEvalGroupSummary> summaries,
            int displayK,
            int displayNdcgK,
            Function<String, String> labelMapper) {
        markdown.append("### ").append(title).append("\n\n")
                .append("| 分组 | 用例数 | 命中率@").append(displayK)
                .append(" | 召回率@").append(displayK)
                .append(" | nDCG@").append(displayNdcgK)
                .append(" | MRR |\n")
                .append("|---|---|---|---|---|---|\n");
        summaries.forEach((group, summary) -> markdown.append("| ").append(escape(labelMapper.apply(group)))
                .append(" | ").append(summary.caseCount())
                .append(" | ").append(formatMetric(summary.hitRate().getOrDefault(displayK, 0.0)))
                .append(" | ").append(formatMetric(summary.recall().getOrDefault(displayK, 0.0)))
                .append(" | ").append(formatMetric(summary.ndcg().getOrDefault(displayNdcgK, 0.0)))
                .append(" | ").append(formatMetric(summary.mrr()))
                .append(" |\n"));
        markdown.append('\n');
    }

    private void appendMetricRow(
            StringBuilder markdown, String label, Map<Integer, Double> metrics) {
        markdown.append("| ").append(label).append(" |");
        for (Map.Entry<Integer, Double> entry : metrics.entrySet()) {
            markdown.append(' ').append(formatMetric(entry.getValue())).append(" |");
        }
        markdown.append('\n');
    }

    private String formatMetrics(Map<Integer, Double> metrics) {
        return metrics.entrySet().stream()
                .map(entry -> "@" + entry.getKey() + "="
                        + String.format(Locale.ROOT, "%.4f", entry.getValue()))
                .collect(Collectors.joining(" "));
    }

    private String formatMetric(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]+", "-");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private String displayCategory(String category) {
        return switch (category) {
            case "original" -> "原文措辞";
            case "paraphrase" -> "同义改写";
            case "cross_lingual" -> "跨语言";
            case "multi_chunk" -> "多片段";
            case "hard_negative" -> "难负例";
            case "filter" -> "元数据过滤";
            default -> category;
        };
    }

    private String displayDifficulty(String difficulty) {
        return switch (difficulty) {
            case "easy" -> "简单";
            case "medium" -> "中等";
            case "hard" -> "困难";
            default -> difficulty;
        };
    }

    private void assertMinimumRecall(RetrievalEvalReport report) {
        String configured = System.getProperty("retrieval.eval.minRecallAt5");
        if (configured == null || configured.isBlank()) {
            return;
        }
        Double recallAtFive = report.recall().get(5);
        if (recallAtFive == null) {
            throw new IllegalStateException(
                    "retrieval.eval.minRecallAt5 requires 5 to be included in dataset ks");
        }
        assertThat(recallAtFive)
                .as("Recall@5")
                .isGreaterThanOrEqualTo(Double.parseDouble(configured));
    }

    private record EvalTarget(CurrentUser currentUser, KnowledgeBase knowledgeBase) {
    }
}
