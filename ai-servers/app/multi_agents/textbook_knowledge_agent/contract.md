# Contract

## Agent
- `textbook_knowledge_agent`

## Input
- authorization, intent, keyword, input_text, context_candidates

## Output
- answer, matched_results, java_backend_meta

## Error Handling
- 输入为空时返回可读提示。
- 证据不足时输出“暂无足够证据”。
- 不调用本地知识库、向量库或检索策略。
