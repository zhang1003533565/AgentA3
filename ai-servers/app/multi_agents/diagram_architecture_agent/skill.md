# 图表架构图智能体 Skill

- 名称：`diagram_architecture_agent`
- 统一前缀：`diagram_`
- 职责：把系统模块、服务依赖和数据流整理为真正的架构图图片。
- 输出：image_generation_result（包含图片 URL/Base64 的 JSON 结果）。
- 边界：不能补充输入材料之外的模块、服务、数据库或调用链。
