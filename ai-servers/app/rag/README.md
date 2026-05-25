# Runtime RAG Framework

该目录是实际运行代码的 RAG 框架层。

- `core/`：通用数据结构、策略基类、策略目录。
- `strategies/`：16 种 RAG 方案的运行时入口。
- `query_transformers/`：Multi-Query、HyDE 等查询改写。
- `chunking/`：语义切分、父子块切分。
- `retrievers/`：Java 后端、关键词、向量、混合、图谱检索器。
- `rerankers/`：重排器。
- `evaluators/`：检索质量、答案质量评估。
- `routers/`：自适应策略路由。
- `generation/`：上下文构建和答案生成辅助。
- `indexing/`：知识入库辅助。
- `structured/`：Text-to-SQL 等结构化知识能力。
- `observability/`：trace、指标、日志辅助。
