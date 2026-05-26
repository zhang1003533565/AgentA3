from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class SentenceTransformersEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "sentence_transformers"
    dependency = "sentence-transformers"
    required_env = ["RAG_SENTENCE_TRANSFORMERS_MODEL"]
    optional_env = ["RAG_SENTENCE_TRANSFORMERS_DEVICE", "RAG_SENTENCE_TRANSFORMERS_NORMALIZE"]
    dimension = "dense"
    description = "sentence-transformers embedding provider scaffold for local dense retrieval."
