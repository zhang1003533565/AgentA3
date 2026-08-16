# Contract

## Agent
- `textbook_knowledge_agent`

## Input
- authorization, intent, keyword, input_text, context_candidates
- knowledgeSourceMode: `provided_material`、`model_generated` 或 `source_selection_required`

## Output
- answer, matched_results, java_backend_meta

## Error Handling
- 输入为空时返回可读提示。
- 无材料且用户未选择来源时，询问用户使用上传材料、知识库还是模型生成。
- 无材料且用户明确要求自行生成时，允许生成并标记为“模型生成内容”。
- 不调用本地知识库、向量库或检索策略。
