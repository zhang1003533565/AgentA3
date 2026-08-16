# Contract

- agentName: `diagram_architecture_agent`
- input: architecture material, module description, service dependency text, or retrieved evidence
- output: image_generation_result (JSON 格式，包含图片 URL/Base64)

## Rules

- Output must be an image generation result, not Mermaid code.
- Use only modules, services, data sources, and relationships supported by user input or evidence.
- If the input already contains an `architecture_prompt_agent` template such as `图类型`、`节点清单`、`关系清单`、`分层分组`、`布局要求` or `禁止事项`, prioritize those fields as the primary source of truth.
- The image should emphasize clear layering, readable labels, and dependency/data-flow direction.
- Do not invent modules, databases, or call chains not present in the input.
