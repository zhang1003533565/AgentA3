# Multi Agents

该目录是运行时多智能体框架，每个智能体独立一个文件夹，代码、skill、prompt、工具声明和输入输出约定放在一起，方便后续单独调整。

| Agent | 作用 | Runtime |
| --- | --- | --- |
| `orchestrator_agent` | 编排多智能体流程和回退链路 | `orchestrator_agent/agent.py` |
| `planner_agent` | 判断意图、决定是否检索和推荐链路 | `planner_agent/agent.py` |
| `retriever_agent` | 调用 Java 后端、本地知识库、向量/图谱/SQL 检索 | `retriever_agent/agent.py` |
| `answer_agent` | 调用模型生成答案 | `answer_agent/agent.py` |
| `critic_agent` | 审核与精简答案 | `critic_agent/agent.py` |
| `memory_agent` | 管理会话记忆 | `memory_agent/agent.py` |
| `sql_agent` | 生成只读 SQL 和结构化查询计划 | `sql_agent/agent.py` |
| `graph_agent` | 检索实体关系和图谱证据路径 | `graph_agent/agent.py` |
| `tool_agent` | 根据任务选择可用工具 | `tool_agent/agent.py` |
