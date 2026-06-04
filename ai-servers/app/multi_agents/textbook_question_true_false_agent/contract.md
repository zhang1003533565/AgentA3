# Contract

- agentName: `textbook_question_true_false_agent`
- input: unrestricted knowledge text or retrieved evidence
- output: strict JSON object
- questionCount: determined by knowledge point coverage

## Output Schema

```json
{
  "questions": [
    {
      "statement": "判断题陈述句",
      "answer": true,
      "explanation": "解析"
    }
  ]
}
```

## Rules

- Output must be valid JSON only.
- `questions` must be a non-empty array.
- `answer` must be a boolean, not a string.
- The number of questions should match the number and density of knowledge points.
