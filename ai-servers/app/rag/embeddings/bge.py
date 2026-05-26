import os

from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class BgeEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "bge"
    status = "implemented_optional"
    dependency = "sentence-transformers"
    dependency_import = "sentence_transformers"
    required_env = ["RAG_BGE_MODEL_NAME"]
    optional_env = ["RAG_BGE_DEVICE", "RAG_BGE_NORMALIZE"]
    dimension = "dense"
    description = "BGE embedding provider scaffold for local Chinese/English retrieval models."

    def embed_text(self, text: str):
        self._ensure_ready()
        from sentence_transformers import SentenceTransformer

        model_name = os.getenv("RAG_BGE_MODEL_NAME")
        device = os.getenv("RAG_BGE_DEVICE") or None
        normalize = os.getenv("RAG_BGE_NORMALIZE", "true").lower() == "true"
        model = SentenceTransformer(model_name, device=device)
        vector = model.encode(text or "", normalize_embeddings=normalize)
        return self._dense_to_embedding_vector([float(value) for value in vector])

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"BGE embedding provider is not configured: {health}")
