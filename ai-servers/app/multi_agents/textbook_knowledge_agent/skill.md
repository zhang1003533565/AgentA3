# 教材知识点智能体 Skill

## 1. 智能体定位
- 名称：`textbook_knowledge_agent`
- 职责：检索教材章节、课程内容、知识点和考点证据。

## 2. 输入
- authorization, intent, keyword, input_text, rag_strategy

## 3. 输出
- matched_results, retrieval_meta

## 4. 工作流
1. 理解 Leader 分配的任务目标。
2. 读取必要上下文和证据。
3. 产出结构化 Markdown 结果，方便后台展示和后续落库。

## 5. 边界与约束
- 不伪造教材事实；证据不足时说明不确定性。
- 输出优先使用 Markdown，便于前端直接渲染。

## 6. 质量标准
- 结构清晰。
- 可追溯到输入主题或检索证据。
- 便于学生复习和教师调整。
