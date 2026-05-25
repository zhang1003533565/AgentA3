# Multi-Agent RAG

## 这个功能是干什么的
多个智能体协作完成复杂 RAG 流程，例如规划、检索、SQL、图谱、回答、审核分工执行。

## 需要的框架组件
- OrchestratorAgent：流程编排。
- PlannerAgent：任务规划。
- RetrieverAgent：证据召回。
- AnswerAgent：答案生成。
- CriticAgent：质量审核。

## 后续实现入口
- Runtime：`app/rag/strategies/multi_agent_rag/`
- Orchestrator：`app/multi_agents/orchestrator_agent/`
