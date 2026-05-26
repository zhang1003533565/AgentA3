from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class OpenAIEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "openai"
    dependency = "langchain-openai"
    required_env = ["OPENAI_API_KEY"]
    optional_env = ["RAG_OPENAI_EMBEDDING_MODEL", "OPENAI_BASE_URL"]
    dimension = "dense"
    description = "OpenAI embedding provider scaffold for future dense vector indexing."
