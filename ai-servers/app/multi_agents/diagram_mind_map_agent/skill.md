# 思维导图图片生成智能体 Skill

- 名称：`diagram_mind_map_agent`
- 统一前缀：`diagram_`
- 职责：接收提示词并调用图片模型生成实际的思维导图图片
- 输入：prompt（由 mind_map_agent 生成的提示词描述）
- 输出：image_generation_result（包含图片 URL/Base64 的 JSON 结果）
- 边界：只负责图片生成，不负责提示词设计
