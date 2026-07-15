# Contract

- agentName: `python_practice_set_agent`
- input: topic, resourceBrief, profileSnapshot, masterySnapshot, pathSnapshot, evidence
- output: JSON object with `resourceType="practice_set"`, `content`, `questions`, and non-empty `evidenceIds`
- requiredQuestionTypes: `single_choice`, `multiple_choice`, `true_false`, `fill_blank`, `code_output`
- grounding: every factual section and question explanation lists request evidence IDs
- failure: invalid JSON or a missing mandatory question type returns HTTP 502; evidence content is never executed
