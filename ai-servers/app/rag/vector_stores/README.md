# Vector Stores

统一管理 RAG 向量/索引存储后端。

当前默认后端：

- `milvus`：通过 `pymilvus` 连接 Docker Milvus，默认 collection 为 `smart_campus_knowledge`。
- Parent-Child 索引使用独立 collection，默认 `smart_campus_knowledge_parent_child`。

兼容/预留后端：

- `local_jsonl`：仅保留显式指定时的兼容能力，不再作为知识库默认方案。
- `faiss`：本地向量索引骨架，待接 `faiss-cpu`。
- `elasticsearch`：混合检索骨架，待接 `elasticsearch`。
- `pgvector`：PostgreSQL 向量检索骨架，待接 `psycopg`。

上层 retriever 不直接依赖具体存储实现，而是通过 `build_vector_store()` 获取后端实例。

## Backend Config

默认从 `RAG_VECTOR_STORE_BACKEND` 读取后端，未配置时使用 `milvus`。`start-ai-server.sh` 会在 Milvus 后端下自动启动 `ai-servers/docker-compose.yml`。

常用配置：

- `RAG_MILVUS_URI=http://localhost:19530`
- `RAG_MILVUS_COLLECTION=smart_campus_knowledge`
- `RAG_MILVUS_PARENT_CHILD_COLLECTION=smart_campus_knowledge_parent_child`
