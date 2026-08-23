# Contract

## Agent
- `ppt_outline_agent`

## Input
- `topic`: PPT 主题
- `audience`: 受众描述，展示时对应“受众”，例如评委、管理层、客户、学生
- `slide_count`: 页数要求
- `source_mode`: `topic_only`（只有主题，可基于通用知识扩展）、`non_outline`（非大纲资料，允许围绕资料扩展并重组框架）或 `outline_grounded`（上传大纲，保留原结构）
- `evidence`: 检索证据
- `constraints`: 其他约束，例如风格、语气、必须包含的章节

## Output
- `ppt_outline_markdown`

## Output Schema
- 标题：`## PPT 大纲`
- 大纲级字段：
  - `主题`
  - `受众`
  - `建议页数`
  - `整体目标`
  - `风格建议`
- 页面级字段：
  - `页标题`
  - `页面类型`
  - `本页目标`
  - `核心内容`
  - `页面节点`（2-4 个节点，格式为“节点标题｜节点说明”）
  - `展示建议`
  - `素材建议`
