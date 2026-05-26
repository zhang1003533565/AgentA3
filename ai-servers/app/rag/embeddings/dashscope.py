from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class DashScopeEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "dashscope"
    dependency = "dashscope"
    required_env = ["DASHSCOPE_API_KEY"]
    optional_env = ["RAG_DASHSCOPE_EMBEDDING_MODEL"]
    dimension = "dense"
    description = "DashScope embedding provider scaffold for Qwen/Tongyi embedding models."
