# 用户画像证据提交协议

版本：`campus-profile-evidence-v1`

## 设计目标

画像证据只负责记录“发生了什么”，不直接修改雷达图分数。聊天、会议、答题、点击、资料变更等行为会实时写入候选证据池，雷达图由后端定时汇总任务统一更新。

协议参考三类通用结构：

- xAPI：用 `actor + verb/action + object + result + context + timestamp` 描述学习行为。
- 1EdTech Caliper：用 `actor + action + object + eventTime` 描述学习分析事件。
- W3C PROV：用 `entity + activity + agent` 保留数据来源、生成活动和责任主体。

## 提交端职责

提交端可以是 Leader、会议模块、答题模块、资源点击模块或用户资料模块。提交端只需要尽量提供清晰证据，不需要决定画像是否更新。

提交端必须做到：

- 只提交真实发生或明确表达的行为，不提交空泛猜测。
- `dimensionKey` 必须来自后台 7 个画像维度。
- `sourceType` 必须说明来源，如 `chat`、`meeting`、`exam`、`click`、`profile`。
- `evidence` 必须是具体证据摘要。
- `confidence` 可以不传；后端会自动计算。
- `suggestedDelta` 只是建议变化，最终更新由定时汇总任务决定。

## JSON 格式

```json
{
  "dimensionKey": "weak_points",
  "sourceType": "chat",
  "sourceId": "app-ai-1782549317335-zgris0",
  "action": "expressed",
  "objectType": "conversation",
  "objectId": "app-ai-1782549317335-zgris0",
  "objectName": "循环队列判满条件",
  "result": "用户继续追问例子",
  "occurredAt": "2026-06-27T15:30:00",
  "evidence": "用户明确说循环队列 front/rear 判满条件不懂，并继续追问例子。",
  "direction": "weakness",
  "suggestedDelta": -2,
  "confidence": 0.72,
  "evidenceTags": ["循环队列", "front/rear", "判满条件"],
  "metadata": {
    "agentName": "leader_agent",
    "capturePolicy": "only_explicit_chat_signal"
  }
}
```

## 字段说明

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `dimensionKey` | 是 | 画像维度 key，例如 `weak_points`、`learning_goal`。 |
| `sourceType` | 是 | 证据来源类型，用于计算来源可靠性。 |
| `sourceId` | 否 | 来源业务 ID，例如会话 ID、会议 ID、题目 ID。 |
| `action` | 否 | 行为动作，例如 `expressed`、`answered`、`analyzed`、`interacted`。 |
| `objectType` | 否 | 对象类型，例如 `conversation`、`meeting`、`question`、`resource`。 |
| `objectId` | 否 | 对象 ID，用于重复证据统计。 |
| `objectName` | 否 | 对象名称或知识点名称。 |
| `result` | 否 | 行为结果摘要，例如正确率、完成状态、会议结论。 |
| `occurredAt` | 否 | 行为实际发生时间，缺失时使用提交时间。 |
| `evidence` | 是 | 具体证据内容。 |
| `direction` | 否 | `increase`、`positive`、`weakness`、`decrease` 等。 |
| `suggestedDelta` | 否 | 建议变化分，通常在 `-3` 到 `3`。 |
| `confidence` | 否 | 提交端建议置信度，后端会保守融合。 |
| `evidenceTags` | 否 | 知识点、资源类型、任务标签。 |
| `metadata` | 否 | 扩展上下文。 |

## 后端置信度计算

后端最终写入的 `confidence` 由 5 项组成：

```text
confidence =
  来源可靠性 * 35%
  + 表达明确度 * 25%
  + 重复出现度 * 20%
  + 时间新鲜度 * 10%
  + 与历史一致性 * 10%
```

来源基础权重：

| sourceType | 基础权重 | 说明 |
| --- | ---: | --- |
| `profile` / `schedule` / `grade` | 0.90 | 用户资料、课表、成绩等事实来源。 |
| `exam` / `wrong_question` / `task` | 0.85 | 答题结果、错题、任务完成记录。 |
| `meeting` | 0.75 | 会议总结、成员分析、会议任务。 |
| `chat` | 0.70 | 用户在聊天中的明确表达。 |
| `click` / `resource` / `favorite` | 0.55 | 点击、浏览、收藏等弱行为。 |
| `ai` / `inference` | 0.40 | AI 单次推断或含糊证据。 |

后端会把拆解写到 `confidenceBreakdown`，包括：

- `sourceReliability`
- `expressionClarity`
- `repetition`
- `recency`
- `historyConsistency`
- `computedConfidence`
- `submittedConfidence`
- `finalConfidence`
- `reasons`

## 自动采集策略

| 来源 | 触发 | 影响维度 |
| --- | --- | --- |
| Leader 聊天 | 用户明确说不会、要备考，或在文件版/图片版/文档版/图解版之间做选择 | `weak_points`、`learning_goal`、`resource_preference` |
| 会议智能体 | 会议总结、成员分析、资源推荐生成后 | `learning_progress`、`weak_points`、`resource_preference` |
| 答题模块 | 正确率、错题、练习提交、任务完成 | `weak_points`、`ability_performance`、`learning_progress` |
| 点击资源 | 点击、收藏、下载同类资源多次出现 | `campus_behavior`、`resource_preference` |
| 用户资料 | 专业、课表、成绩、明确填写变化 | `course_background`、`learning_goal` |

## 输出形式选择记忆

当一个任务既可以做成图片，也可以做成文件时，Leader 应按下面策略处理：

1. 没有稳定偏好时，先问用户：“要图片形式还是文件形式？”
2. 用户选择“文件版/文档版/PDF/Word/PPT/表格”时，提交 `resource_preference` 证据，`objectName` 设为 `文件/文档`。
3. 用户选择“图片版/图解版/配图/海报”时，提交 `resource_preference` 证据，`objectName` 设为 `图片/图解`。
4. 后端会在 `profileSnapshot.outputPreferenceHints` 中返回最近 90 天的输出偏好统计。
5. 同类任务如果有中高置信偏好，Leader 默认按偏好推送。
6. 如果默认推送了文件，回答结束时提示“还需要图片形式也可以继续生成”；反过来也一样。

选择文件版示例：

```json
{
  "dimensionKey": "resource_preference",
  "sourceType": "chat",
  "sourceId": "app-ai-1782549317335-zgris0",
  "action": "expressed",
  "objectType": "conversation",
  "objectName": "文件/文档",
  "evidence": "用户在 Leader 对话中选择文件或文档形式：要文件版。",
  "direction": "increase",
  "suggestedDelta": 3,
  "evidenceTags": ["文件", "文档", "输出形式偏好"]
}
```

## 更新边界

- 单条证据只进入 `candidate`，不直接更新雷达图。
- 定时汇总任务按用户和维度聚合候选证据。
- 低置信证据继续留在候选池。
- 正负证据接近抵消时不更新分数。
- 已参与更新的证据标记为 `applied`。
- 每次画像分数变化都必须能追溯到证据来源、置信度拆解和实际 `appliedDelta`。
