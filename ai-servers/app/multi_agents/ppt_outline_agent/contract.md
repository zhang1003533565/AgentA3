# Contract

## Agent
- `ppt_outline_agent`

## Input
- `topic`: PPT 主题
- `scene_type`: 场景枚举，展示时对应“使用场景”，支持 `academic` / `business` / `roadshow` / `report` / `teaching`
- `audience`: 受众描述，展示时对应“受众”，例如评委、管理层、客户、学生
- `slide_count`: 页数要求
- `evidence`: 检索证据
- `constraints`: 其他约束，例如风格、语气、必须包含的章节

## Output
- `ppt_outline_markdown`

## Output Schema
- 标题：`## PPT 大纲`
- 大纲级字段：
  - `主题`
  - `使用场景`
  - `受众`
  - `建议页数`
  - `整体目标`
  - `风格建议`
- 页面级字段：
  - `页标题`
  - `页面类型`
  - `本页目标`
  - `核心内容`
  - `展示建议`
  - `素材建议`
