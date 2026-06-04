# Contract

## Agent
- `ppt_layout_agent`

## Input
- `ppt_outline`: 上游 `ppt_outline_agent` 输出的大纲
- `theme`: 可选主题补充
- `constraints`: 布局约束，例如风格、品牌色、信息密度、是否偏图文或偏数据

## Output
- `ppt_layout_markdown`

## Output Schema
- 标题：`## PPT 布局方案`
- 全局字段：
  - `主题`
  - `使用场景`
  - `受众`
  - `整体布局策略`
  - `视觉风格建议`
- 页面级字段：
  - `页标题`
  - `页面类型`
  - `布局结构`
  - `信息层级`
  - `区域安排`
  - `视觉建议`
  - `素材处理`
