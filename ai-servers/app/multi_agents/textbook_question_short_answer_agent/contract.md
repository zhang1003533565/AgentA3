# Contract

- agentName: `textbook_question_short_answer_agent`
- output: strict_question_bank_json
- questionType: `short_answer`
- validation: `app.multi_agents.question_bank_schema.review_question_bank_payload`

## Output Schema

Top-level JSON must contain exactly:

```json
{
  "questions": [],
  "missingInfo": []
}
```

Each question must use the unified question-bank shape documented in `docs/exam-question-json-spec.md`.

## Rules

- Output valid JSON only.
- Do not output Markdown code fences or explanatory text.
- Every question `type` must be `short_answer`.
- Invalid JSON or invalid question schema must not pass validation.
