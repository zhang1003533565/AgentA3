# Multi-Query RAG

## 这个功能是干什么的
让模型把用户问题改写成多个不同角度的查询，再合并多路检索结果。

## 需要的框架组件
- QueryTransformer：生成多个查询。
- Retriever：并行或串行召回。
- ResultMerger：合并、去重、排序结果。

## 后续实现入口
- Runtime：`app/rag/strategies/multi_query_rag/`
- Query Transformer：`app/rag/query_transformers/multi_query.py`
