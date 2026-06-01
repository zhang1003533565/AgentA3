# Graph Stores

统一管理 GraphRAG 图谱后端。

当前可用后端：

- `local_graph`：从本地文档/索引 chunk 中抽取轻量实体路径，无外部依赖。

可选后端：

- `neo4j`：当前禁用。ai-server 不再读取环境变量，后续需要由 Java/调用方显式传入连接配置后再启用。

## Config

ai-server 不再从环境变量选择 graph store 或读取连接信息。默认只启用 `local_graph`。
