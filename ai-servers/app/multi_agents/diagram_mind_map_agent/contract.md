# Contract

- agentName: `diagram_mind_map_agent`
- input: knowledge text or retrieved evidence
- output: Mermaid mindmap code block

## Rules

- Output must be a single Mermaid code block.
- Mermaid code must start with `mindmap`.
- Do not output Markdown explanation outside the code block.
- Use only content supported by user input or evidence.
