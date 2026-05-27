# Multi Agents

该目录是当前 AI Server 唯一维护的多智能体目录。旧的 planner/retriever/answer/critic/memory/sql/graph/tool/orchestrator 目录已移除，当前仅保留学习资源生产相关的 12 个智能体。

| Agent | 中文名称 | 职责 |
| --- | --- | --- |
| `leader_agent` | Leader 智能体 | 统一路由、任务拆解、会话记忆、基于 LLM 的直接回答 |
| `mind_map_agent` | 思维导图智能体 | 根据主题和证据生成 Markdown/Mermaid 思维导图 |
| `textbook_knowledge_agent` | 教材知识点智能体 | 统一处理教材、Markdown 教材文本和知识点整理，并调用 Java 后端、本地知识库、向量/图谱/SQL 检索相关证据 |
| `textbook_question_single_choice_agent` | 选择题智能体 | 基于知识点生成单选题、选项、答案和解析 |
| `textbook_question_fill_blank_agent` | 填空题智能体 | 基于知识点生成填空题、答案和解析 |
| `textbook_question_true_false_agent` | 判断题智能体 | 基于知识点生成判断题、答案和解析 |
| `textbook_question_multiple_choice_agent` | 多选题智能体 | 基于知识点生成多选题、答案和解析 |
| `textbook_question_short_answer_agent` | 简答题智能体 | 基于知识点生成简答题、答案要点和评分参考 |
| `textbook_question_calculation_agent` | 计算题智能体 | 基于知识点生成计算题、解题步骤和答案 |
| `textbook_question_programming_agent` | 编程题智能体 | 基于知识点生成编程题、参考思路和测试用例 |
| `ppt_agent` | PPT 智能体 | 基于主题和证据生成 PPT 页结构、大纲和页面建议 |
| `image_agent` | 图片智能体 | 基于知识点生成教学配图/封面图提示词 |

每个智能体文件夹都包含：`agent.py`、`skill.md`、`prompt.md`、`contract.md`、`tools.yaml`，方便后续单独调整 skill。

## 调用参数

对话接口 `POST /internal/chat`、流式接口 `POST /internal/chat/stream` 和测试接口 `POST /internal/rag/query` 统一使用 `agentName` 指定智能体。

| 功能 | `agentName` | 默认 `ragStrategy` | 主要输入 |
| --- | --- | --- | --- |
| Leader 自动分发 | 留空或 `leader_agent` | 不手动传，由 Leader 判断 | `input` |
| 思维导图 | `mind_map_agent` | `multi_agent_rag` | `input` 为主题/要求 |
| 教材知识点 | `textbook_knowledge_agent` | `hybrid_search` | `input` 为章节、知识点问题或 Markdown 教材文本 |
| 选择题 | `textbook_question_single_choice_agent` | `multi_agent_rag` | `input` 为出题范围 |
| 填空题 | `textbook_question_fill_blank_agent` | `multi_agent_rag` | `input` 为出题范围 |
| 判断题 | `textbook_question_true_false_agent` | `multi_agent_rag` | `input` 为出题范围 |
| 多选题 | `textbook_question_multiple_choice_agent` | `multi_agent_rag` | `input` 为出题范围 |
| 简答题 | `textbook_question_short_answer_agent` | `multi_agent_rag` | `input` 为出题范围 |
| 计算题 | `textbook_question_calculation_agent` | `multi_agent_rag` | `input` 为出题范围 |
| 编程题 | `textbook_question_programming_agent` | `multi_agent_rag` | `input` 为出题范围 |
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

Leader 意图识别和所有智能体生成都依赖 Java 后端从数据库 `system_config` 转发的 `ai.service.text.provider`、`ai.service.text.base-url`、`ai.service.text.api-key`、`ai.service.text.model`。这些配置缺失、模型不可用或返回格式错误时，AI Server 会直接报错，不会本地兜底生成假成功结果。
