# Hybrid Search

## 这个功能是干什么的
结合关键词检索和向量检索，兼顾精确匹配与语义匹配。

## 需要的框架组件
- KeywordRetriever：关键词/BM25 召回。
- VectorRetriever：向量召回。
- ScoreFusion：分数融合。

## 后续实现入口
- Runtime：`app/rag/strategies/hybrid_search/`
- Retrieval：`app/rag/retrievers/`
