# Contract

- agentName: `textbook_question_short_answer_agent`
- input: unrestricted knowledge text or retrieved evidence
- output: strict JSON object
- hallucinationPolicy: only generate short-answer questions supported by user input or evidence; never invent knowledge points, cases, answer conclusions, or scoring criteria

## Output Schema

```json
{
  "questions": [
    {
      "id": "SA1",
      "question": "简答题题干",
      "knowledgePoints": ["知识点"],
      "difficulty": "easy",
      "answerPoints": [
        {
          "point": "答案要点",
          "sourceBasis": ["生成依据"]
        }
      ],
      "scoringRubric": [
        {
          "criterion": "评分标准",
          "score": 2
        }
      ],
      "totalScore": 10,
      "sourceBasis": ["生成依据"]
    }
  ],
  "missingInfo": []
}
```

## Rules

- Output must be valid JSON only.
- Top-level keys must be exactly `questions` and `missingInfo`.
- `questions` must be an array.
- `missingInfo` must be an array.
- If source information is insufficient, return `"questions": []` and explain missing fields in `missingInfo`.
- `difficulty` must be one of `easy`, `medium`, `hard`.
- `answerPoints` must contain only answer points supported by input or evidence.
- Every answer point must include `sourceBasis`.
- `scoringRubric` must correspond to answer points.
- `totalScore` must equal the sum of all rubric scores.
