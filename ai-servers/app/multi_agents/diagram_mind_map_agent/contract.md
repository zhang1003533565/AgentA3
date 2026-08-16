# Contract

## Agent
- `diagram_mind_map_agent`

## Input
- prompt (必填，由 mind_map_agent 生成的提示词)
- style (可选，图片风格)
- size (可选，图片尺寸)
- seed (可选，随机种子)
- negative_prompt (可选，负面提示词)

## Output
- image_generation_result (JSON 格式，包含图片 URL/Base64)

## Error Handling
- 提示词为空时返回错误
- 图片生成失败时在 message 字段中说明原因
