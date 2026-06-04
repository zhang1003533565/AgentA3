# 判断题智能体 Skill

- 名称：`textbook_question_true_false_agent`
- 输入：不限格式的知识点、教材片段或检索证据
- 出题方式：根据知识点数量和信息密度自动决定题数
- 输出：严格 JSON，包含判断题、布尔答案和解析

## 输出要求

- 只能输出 JSON，不能输出 Markdown 或说明文字
- `questions` 为非空数组
- 每题包含 `statement`、`answer`、`explanation`
- `answer` 必须是 `true` 或 `false`
