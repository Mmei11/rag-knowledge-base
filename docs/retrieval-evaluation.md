# 检索质量离线评测

## 目标

在不依赖人工逐条看回答的情况下，量化“检索有没有把正确的资料片段找出来”。

评测直接调用 `RagRetrievalService.search`，因此覆盖完整链路：

```text
问题 -> Query Embedding -> pgvector 向量召回 + 关键词召回 -> RRF 融合 -> TopK
```

## 代码位置

| 文件 | 作用 |
| --- | --- |
| `backend/src/test/java/com/example/ragkb/eval/RetrievalMetrics.java` | HitRate / Recall / Precision / MRR / nDCG |
| `backend/src/test/java/com/example/ragkb/eval/ChunkKey.java` | 解析 `fileName#chunkIndex`、`chunkId:<id>` 标签 |
| `backend/src/test/java/com/example/ragkb/eval/RetrievalEvalDataset*.java` | 数据集模型与 JSON 加载 |
| `backend/src/test/java/com/example/ragkb/eval/RetrievalEvaluationTest.java` | 评测 runner、控制台报表、JSON/Markdown 报告 |
| `backend/src/test/resources/retrieval-eval/golden-set.json` | 默认评测集 |
| `backend/src/test/resources/application-retrieval-eval.yml` | 评测 profile，读取仓库根目录 `.env` |

## 数据集格式

```json
{
  "name": "food-safety-baseline",
  "description": "可选说明",
  "username": "meijinchao",
  "knowledgeBaseName": "Food secrity",
  "topK": 10,
  "ks": [1, 3, 5, 10],
  "cases": [
    {
      "id": "q-law-haccp",
      "question": "国家鼓励食品生产经营企业实施危害分析与关键控制点体系吗？",
      "relevantChunks": ["中华人民共和国食品安全法.pdf#18"],
      "documentFileNames": [],
      "startDate": null,
      "endDate": null,
      "similarityThreshold": null
    }
  ]
}
```

字段说明：

- `username` / `knowledgeBaseName`：按名称解析目标用户和知识库。也可以改用 `userId` / `knowledgeBaseId`。
- `relevantChunks`：相关片段标签，推荐 `fileName#chunkIndex`；也支持 `chunkId:<id>`，但 chunk id 在重建数据库后会变化。
- `documentFileNames`：可选，按文件名把检索限制在指定文档内。
- `startDate` / `endDate`：可选，按文档上传日期过滤。
- `similarityThreshold`：可选，覆盖默认相似度阈值。
- 未知字段（例如 `_note`）会被忽略，方便做标注备注。

## 标注方法

先用 SQL 找到“答案所在的 chunk”，再写成 `fileName#chunkIndex`：

```sql
SELECT d.file_name,
       c.chunk_index,
       LEFT(REPLACE(c.content, E'\n', ' '), 180) AS preview
FROM document_chunks c
JOIN documents d ON d.id = c.document_id
WHERE c.knowledge_base_id = 5
ORDER BY d.file_name, c.chunk_index
LIMIT 200;
```

标注时建议覆盖：

- 原文措辞类问题（当前 baseline 的主要构成）
- 口语化 / 同义改写问题
- 跨段落、答案分布在多个 chunk 的问题
- 与目标 chunk 高度相似的“难负例”
- 知识库确实没有答案的问题（后续可扩展 no-answer 指标）

## 运行方式

### 本机有 Java 21 + Maven

```bash
cd backend
./mvnw -Dtest=RetrievalEvaluationTest -Dretrieval.eval.enabled=true test
```

### 本机没有 Java，使用 Docker Maven

项目提供了脚本，直接运行：

```bash
cd backend
./scripts/retrieval-eval.sh
```

需要自定义数据集或阈值时，把 Maven 参数直接传给它：

```bash
./scripts/retrieval-eval.sh -Dretrieval.eval.dataset=/path/to/dataset.json -Dretrieval.eval.minRecallAt5=0.8
```

脚本等价于：

```bash
cd backend
docker run --rm --add-host=host.docker.internal:host-gateway \
  --env-file ../.env \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/rag_kb \
  -e REDIS_HOST=host.docker.internal \
  -v "$PWD":/workspace \
  -v ragkb-maven-repo:/root/.m2 \
  -w /workspace \
  maven:3.9-eclipse-temurin-21 \
  mvn -s /workspace/.mvn/settings.xml \
  -Dtest=RetrievalEvaluationTest \
  -Dretrieval.eval.enabled=true test
```

可选参数：

| 参数 | 说明 |
| --- | --- |
| `-Dretrieval.eval.dataset=/path/to/dataset.json` | 使用自定义评测集 |
| `-Dretrieval.eval.output=target/retrieval-eval` | 报告输出目录 |
| `-Dretrieval.eval.minRecallAt5=0.8` | 当 Recall@5 低于阈值时让测试失败，可用于回归门禁 |
| `RETRIEVAL_SIMILARITY_THRESHOLD=0.20 ./scripts/retrieval-eval.sh` | 覆盖检索相似度阈值，用于阈值消融 |

报告会写到 `backend/target/retrieval-eval/<dataset>-<timestamp>.json` 和 `.md`。

## 阈值消融结论

在 `food-safety-hardened` 上分别以 `0.35` 和 `0.20` 运行，Hit@5、Recall@5、nDCG@10、MRR 完全一致；
日志中的 `vectorResultCount` 基本都等于 `candidateLimit = topK × candidateMultiplier = 30`，
说明当前阈值没有过滤掉候选，不是召回瓶颈。

4 条完全漏召回（`q-paper-title`、`q-xl-handwashing-zh`、`q-para-heating`、`q-hn-hepatitis-exclusion`）
属于排序或跨语言召回问题，调低阈值不会改善。下一步应优先做 Rerank 和查询改写，而不是继续降低阈值。

## 指标含义

- **HitRate@K**：TopK 中至少命中一个相关片段的问题占比。
- **Recall@K**：TopK 命中的相关片段数 / 数据集中标注的相关片段数。
- **Precision@K**：TopK 命中的相关片段数 / K。
- **nDCG@K**：考虑排序位置的归一化折损累计增益，越靠前得分越高。
- **MRR@K**：第一个相关片段排名的倒数均值。

单片段问题中 Recall@K 与 HitRate@K 数值相同；多片段问题中 Recall@K 会低于 HitRate@K，除非所有标注片段都被召回。当前评测集已包含多 chunk 用例，可以直接观察这一差异。

## 注意事项

1. **Embedding 必须与建库时一致**。如果文档是用 Qwen 向量化的，评测也必须用同一个 Qwen embedding 模型；否则向量空间不可比，指标没有意义。
2. **Mock embedding 不能用于质量评测**。`MockEmbeddingService` 基于文本哈希生成随机向量，只适合验证链路能否跑通。
3. **当前 baseline 仍有提升空间**。评测集已覆盖原文、同义改写、跨语言、多 chunk、难负例和过滤六类问题，但仍可继续补充无答案问题和更口语化的对抗样本。
4. **chunk 标签依赖切块策略**。如果调整 `TextChunker` 的 chunk size / overlap，需要重新标注 `fileName#chunkIndex`。
5. **评测会真实调用 embedding API**，46 条问题约消耗 46 次 embedding 请求，注意额度与耗时。
