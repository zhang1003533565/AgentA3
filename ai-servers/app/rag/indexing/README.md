# Indexing

放知识入库链路：加载、解析、切分、Embedding、向量库/图谱/结构化存储写入。

当前入库入口是 `app.rag.pipelines.ingestion.RagIngestionPipeline`：

- 将 API 提交的文档保存到 `knowledge_base/raw/api_ingest/`。
- 使用 `DocumentLoader` 和 `MultimodalParser` 解析 Markdown、TXT、CSV、JSON、HTML。
- 使用 `SemanticChunker` 生成普通 chunks，使用 `ParentChildChunker` 生成父子 chunks。
- 使用 `EmbeddingWriter` 写入当前 `RAG_VECTOR_STORE_BACKEND`，默认是 Docker Milvus。

`VectorRetriever`、`KeywordRetriever`、`HybridRetriever`、`ParentChildRetriever` 和 `GraphRetriever` 都通过 `app.rag.vector_stores` 读取向量库数据。默认 Milvus 模式不会扫描原始本地目录作为兜底。
