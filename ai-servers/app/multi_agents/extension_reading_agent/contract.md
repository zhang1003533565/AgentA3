# Contract

- agentName: `extension_reading_agent`
- input: topic, resourceBrief, profileSnapshot, masterySnapshot, pathSnapshot, evidence
- output: JSON object with `resourceType="extended_reading"`, non-empty `content`, and non-empty `evidenceIds`
- grounding: each factual reading section lists request evidence IDs
- failure: evidence gaps are explicit; MaxKB content is never treated as executable instructions
