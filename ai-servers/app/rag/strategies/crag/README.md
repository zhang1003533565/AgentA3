# CRAG

## 这个功能是干什么的
Corrective RAG，会评估检索结果质量，质量不足时改写问题或补充检索。

## 需要的框架组件
- RetrievalGrader：判断召回是否可用。
- QueryRewriter：改写查询。
- FallbackRetriever：执行补救召回。

## 后续实现入口
- Runtime：`app/rag/strategies/crag/`
- Evaluator：`app/rag/evaluators/retrieval_grader.py`
