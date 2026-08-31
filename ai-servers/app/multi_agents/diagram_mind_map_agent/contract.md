# Contract

## Agent
- `diagram_mind_map_agent`

## Input
- diagram_material (必填，知识点材料、主题描述或检索证据)
- evidence (可选，Java 后端接入的第三方知识库证据)

## Output
- Mermaid mindmap code block

## Error Handling
- 输入材料为空时返回错误
- 模型返回无法解析的 Mermaid 时返回错误
