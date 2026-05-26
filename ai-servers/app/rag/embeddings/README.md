# Embeddings

统一管理 RAG embedding provider。

当前可用后端：

- `local_lexical`：本地稀疏词法 embedding，无外部依赖，默认启用。

已预留骨架：

- `openai`
- `dashscope`
- `bge`
- `sentence_transformers`

## Provider Config

- `RAG_EMBEDDING_PROVIDER=local_lexical|openai|dashscope|bge|sentence_transformers`

除 `local_lexical` 外，其他 provider 当前是 scaffold：`health()` 可查看缺失配置，实际 `embed_text()` 会明确报错，避免误以为已经完成真实 embedding。
