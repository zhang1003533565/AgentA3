# ai-servers

Python FastAPI + LangChain internal service for AppBackend AI chat migration.

## Structure

- `app/main.py`: app bootstrap
- `app/api/routes/chat.py`: internal chat APIs
- `app/langgraph/graph/workflow.py`: graph orchestrator
- `app/langgraph/nodes/*`: node implementations
- `app/langgraph/state.py`: graph state
- `app/services/*`: orchestration, memory, retrieval, langchain calls
- `app/model_providers/*`: runtime model provider adapters
- `app/rag/*`: runtime rag strategies and retrievers
- `app/multi_agents/*`: runtime multi-agent roles and skill files (one folder per agent)
- `app/models/schemas.py`: request/response models
- `app/__init__.py`: package bootstrap
- `app/utils/*`: text/sse helpers

AI 相关框架统一放在 `app/` 下维护，避免运行代码、skill、contract 出现两套目录。

## Start

在 `ai-servers` 目录执行：

```bash
./start-ai-server.sh
```

默认 `RAG_VECTOR_STORE_BACKEND=milvus`，脚本会先通过当前目录的 `docker-compose.yml` 自动启动 Milvus/Etcd/MinIO 向量库，再启动 FastAPI 服务。

## Endpoints

- `POST /internal/chat`
- `POST /internal/chat/stream` (SSE)
- `GET /internal/rag/strategies`
- `GET /internal/rag/strategies/{strategy_name}`
- `GET /internal/rag/capabilities`
- `GET /internal/rag/framework`：完整框架目录、provider/store 配置及文档能力覆盖
- `GET /internal/rag/agents`：多智能体与 skill/prompt/contract/tools 目录
- `GET /internal/rag/agents/{agent_name}`：单个智能体详情
- `POST /internal/rag/query`
- `POST /internal/rag/documents`：保存文档、解析、切分并写入 Docker Milvus 向量库
- `POST /internal/rag/pdf/convert`：PDF 转 Markdown zip 或 DOCX
- `POST /internal/rag/ppt/convert`：PPTX 转 DOCX，按幻灯片顺序重排并保留图片
- `GET /internal/rag/documents`
- `GET /internal/rag/vector-store/health`
- `GET /internal/rag/embedding/health`
- `GET /internal/rag/graph-store/health`
- `GET /internal/rag/text-to-sql/schema`
- `POST /internal/rag/text-to-sql/execute`
- `POST /internal/rag/evaluate`
- `GET /healthz`

`POST /internal/chat`、SSE 接口和 `POST /internal/rag/query` 都支持传入 `agentName` 指定当前多智能体之一：

`leader_agent`、`diagram_mind_map_agent`、`diagram_flowchart_agent`、`diagram_activity_agent`、`diagram_architecture_agent`、`mind_map_agent`、`textbook_knowledge_agent`、`textbook_question_single_choice_agent`、`textbook_question_fill_blank_agent`、`textbook_question_true_false_agent`、`textbook_question_multiple_choice_agent`、`textbook_question_short_answer_agent`、`textbook_question_calculation_agent`、`textbook_question_programming_agent`、`meeting_controller_agent`、`meeting_transcription_agent`、`meeting_summary_agent`、`meeting_member_analysis_agent`、`meeting_resource_recommendation_agent`、`meeting_voice_broadcast_agent`、`ppt_outline_agent`、`ppt_layout_agent`、`ppt_review_agent`、`ppt_image_agent`、`ppt_to_docx_agent`、`image_agent`。

`agentName` 留空或传 `leader_agent` 时由 Leader 先做意图识别，再决定直接回答、调用专业智能体，或调用 Text-to-SQL / Java 后端接口。Java 后端会从 `system_config` 读取 `ai.service.text.provider`、`ai.service.text.base-url`、`ai.service.text.api-key`、`ai.service.text.model`，再通过内部请求头传给 Python；配置缺失或 LLM 调用失败会直接报错，不做本地规则兜底。只有 `needRetrieval=true` 的专业智能体才需要 `ragStrategy`；`leader_agent` 不传 RAG 策略。

```json
{
  "agentName": "mind_map_agent",
  "input": "把操作系统进程调度整理成思维导图"
}
```

支持的 `ragStrategy`：

`naive_rag`、`multi_query_rag`、`hyde`、`semantic_chunking`、`parent_child`、`hybrid_search`、`reranking`、`crag`、`self_rag`、`adaptive_rag`、`graph_rag`、`text_to_sql`、`agentic_rag`、`multi_agent_rag`、`multimodal_rag`、`speculative_rag`。

## Run

```bash
cd ai-servers
./start-ai-server.sh
```

默认会启动 Docker Milvus 并使用向量库作为知识库后端。构建知识库 + 启动服务：

```bash
cd ai-servers
./start-ai-server.sh --build-kb
```

Windows PowerShell：

```powershell
cd ai-servers
.\start-ai-server.ps1 --build-kb
```

手动启动仍然可用：

```bash
cd ai-servers
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port ${PYTHON_SERVER_PORT:-8081}
```

## Environment Variables

- `PYTHON_SERVER_PORT` default `8081`
- LLM provider/base-url/api-key/model 只接受 Java 请求头转发的 `ai.service.text.*` 配置；Python 服务不再读取本地模型环境变量兜底。
- `REDIS_URL` default `redis://localhost:6379/0`
- `LLM_MEMORY_TTL_MINUTES` default `120`
- `LLM_MEMORY_MAX_MESSAGES` default `20`
- `JAVA_BACKEND_BASE_URL` default `http://localhost:8080`
- `JAVA_BACKEND_TIMEOUT_SECONDS` default `8`
- `RAG_KNOWLEDGE_BASE_DIR` default `knowledge_base/raw`
- `RAG_CHUNK_SIZE` default `800`
- `RAG_CHUNK_OVERLAP` default `120`
- `RAG_VECTOR_TOP_K` default `5`
- `RAG_EMBEDDING_PROVIDER` default `local_lexical`
- `RAG_VECTOR_STORE_BACKEND` default `milvus`
- `RAG_GRAPH_STORE_BACKEND` default `local_graph`
- `RAG_SQLITE_DB_PATH` optional, enables read-only SQLite execution for Text-to-SQL
- `RAG_PARENT_CHUNK_SIZE` default `1600`
- `RAG_PARENT_CHUNK_OVERLAP` default `160`
- `RAG_CHILD_CHUNK_SIZE` default `420`
- `RAG_CHILD_CHUNK_OVERLAP` default `80`

预留向量库配置：

- `RAG_FAISS_INDEX_DIR`
- `RAG_MILVUS_URI`
- `RAG_MILVUS_COLLECTION`
- `RAG_MILVUS_DIMENSION` default `384`
- `RAG_MILVUS_METRIC_TYPE` default `COSINE`
- `RAG_ELASTICSEARCH_URL`
- `RAG_ELASTICSEARCH_INDEX`
- `RAG_PGVECTOR_DSN`
- `RAG_PGVECTOR_TABLE`

预留 embedding 配置：

- `OPENAI_API_KEY`
- `RAG_OPENAI_EMBEDDING_MODEL`
- `DASHSCOPE_API_KEY`
- `RAG_DASHSCOPE_EMBEDDING_MODEL`
- `RAG_BGE_MODEL_NAME`
- `RAG_SENTENCE_TRANSFORMERS_MODEL`

预留图谱配置：

- `RAG_NEO4J_URI`
- `RAG_NEO4J_USERNAME`
- `RAG_NEO4J_PASSWORD`
- `RAG_NEO4J_DATABASE`
- `RAG_NEO4J_SEARCH_CYPHER`

`ai-servers` 的业务数据（食堂/档口/菜品/优惠券/课表）通过 `Authorization` 复用 Java 现有接口获取，不直接连数据库。

## RAG Runtime Notes

- `/internal/rag/query` 默认走 Leader 编排；只有路由到检索型智能体时才进入 `app.rag.engine.rag_engine`，未知 RAG 策略会回退到 `naive_rag`。
- 策略只返回证据时，会由 `local_context_synthesizer` 生成一段带来源的本地答案，方便无 LLM 环境也能调通链路。
- 本地文档 RAG 默认读取 `ai-servers/knowledge_base/raw` 下的 `.md`、`.markdown`、`.txt`、`.csv`、`.json`、`.html`、`.htm`、`.pdf` 和图片文件。
- PDF 正文抽取会优先使用可选依赖 `pypdf`；未安装时仍会保留文件元数据，不会阻塞入库。
- 默认向量库为 Docker Milvus；`local_jsonl` 仅保留显式指定时的兼容能力，不再作为知识库默认方案。

## Build Knowledge Base With Docker Milvus

在 `ai-servers` 目录启动 Docker 向量库：

```bash
docker compose up -d
```

把 Markdown、TXT、CSV、JSON、HTML、PDF 或图片文件放入 `ai-servers/knowledge_base/raw`，然后执行：

```bash
python3 scripts/build_knowledge_base.py --backend milvus
```

默认不再扫描本地 raw 目录作为检索兜底；上传和离线构建都会写入 Milvus collection。
