# Contract

- agentName: `diagram_architecture_agent`
- input: architecture material, module description, service dependency text, or retrieved evidence
- output: Mermaid architecture diagram code block

## Rules

- Output must be a single Mermaid code block.
- Use only modules, services, data sources, and relationships supported by user input or evidence.
- If the input already contains structured architecture fields such as `图类型`、`节点清单`、`关系清单`、`分层分组`、`布局要求` or `禁止事项`, prioritize those fields as the primary source of truth.
- The diagram should emphasize clear layering, readable labels, and dependency/data-flow direction.
- Do not invent modules, databases, or call chains not present in the input.
