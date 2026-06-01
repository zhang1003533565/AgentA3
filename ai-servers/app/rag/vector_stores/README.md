# Vector Stores

统一管理 RAG 向量/索引存储后端。

当前默认后端：

- `local_jsonl`：读取和写入 `knowledge_base/raw/.index/local_chunks.jsonl`。

已预留骨架：

- `faiss`：本地向量索引骨架，待接 `faiss-cpu`。
- `milvus`：远程向量库骨架，待接 `pymilvus`。
- `elasticsearch`：混合检索骨架，待接 `elasticsearch`。
- `pgvector`：PostgreSQL 向量检索骨架，待接 `psycopg`。

上层 retriever 不直接依赖具体存储实现，而是通过 `build_vector_store()` 获取后端实例。

## Backend Config

ai-server 不再从环境变量选择 vector store 或读取连接信息。默认只启用 `local_jsonl`。

除 `local_jsonl` 外，其他后端当前禁用：`health()` 可查看状态，`upsert_documents()` 会明确拒绝写入，避免误以为已经完成真实入库。
