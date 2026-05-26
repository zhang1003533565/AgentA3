# Agentic RAG

## 这个功能是干什么的
由智能体自主规划检索、工具调用、答案生成和质量检查。

## 需要的框架组件
- PlannerAgent：规划任务。
- ToolAgent：选择工具。
- TextbookKnowledgeAgent：执行检索。
- CriticAgent：审核答案。

## 后续实现入口
- Runtime：`app/rag/strategies/agentic_rag/`
- Agents：`app/multi_agents/`
