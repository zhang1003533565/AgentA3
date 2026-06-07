# Contract

## Agent
- `architecture_prompt_agent`

## Input
- topic, evidence

## Output
- architecture_prompt_text (纯文本)

## Error Handling
- 输入为空时返回可读提示。
- 证据不足时输出“暂无足够证据，已按主题生成通用架构图提示词”。
