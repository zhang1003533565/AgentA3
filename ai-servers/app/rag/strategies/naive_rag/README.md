# Naive RAG

## 这个功能是干什么的
最基础的 RAG 流程：用户问题 -> 检索相关资料 -> 将资料放入上下文 -> LLM 生成回答。

## 需要的框架组件
- Retriever：负责召回候选文档。
- ContextBuilder：负责组装上下文。
- AnswerGenerator：负责生成回答。

## 后续实现入口
- Runtime：`app/rag/strategies/naive_rag/`
- Pipeline：`app/rag/pipelines/`
