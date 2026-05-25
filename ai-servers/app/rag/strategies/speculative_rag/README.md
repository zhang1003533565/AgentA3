# Speculative RAG

## 这个功能是干什么的
先快速生成草稿答案，同时或随后检索证据验证，再修订输出，用于降低感知延迟。

## 需要的框架组件
- DraftGenerator：快速草稿。
- EvidenceVerifier：证据验证。
- AnswerReviser：修订最终答案。

## 后续实现入口
- Runtime：`app/rag/strategies/speculative_rag/`
- Generation：`app/rag/generation/`
