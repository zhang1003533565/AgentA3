# Contract

- 输入：包含用户要求、目标格式、源内容和会话上下文的 JSON。
- 输出：严格 JSON 对象，`action` 只能为 `export` 或 `clarify`。
- `export` 必须提供非空 `title` 和 `content`。
- `clarify` 必须提供非空 `question`，不得伪造待转换内容。
