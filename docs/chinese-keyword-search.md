# 中文关键词检索

## 问题

原有关键词通道使用 PostgreSQL：

```sql
to_tsvector('simple', content)
websearch_to_tsquery('simple', ?)
```

`simple` 配置不会切分中文，一整段连续汉字会被当成一个 lexeme。查询侧虽然产出了
“食品”“安全”这样的 bigram，但无法匹配整段 lexeme，实际只能靠 `LIKE + pg_trgm` 兜底，
中文关键词检索并没有真正走全文索引。

## 方案

把中文分词放到应用侧，并用一张“分词后的文本”参与 PostgreSQL 全文检索：

```text
文档入库 -> jieba INDEX 分词 -> search_text（空格分隔）
查询    -> jieba INDEX 分词 -> plainto_tsquery('simple', '词1 词2 ...')
```

这样 `simple` 配置看到的是已经用空格分隔好的词，每个词都会成为一个独立 lexeme，
GIN 索引可以真正参与中文关键词召回。

## 分词规则

实现位置：

```text
backend/src/main/java/com/example/ragkb/common/text/ChineseTextSegmenter.java
```

- 使用 `com.huaban:jieba-analysis:1.0.2` 的 `SegMode.INDEX`，同时保留长词和词典内的子词，
  提升召回。
- NFKC 归一化，过滤标点、空白。
- 汉字词长度 ≥ 2；Latin/数字串长度 ≥ 2 并转小写。
- 停用词：什么、怎么、如何、哪些、这个、那个、一下、关于、是否、请问、能否、的话、以及。
- 入库：保留词序和重复词（`ts_rank_cd` 会利用词频），最多 512 个 token。
- 查询：去重、按长度降序、最多 16 个 token；如果过滤后为空，保留单字汉字兜底。
- 入库和查询共用同一个 `ChineseTextSegmenter`，保证两边规则一致。

## 数据库变更

新增列：

```sql
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS search_text TEXT;
```

新增索引：

```sql
CREATE INDEX idx_document_chunks_search_text_fts
    ON document_chunks USING GIN (to_tsvector('simple', search_text));
```

`content` 上的 trigram 索引保留，继续服务 LIKE 兜底。

migration 文件：

```text
docker/postgres/migrations/20261109_add_chunk_search_text.sql
```

## 查询逻辑

关键词 SQL 使用：

```sql
COALESCE(ts_rank_cd(
    to_tsvector('simple', c.search_text),
    plainto_tsquery('simple', ?)
), 0) + (<LIKE 分数>) AS keyword_score
```

其中 `?` 是空格分隔的查询词，`plainto_tsquery` 会把它们按 AND 组合。

最初尝试过 `to_tsquery('simple', '词1 | 词2')` 做 OR 召回，结果 Hit@5 从 0.9130 降到 0.8913：
“食品”“安全”这类常见词会把大量片段拉进关键词通道，RRF 被噪声影响。改为 AND 后
Hit@5 回到 0.9130，MRR 和 nDCG 反而超过 baseline。

`WHERE` 中保留原始表达式：

```sql
to_tsvector('simple', c.search_text) @@ plainto_tsquery('simple', ?)
```

不要把 `COALESCE` 包进 `WHERE`，否则 GIN 索引无法命中。
旧行 `search_text IS NULL` 时 FTS 不命中，仍由 LIKE 兜底。

## 评测结果

同一份 46 条评测集，关闭 rerank 隔离分词影响：

| 指标 | 旧关键词 | jieba + plainto_tsquery |
|---|---|---|
| Hit@1 | 0.6739 | 0.6957 |
| Hit@5 | 0.9130 | 0.9130 |
| Recall@5 | 0.9130 | 0.9130 |
| nDCG@10 | 0.7994 | 0.8134 |
| MRR@10 | 0.7623 | 0.7870 |
| 平均耗时 | 754ms | 566ms |

- 同义改写类 MRR 0.6813 → 0.7292。
- 多片段类 MRR 0.6333 → 0.8667，nDCG@10 0.7231 → 0.8489。
- 原文措辞类略有波动（MRR 0.8194 → 0.7963），跨语言和难负例基本不变。
- 完整报告见 `docs/retrieval-eval-keyword.md`。

## 老数据回填

不需要重新解析文档或重新生成 embedding，只对已有 chunk 做分词并更新 `search_text`。

实现：

```text
backend/src/main/java/com/example/ragkb/document/service/SearchTextBackfillService.java
backend/src/main/java/com/example/ragkb/document/service/SearchTextBackfillRunner.java
```

特性：

- `pg_try_advisory_lock` 防止多实例并发回填。
- 按 id keyset 分批，默认每批 500 条。
- 只更新 `search_text IS NULL OR BTRIM(search_text) = ''` 的行，天然幂等。
- 每批一个事务，日志输出进度。

执行一次：

```bash
cd /Users/meijinchao/Documents/Codex/2026-09-19/v/rag-knowledge

# 1. 应用 migration
docker exec -i rag-kb-postgres psql -U rag_user -d rag_kb \
  < docker/postgres/migrations/20261109_add_chunk_search_text.sql

# 2. 重建并启动 backend，开启一次性回填
SEARCH_TEXT_BACKFILL_ENABLED=true docker compose up -d --build backend

# 3. 观察日志，确认回填完成
docker compose logs -f backend

# 4. 改回默认 false 并重启
docker compose up -d --force-recreate backend
```

验证：

```sql
SELECT count(*)
FROM document_chunks
WHERE search_text IS NULL OR BTRIM(search_text) = '';
-- 期望 0
```

## 注意事项

- `search_text` 约为 content 的 1–2 倍，当前 2637 个 chunk 规模可接受。
- jieba 词典首次加载约 1–3 秒，`@PostConstruct` 已预热。
- 回填只做分词和 UPDATE，不调用 embedding API。
- 如果调整 `TextChunker` 的切块策略，需要重新解析文档，`search_text` 会随新 chunk 重新生成。
