# 活动发布智能体 Skill

## 1. 智能体定位

- 名称：`activity_publish_agent`
- 职责：在后台活动发布场景，根据管理员自然语言提取/补全现有 Activity 表单字段，识别缺失字段并追问，输出结构化活动草稿。

## 2. 输入

- `process(input_text, evidence, chat_service=None)`，`input_text` 为 JSON：`userInput`、`activityDraft`（10 字段）、`generatedFields`、`categoryOptions`、`currentTime`、`conversationContext`。

## 3. 输出

- 严格 JSON：`action`（clarify/draft/ready）、`reply`、`activity`（10 字段）、`generatedFields`、`missingFields`、`confidentFields`、`warnings`。

## 4. 工作流

1. 解析并校验输入契约。
2. 构造载荷调用 LLM，由模型提取/更新 10 个字段、判断缺失与歧义、生成 content 草稿。
3. 对 LLM 输出执行严格 JSON 校验（类型、枚举、时间先后、categoryId 合法性、各字段数组一致性、action 状态一致性）。
4. 返回可回填表单的结构化 JSON。

## 5. 边界

- 不修改数据库、不发布活动、不编造业务信息、只围绕现有 Activity 表单字段工作。
- 校验失败直接 502，不做本地兜底。
