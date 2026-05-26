# Contract

## Agent
- `leader_agent`

## Input
- user_query, rag_strategy, session_token, history

## Output
- intent, target_agent, need_retrieval, answer

## Error Handling
- 输入为空时返回可读提示。
- 证据不足时输出“暂无足够证据”。
