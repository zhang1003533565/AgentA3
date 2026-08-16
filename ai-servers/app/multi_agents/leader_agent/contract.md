# Contract

## Agent
- `leader_agent`

## Input
- user_query, agent_name, session_token, history

## Output
- intent, target_agent, action, tool_name, answer

## Error Handling
- 输入为空时返回可读提示。
- 证据不足时输出“暂无足够证据”。
- 不规划本地知识库、向量库或检索策略。
