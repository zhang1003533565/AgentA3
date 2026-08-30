# Contract

- agentName: `activity_publish_agent`
- 用途: 与后台活动发布人员多轮对话，提取并补全现有 Activity 表单 10 个字段，输出可回填表单的结构化数据

## Input

`process(input_text, evidence, chat_service=None)`，其中 `input_text` 为 JSON 字符串：

- `userInput` (string, 必需): 管理员本轮输入
- `activityDraft` (object, 必需): 恰好 10 个字段的当前草稿，null 表示未确认
- `generatedFields` (string[], 可选): 上一轮 AI 生成未确认的字段（仅 title/content）
- `categoryOptions` (`{id:int, name:string}[]`, 可选): 现有活动分类候选
- `currentTime` (string, 可选): 当前时间 ISO 8601，用于相对时间解析
- `conversationContext` (object, 可选): 最近对话摘要，仅用于指代理解

Agent 不保存草稿状态；`activityDraft` 是唯一状态输入。

## Output

顶层 7 键：`action`、`reply`、`activity`、`generatedFields`、`missingFields`、`confidentFields`、`warnings`

- `action`: `clarify`（必填字段缺失或有歧义）/ `draft`（必填齐全但有未确认的 AI 生成项）/ `ready`（必填齐全且无未确认项）
- `reply`: 非空字符串
- `activity`: 恰好 10 个键（title/organizerName/coverImage/categoryId/maxPeople/location/startTime/endTime/signupEndTime/content），值允许 null 或对应类型
- `generatedFields`: AI 生成未确认字段，仅能包含 title/content
- `missingFields`: 仍阻塞表单的必填字段
- `confidentFields`: 已确认字段（activity 非空字段 − generatedFields）
- `warnings`: 非阻塞提示数组

## Validation

`agent.py` 内 `validate_output_contract` 执行严格校验：顶层 7 键、action 枚举、activity 恰好 10 键、字段类型、maxPeople>=1、时间格式与先后、categoryId 必须在 categoryOptions 内、missingFields/confidentFields/generatedFields 一致性、action 与状态一致性；禁止 Markdown 代码块与额外字段。校验失败抛 502，不本地兜底。

## Boundary

- 不写数据库、不创建/发布活动、不修改 Activity 表单字段。
- 不编造时间、地点、人数、分类、图片 URL 等业务信息。
- 不修改其他 Agent 或公共 AI 编排代码。
