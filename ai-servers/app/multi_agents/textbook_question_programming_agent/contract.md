# Contract

- agentName: `textbook_question_programming_agent`
- input: unrestricted programming knowledge text or retrieved evidence
- output: strict JSON object
- hallucinationPolicy: only generate content supported by user input or evidence; never invent problem background, language, constraints, examples, or test cases

## Output Schema

```json
{
  "questions": [
    {
      "id": "P1",
      "title": "题目标题",
      "knowledgePoints": ["知识点"],
      "difficulty": "easy",
      "language": "未指定",
      "description": "题目描述",
      "inputFormat": "输入格式",
      "outputFormat": "输出格式",
      "constraints": ["约束条件"],
      "examples": [
        {
          "input": "样例输入",
          "output": "样例输出",
          "explanation": "样例解释"
        }
      ],
      "testCases": [
        {
          "input": "测试输入",
          "expectedOutput": "期望输出",
          "hidden": false
        }
      ],
      "solutionOutline": ["解题步骤"],
      "referenceSolution": "参考代码或空字符串",
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
- `language` must be explicitly supported by input or evidence; otherwise use `未指定`.
- `referenceSolution` must be an empty string when language is not specified.
