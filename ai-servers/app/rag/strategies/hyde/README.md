# HyDE

## 这个功能是干什么的
先让模型生成一个“假设答案/假设文档”，再用这个文本做向量检索，提高语义召回效果。

## 需要的框架组件
- HydeTransformer：生成假设文档。
- VectorRetriever：基于假设文档检索。
- EvidenceFilter：过滤不可靠结果。

## 后续实现入口
- Runtime：`app/rag/strategies/hyde/`
- Query Transformer：`app/rag/query_transformers/hyde.py`
