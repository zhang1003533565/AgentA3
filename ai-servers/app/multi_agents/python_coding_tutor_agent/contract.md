# Contract

- agentName: `python_coding_tutor_agent`
- input: JSON object with `questionType` (`hint|solution|explain|debug|free`), `problem` (id/number/title/difficulty/description/examples/tags/funcName), `userCode`, `judgeResult` (nullable), `followUp` (nullable), `history` (list of {role, content})
- output: Markdown text answering the requested type
- anti-cheating: never emit a full AC implementation unless explicitly requested after guidance; hint must escalate in 3 levels on request; never dump the full hidden testcase set
- missing-input: when `userCode` is empty for explain/debug, state what is missing instead of fabricating
- failure: if the model cannot answer, explain what information or configuration is missing; never fall back to local canned templates
