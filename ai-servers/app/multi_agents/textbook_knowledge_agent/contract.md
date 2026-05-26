# Contract

## Agent
- `textbook_knowledge_agent`

## Input
- authorization, intent, keyword, input_text, rag_strategy

## Output
- matched_results, retrieval_meta

## Error Handling
- 输入为空时返回可读提示。
- 证据不足时输出“暂无足够证据”。
