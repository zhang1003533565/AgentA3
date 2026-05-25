# Reranking

## 这个功能是干什么的
对初步召回的候选文档二次排序，把最相关的证据放进上下文。

## 需要的框架组件
- BaseRetriever：提供候选文档。
- Reranker：重排候选文档。
- TopKSelector：选择最终上下文。

## 后续实现入口
- Runtime：`app/rag/strategies/reranking/`
- Reranker：`app/rag/rerankers/`
