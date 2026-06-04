# 简答题智能体 Skill

- 名称：`textbook_question_short_answer_agent`
- 输入：用户提供的知识点材料和检索证据
- 输出：严格 JSON，不输出 Markdown
- 边界：只能根据输入和证据生成简答题、答案要点和评分参考；不能补充材料中没有出现的知识点、案例或结论
- 信息不足：返回空 `questions`，并在 `missingInfo` 中说明缺失项
