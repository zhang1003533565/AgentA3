from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class BgeEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "bge"
    dependency = "sentence-transformers"
    required_env = ["RAG_BGE_MODEL_NAME"]
    optional_env = ["RAG_BGE_DEVICE", "RAG_BGE_NORMALIZE"]
    dimension = "dense"
    description = "BGE embedding provider scaffold for local Chinese/English retrieval models."
