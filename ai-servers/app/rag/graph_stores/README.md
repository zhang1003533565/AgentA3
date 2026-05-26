# Graph Stores

统一管理 GraphRAG 图谱后端。

当前可用后端：

- `local_graph`：从本地文档/索引 chunk 中抽取轻量实体路径，无外部依赖。

可选后端：

- `neo4j`：当安装 `neo4j` Python 包并配置连接信息后，可执行 Cypher 查询。

## Config

- `RAG_GRAPH_STORE_BACKEND=local_graph|neo4j`
- `RAG_NEO4J_URI`
- `RAG_NEO4J_USERNAME`
- `RAG_NEO4J_PASSWORD`
- `RAG_NEO4J_DATABASE`
- `RAG_NEO4J_SEARCH_CYPHER`
