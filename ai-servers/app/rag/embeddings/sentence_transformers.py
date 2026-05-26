import os

from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class SentenceTransformersEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "sentence_transformers"
    status = "implemented_optional"
    dependency = "sentence-transformers"
    dependency_import = "sentence_transformers"
    required_env = ["RAG_SENTENCE_TRANSFORMERS_MODEL"]
    optional_env = ["RAG_SENTENCE_TRANSFORMERS_DEVICE", "RAG_SENTENCE_TRANSFORMERS_NORMALIZE"]
    dimension = "dense"
    description = "sentence-transformers embedding provider scaffold for local dense retrieval."

    def embed_text(self, text: str):
        self._ensure_ready()
        from sentence_transformers import SentenceTransformer

        model_name = os.getenv("RAG_SENTENCE_TRANSFORMERS_MODEL")
        device = os.getenv("RAG_SENTENCE_TRANSFORMERS_DEVICE") or None
        normalize = os.getenv("RAG_SENTENCE_TRANSFORMERS_NORMALIZE", "true").lower() == "true"
        model = SentenceTransformer(model_name, device=device)
        vector = model.encode(text or "", normalize_embeddings=normalize)
        return self._dense_to_embedding_vector([float(value) for value in vector])

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"sentence-transformers embedding provider is not configured: {health}")
