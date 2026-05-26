# Multi Agents

该目录是当前 AI Server 唯一维护的多智能体目录。旧的 planner/retriever/answer/critic/memory/sql/graph/tool/orchestrator 目录已移除，当前仅保留学习资源生产相关的 7 个智能体。

| Agent | 中文名称 | 职责 |
| --- | --- | --- |
| `leader_agent` | Leader 智能体 | 统一路由、任务拆解、会话记忆、最终回答兜底 |
| `mind_map_agent` | 思维导图智能体 | 根据主题和证据生成 Markdown/Mermaid 思维导图 |
| `md_knowledge_agent` | MD 知识点提取智能体 | 从 Markdown/文本中提取标题、列表和知识点 |
| `textbook_knowledge_agent` | 教材知识点智能体 | 调用 Java 后端、本地知识库、向量/图谱/SQL 检索教材相关证据 |
| `textbook_question_bank_agent` | 教材题库智能体 | 基于知识点生成练习题、简答题和参考答案要点 |
| `ppt_agent` | PPT 智能体 | 基于主题和证据生成 PPT 页结构、大纲和页面建议 |
| `image_agent` | 图片智能体 | 基于知识点生成教学配图/封面图提示词 |

每个智能体文件夹都包含：`agent.py`、`skill.md`、`prompt.md`、`contract.md`、`tools.yaml`，方便后续单独调整 skill。
