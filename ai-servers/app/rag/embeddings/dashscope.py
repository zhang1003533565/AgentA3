import os

from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class DashScopeEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "dashscope"
    status = "implemented_optional"
    dependency = "dashscope"
    dependency_import = "dashscope"
    required_env = ["DASHSCOPE_API_KEY"]
    optional_env = ["RAG_DASHSCOPE_EMBEDDING_MODEL"]
    dimension = "dense"
    description = "DashScope embedding provider scaffold for Qwen/Tongyi embedding models."

    def embed_text(self, text: str):
        self._ensure_ready()
        import dashscope

        dashscope.api_key = os.getenv("DASHSCOPE_API_KEY")
        model = os.getenv("RAG_DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v3")
        response = dashscope.TextEmbedding.call(model=model, input=text or "")
        if getattr(response, "status_code", None) != 200:
            raise RuntimeError(f"DashScope embedding failed: {response}")
        embeddings = response.output.get("embeddings", []) if hasattr(response, "output") else []
        if not embeddings:
            return {}
        return self._dense_to_embedding_vector(embeddings[0].get("embedding", []))

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"DashScope embedding provider is not configured: {health}")
