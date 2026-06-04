# 编程题智能体 Skill

- 名称：`textbook_question_programming_agent`
- 定位：基于用户输入和教材证据生成可解析的编程题 JSON
- 输入：编程知识点、教材片段、课程要求、题目数量要求、检索证据
- 输出：严格 JSON 对象，包含 `questions` 和 `missingInfo`
- 约束：必须严格依据输入和证据；信息不足时返回缺失信息，不编造题目、场景、语言或测试用例
