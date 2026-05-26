# Runtime RAG Framework

该目录是实际运行代码的 RAG 框架层。

- `core/`：通用数据结构、策略基类、策略目录。
- `strategies/`：16 种 RAG 方案的运行时入口。
- `query_transformers/`：Multi-Query、HyDE 等查询改写。
- `chunking/`：语义切分、父子块切分。
- `retrievers/`：Java 后端、关键词、向量、混合、图谱检索器。
- `rerankers/`：重排器。
- `evaluators/`：检索质量、答案质量评估。
- `embeddings/`：embedding provider 抽象，默认本地词法 embedding，预留 OpenAI、DashScope、BGE、sentence-transformers。
- `routers/`：自适应策略路由。
- `generation/`：上下文构建和答案生成辅助。
- `indexing/`：知识入库辅助。
- `vector_stores/`：向量/索引存储抽象，默认本地 JSONL，后续可替换 Milvus、FAISS、ES、pgvector。
- `structured/`：Text-to-SQL 等结构化知识能力。
- `observability/`：trace、指标、日志辅助。

## Strategy Runtime

16 种 RAG 方案都已接入 `app.rag.engine.rag_engine`，聊天接口通过 `ChatRequest.ragStrategy` 选择策略。

| Strategy | Category | Runtime Entry |
| --- | --- | --- |
| `naive_rag` | baseline | `strategies/naive_rag/strategy.py` |
| `multi_query_rag` | query_transform | `strategies/multi_query_rag/strategy.py` |
| `hyde` | query_transform | `strategies/hyde/strategy.py` |
| `semantic_chunking` | indexing | `strategies/semantic_chunking/strategy.py` |
| `parent_child` | indexing | `strategies/parent_child/strategy.py` |
| `hybrid_search` | retrieval | `strategies/hybrid_search/strategy.py` |
| `reranking` | ranking | `strategies/reranking/strategy.py` |
| `crag` | corrective | `strategies/crag/strategy.py` |
| `self_rag` | corrective | `strategies/self_rag/strategy.py` |
| `adaptive_rag` | routing | `strategies/adaptive_rag/strategy.py` |
| `graph_rag` | structured_knowledge | `strategies/graph_rag/strategy.py` |
| `text_to_sql` | structured_knowledge | `strategies/text_to_sql/strategy.py` |
| `agentic_rag` | agentic | `strategies/agentic_rag/strategy.py` |
| `multi_agent_rag` | agentic | `strategies/multi_agent_rag/strategy.py` |
| `multimodal_rag` | multimodal | `strategies/multimodal_rag/strategy.py` |
| `speculative_rag` | performance | `strategies/speculative_rag/strategy.py` |

当前实现是轻量本地版：默认使用 Markdown/TXT/CSV/JSON/HTML 文件、Java 后端接口、词法向量检索和规则化路由。后续可以在对应目录替换为 Milvus、ES、Neo4j、真实 SQL 执行器或多模态模型。
