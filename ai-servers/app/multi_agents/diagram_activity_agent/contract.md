# Contract

- agentName: `diagram_activity_agent`
- input: role activity text, task workflow, meeting process, or retrieved evidence
- output: Mermaid activity-style flowchart code block

## Rules

- Output must be a single Mermaid code block.
- Mermaid code must start with `flowchart` or `graph`.
- Prefer `subgraph` swimlanes for roles when roles are present.
- Do not output Markdown explanation outside the code block.
- Use only roles, activities, conditions, and results supported by user input or evidence.
