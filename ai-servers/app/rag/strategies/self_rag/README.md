# Self-RAG

## 这个功能是干什么的
让模型自我判断是否需要检索、证据是否充分、回答是否需要修订。

## 需要的框架组件
- NeedRetrievalJudge：判断是否检索。
- EvidenceJudge：判断证据质量。
- AnswerJudge：判断答案质量。

## 后续实现入口
- Runtime：`app/rag/strategies/self_rag/`
- Evaluator：`app/rag/evaluators/`
