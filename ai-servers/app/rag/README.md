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
- `graph_stores/`：GraphRAG 图谱存储抽象，默认本地图谱抽取，可选 Neo4j。
- `routers/`：自适应策略路由。
- `generation/`：上下文构建和答案生成辅助。
- `indexing/`：知识入库辅助。
- `vector_stores/`：向量/索引存储抽象，默认本地 JSONL，可切换 Docker Milvus，后续可扩展 FAISS、ES、pgvector。
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

当前实现是轻量本地版：默认使用 Markdown/TXT/CSV/JSON/HTML/PDF/图片文件、Java 后端接口、词法向量检索和规则化路由。`RagEngine.run()` 会统一补齐本地答案合成、trace 和未知策略回退。后续可以在对应目录替换为 Milvus、ES、Neo4j、真实 SQL 执行器或多模态模型。

## Runtime API

- `GET /internal/rag/strategies`：列出 16 种策略。
- `GET /internal/rag/strategies/{strategy_name}`：查看单个策略说明。
- `GET /internal/rag/capabilities`：查看检索、索引、评估、结构化知识和智能体能力目录。
- `GET /internal/rag/framework`：查看模型服务商、Embedding、向量库、图谱库、索引和运行环境目录。
- `GET /internal/rag/agents`：查看多智能体职责、skill、prompt、contract 和 tools 文件。
- `GET /internal/rag/agents/{agent_name}`：查看单个智能体详情。
- `POST /internal/rag/query`：按 `ragStrategy` 执行查询，并返回答案、文档、trace 和 metadata。
- `POST /internal/rag/documents`：写入知识库、解析多模态内容、切分并写入当前 `RAG_VECTOR_STORE_BACKEND`。
- `POST /internal/rag/evaluate`：返回 hitRate、MRR、contextRelevance、faithfulness 等轻量评估指标。

## Knowledge Base

知识库搭建说明见 `app/rag/KNOWLEDGE_BASE_GUIDE.md`。
