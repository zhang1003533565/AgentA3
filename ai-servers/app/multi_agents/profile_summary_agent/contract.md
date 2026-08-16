# Contract

- agentName: `profile_summary_agent`
- output: `strict_profile_summary_json`
- input: 用户画像快照 JSON、维度分数、证据数量、置信度、趋势、来源摘要
- validation: Java 后端解析 JSON；字段缺失或 JSON 非法时丢弃智能体结果并使用本地规则总结

## Output Schema

```json
{
  "aiSummary": "",
  "strengthSummary": "",
  "weaknessSummary": "",
  "advantageDimensions": [],
  "gapDimensions": [],
  "improvementSuggestions": [],
  "dataStatusText": "",
  "dataSourceText": "",
  "confidenceNotes": [],
  "leaderReferenceRules": [],
  "missingInfo": []
}
```

## Boundaries

- 不能修改画像分数。
- 不能把候选证据当作已采纳证据。
- 不能把默认基线伪装成真实画像。
- 不能给用户贴负面标签。
- 当前输入优先于历史画像；画像只做 Leader 个性化参考。
