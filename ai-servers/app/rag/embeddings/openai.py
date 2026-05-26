import os

from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class OpenAIEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "openai"
    status = "implemented_optional"
    dependency = "langchain-openai"
    dependency_import = "langchain_openai"
    required_env = ["OPENAI_API_KEY"]
    optional_env = ["RAG_OPENAI_EMBEDDING_MODEL", "OPENAI_BASE_URL"]
    dimension = "dense"
    description = "OpenAI embedding provider scaffold for future dense vector indexing."

    def embed_text(self, text: str):
        self._ensure_ready()
        from langchain_openai import OpenAIEmbeddings

        model = os.getenv("RAG_OPENAI_EMBEDDING_MODEL", "text-embedding-3-small")
        embeddings = OpenAIEmbeddings(
            model=model,
            api_key=os.getenv("OPENAI_API_KEY"),
            base_url=os.getenv("OPENAI_BASE_URL") or None,
        )
        return self._dense_to_embedding_vector(embeddings.embed_query(text or ""))

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"OpenAI embedding provider is not configured: {health}")
