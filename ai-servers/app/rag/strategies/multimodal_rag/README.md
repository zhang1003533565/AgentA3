# Multimodal RAG

## 这个功能是干什么的
支持图片、PDF、表格等多模态资料的解析、索引和检索。

## 需要的框架组件
- MultimodalParser：解析多模态文件。
- ImageEmbedder：图片向量化。
- TableExtractor：表格结构化。
- CrossModalRetriever：跨模态召回。

## 后续实现入口
- Runtime：`app/rag/strategies/multimodal_rag/`
- Indexing：`app/rag/indexing/`
