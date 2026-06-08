# 元数据过滤 RAG

## 作用

先按 metadata 缩小检索范围，再对候选片段进行重排。适用于多个知识库共用同一个向量库时，按 `knowledgeBaseId`、来源文档、标签或业务场景进行隔离召回。

## 适用场景

- 只在某一个或多个知识库内检索。
- 只检索指定标签，如“后勤”“教务”“课程资料”。
- 防止无关知识库的 chunk 混入召回结果。

## 输入参数

从 `RagQuery.metadata` 读取：

- `knowledgeBaseIds`
- `source` / `sources`
- `tags`
- `scene` / `scenes`
