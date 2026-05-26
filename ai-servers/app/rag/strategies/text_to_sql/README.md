# Text-to-SQL

## 这个功能是干什么的
把用户自然语言转换成受控只读 SQL，用于查询结构化业务数据。

## 需要的框架组件
- SchemaRegistry：管理可查询 schema。
- SqlGenerator：生成 SQL。
- SqlGuard：校验 SQL 安全性。
- SqlExecutor：执行只读查询。

## 后续实现入口
- Runtime：`app/rag/strategies/text_to_sql/`
- Agent：`app/multi_agents/textbook_knowledge_agent/`
