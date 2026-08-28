# Multi Agents

该目录是当前 AI Server 唯一维护的多智能体目录。旧的 planner/retriever/answer/critic/memory/sql/graph/tool/orchestrator 目录已移除，当前保留学习资源生产、会议处理和 PPT 工作流相关智能体。

| Agent | 中文名称 | 职责 |
| --- | --- | --- |
| `leader_agent` | Leader 智能体 | 统一路由、任务拆解、会话记忆、基于 LLM 的直接回答 |
| `architecture_prompt_agent` | 图表架构图提示词智能体 | 根据系统说明、模块依赖和数据流生成可交给架构图智能体使用的提示词 |
| `diagram_mind_map_agent` | 图表思维导图智能体 | 根据知识点材料和证据生成 Mermaid 思维导图 |
| `diagram_flowchart_agent` | 图表流程图智能体 | 根据步骤、算法或业务过程生成 Mermaid 流程图 |
| `diagram_activity_agent` | 图表活动图智能体 | 根据角色协作、任务执行和活动顺序生成 Mermaid 活动图 |
| `diagram_architecture_agent` | 图表架构图智能体 | 根据系统模块、服务依赖和数据流生成 Mermaid 架构结构，不直接生图 |
| `textbook_knowledge_agent` | 教材知识点智能体 | 统一处理教材、Markdown 教材文本和知识点整理；第三方知识库证据由 Java 后端接入后作为输入传入 |
| `textbook_question_single_choice_agent` | 选择题智能体 | 基于知识点生成单选题、选项、答案和解析 |
| `textbook_question_fill_blank_agent` | 填空题智能体 | 基于知识点生成填空题、答案和解析 |
| `textbook_question_true_false_agent` | 判断题智能体 | 基于知识点生成判断题、答案和解析 |
| `textbook_question_multiple_choice_agent` | 多选题智能体 | 基于知识点生成多选题、答案和解析 |
| `textbook_question_short_answer_agent` | 简答题智能体 | 基于知识点生成简答题、答案要点和评分参考 |
| `textbook_question_calculation_agent` | 计算题智能体 | 基于知识点生成计算题、解题步骤和答案 |
| `textbook_question_programming_agent` | 编程题智能体 | 基于知识点生成编程题、参考思路和测试用例 |
| `ppt_outline_agent` | PPT 大纲智能体 | 基于主题和证据生成 PPT 页结构、大纲和页面建议 |
| `ppt_structure_agent` | Presenton 结构智能体 | 按 Presenton 布局组件 Schema 为每页选择 layoutId |
| `ppt_review_agent` | PPT 审查智能体 | 审查 PPT 内容和布局，输出问题清单、修改建议和置信度 |
| `ppt_image_agent` | PPT 配图提示词智能体 | 为 PPT 封面、插图和示意图生成图片提示词，不直接生图 |
| `ppt_to_docx_agent` | PPT 转 DOCX 智能体 | 将 PPTX 文件转换为 DOCX，按幻灯片顺序重排内容并保留图片 |

每个智能体文件夹都包含：`agent.py`、`skill.md`、`prompt.md`、`contract.md`、`tools.yaml`，方便后续单独调整 skill。

## 调用参数

对话接口 `POST /internal/chat`、流式接口 `POST /internal/chat/stream` 和测试接口 `POST /internal/rag/query` 统一使用 `agentName` 指定智能体。

| 功能 | `agentName` | 执行边界 | 主要输入 |
| --- | --- | --- | --- |
| Leader 自动分发 | 留空或 `leader_agent` | Java 后端负责第三方知识库接入 | `input` |
| 架构图提示词 | `architecture_prompt_agent` | 直接处理输入上下文 | `input` 为系统说明、模块依赖或数据流材料 |
| 思维导图图片 | `generate_mind_map_image_tool` | 工具编排 | 工具内部调用 `mind_map_agent` 生成提示词，再调用唯一图片入口 |
| 流程图图片 | `generate_flowchart_image_tool` | 工具编排 | 工具内部调用 `diagram_flowchart_prompt_agent` 生成提示词，再调用唯一图片入口 |
| 架构图图片 | `generate_architecture_image_tool` | 工具编排 | 工具内部调用 `architecture_prompt_agent` 生成提示词，再调用唯一图片入口 |
| 知识图谱图片 | `generate_knowledge_graph_image_tool` | 工具编排 | 工具内部调用 `knowledge_graph_prompt_agent` 生成提示词，再调用唯一图片入口 |
| 教材知识点 | `textbook_knowledge_agent` | Java 后端负责第三方知识库接入 | `input` 为章节、知识点问题或 Markdown 教材文本 |
| 选择题 | `textbook_question_single_choice_agent` | 直接处理输入上下文 | `input` 为出题范围 |
| 填空题 | `textbook_question_fill_blank_agent` | 直接处理输入上下文 | `input` 为出题范围 |
| 判断题 | `textbook_question_true_false_agent` | 直接处理输入上下文 | `input` 为出题范围 |
| 多选题 | `textbook_question_multiple_choice_agent` | 直接处理输入上下文 | `input` 为出题范围 |
| 简答题 | `textbook_question_short_answer_agent` | 直接处理输入上下文 | `input` 为出题范围 |
| 计算题 | `textbook_question_calculation_agent` | 直接处理输入上下文 | `input` 为出题范围 |
| 编程题 | `textbook_question_programming_agent` | 直接处理输入上下文 | `input` 为出题范围 |
| PPT 大纲 | `ppt_outline_agent` | 直接处理输入上下文 | `input` 为课件主题 |
| PPT 结构选版 | `ppt_structure_agent` | Presenton 模板结构选择 | `input` 为模板布局 Schema 和 PPT 大纲 JSON |
| PPT 审查 | `ppt_review_agent` | 直接处理输入上下文 | `input` 为 PPT 大纲、布局或页面内容 |
| PPT 图片 | `generate_ppt_image_tool` | 工具编排 | 工具内部调用 `ppt_image_agent` 生成提示词，再调用唯一图片入口 |
| PPT 转 DOCX | `ppt_to_docx_agent` | 确定性文件转换 | 上传 `.pptx` 文件后通过文档转换接口生成 `.docx` |

请求示例：

```json
{
  "sessionId": "course-001",
  "agentName": "ppt_outline_agent",
  "input": "根据数据结构中栈与队列的知识点生成 6 页课件大纲"
}
```

AI Server 已移除本地检索策略执行。显式调用专业智能体时，输出由该智能体调用 LLM 生成；不传时由 Leader 调用 LLM 判断问题文字并分配。第三方知识库的检索、入库和策略能力由 Java 后端对接外部服务。

Leader 意图识别和所有智能体生成都依赖 Java 后端从数据库 `system_config` 转发的 `ai.service.text.provider`、`ai.service.text.base-url`、`ai.service.text.api-key`、`ai.service.text.model`。这些配置缺失、模型不可用或返回格式错误时，AI Server 会直接报错，不会本地兜底生成假成功结果。
