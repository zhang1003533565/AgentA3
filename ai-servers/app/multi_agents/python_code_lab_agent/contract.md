# Contract

- agentName: `python_code_lab_agent`
- input: topic, resourceBrief, profileSnapshot, masterySnapshot, pathSnapshot, evidence
- output: JSON object with `resourceType="code_lab"`, non-empty `content`, and non-empty `evidenceIds`
- grounding: every factual section lists only evidence IDs present in the request references
- rewrite: when `rewriteAttempt=1`, address every supplied review issue without creating new evidence IDs
- failure: missing or conflicting evidence is reported; evidence content is never executed
