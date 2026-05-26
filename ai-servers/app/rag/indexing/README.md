# Indexing

放知识入库链路：加载、解析、切分、Embedding、向量库/图谱/结构化存储写入。

当前本地入库入口是 `app.rag.pipelines.ingestion.RagIngestionPipeline`：

- 将 API 提交的文档保存到 `knowledge_base/raw/api_ingest/`。
- 使用 `DocumentLoader` 和 `MultimodalParser` 解析 Markdown、TXT、CSV、JSON、HTML。
- 使用 `SemanticChunker` 生成本地 chunks。
- 使用 `EmbeddingWriter` 写入 `.index/local_chunks.jsonl`，作为后续替换真实向量库前的本地索引清单。

`VectorRetriever`、`KeywordRetriever`、`HybridRetriever` 和 `GraphRetriever` 会通过 `app.rag.vector_stores` 优先读取 `.index/local_chunks.jsonl`；当索引不存在或为空时，才回退到扫描原始文件目录。
