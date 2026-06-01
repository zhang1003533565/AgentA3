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

ai-server 不再从环境变量选择 embedding provider 或读取外部服务密钥。当前默认只启用 `local_lexical`。

除 `local_lexical` 外，其他 provider 当前是 scaffold：`health()` 可查看状态，实际 `embed_text()` 会明确报错，避免误以为已经完成真实 embedding。
