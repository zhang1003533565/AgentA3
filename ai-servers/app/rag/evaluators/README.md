# Evaluation

放数据集、指标、LLM Judge、报表，用于评估召回和回答质量。

当前已提供 `RagEvaluator`：

- `hitRate`
- `mrr`
- `contextRelevance`
- `faithfulness`
- `answerTermCoverage`

API 入口：`POST /internal/rag/evaluate`。
