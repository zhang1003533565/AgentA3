# Multi Agents

该目录是当前 AI Server 唯一维护的多智能体目录。旧的 planner/retriever/answer/critic/memory/sql/graph/tool/orchestrator 目录已移除，当前仅保留学习资源生产相关的 7 个智能体。

| Agent | 中文名称 | 职责 |
| --- | --- | --- |
| `leader_agent` | Leader 智能体 | 统一路由、任务拆解、会话记忆、基于 LLM 的直接回答 |
| `mind_map_agent` | 思维导图智能体 | 根据主题和证据生成 Markdown/Mermaid 思维导图 |
| `md_knowledge_agent` | MD 知识点提取智能体 | 从 Markdown/文本中提取标题、列表和知识点 |
| `textbook_knowledge_agent` | 教材知识点智能体 | 调用 Java 后端、本地知识库、向量/图谱/SQL 检索教材相关证据 |
| `textbook_question_bank_agent` | 教材题库智能体 | 基于知识点生成练习题、简答题和参考答案要点 |
| `ppt_agent` | PPT 智能体 | 基于主题和证据生成 PPT 页结构、大纲和页面建议 |
| `image_agent` | 图片智能体 | 基于知识点生成教学配图/封面图提示词 |

每个智能体文件夹都包含：`agent.py`、`skill.md`、`prompt.md`、`contract.md`、`tools.yaml`，方便后续单独调整 skill。

## 调用参数

对话接口 `POST /internal/chat`、流式接口 `POST /internal/chat/stream` 和测试接口 `POST /internal/rag/query` 统一使用 `agentName` 指定智能体。

| 功能 | `agentName` | 默认 `ragStrategy` | 主要输入 |
| --- | --- | --- | --- |
| Leader 自动分发 | 留空或 `leader_agent` | 不手动传，由 Leader 判断 | `input` |
| 思维导图 | `mind_map_agent` | `multi_agent_rag` | `input` 为主题/要求 |
| MD 知识点提取 | `md_knowledge_agent` | `semantic_chunking` | `input` 为 Markdown 文本 |
| 教材知识点 | `textbook_knowledge_agent` | `hybrid_search` | `input` 为章节或知识点问题 |
| 教材题库 | `textbook_question_bank_agent` | `multi_agent_rag` | `input` 为出题范围 |
| PPT | `ppt_agent` | `multi_agent_rag` | `input` 为课件主题 |
| 图片 | `image_agent` | `multimodal_rag` | `input` 为配图主题 |

请求示例：

```json
{
  "sessionId": "course-001",
  "agentName": "ppt_agent",
  "input": "根据数据结构中栈与队列的知识点生成 6 页课件大纲"
}
```

`ragStrategy` 可覆盖默认策略，例如教材知识点智能体可指定 `graph_rag` 或 `parent_child`。显式调用专业智能体时，输出由该智能体调用 LLM 生成；不传时由 Leader 调用 LLM 判断问题文字并分配。

Leader 意图识别和所有智能体生成都依赖 Java 后端从数据库 `system_config` 转发的 `ai.service.base-url`、`ai.service.api-key`、`ai.service.model`。这些配置缺失、模型不可用或返回格式错误时，AI Server 会直接报错，不会本地兜底生成假成功结果。
