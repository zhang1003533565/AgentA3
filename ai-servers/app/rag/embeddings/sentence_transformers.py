from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class SentenceTransformersEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "sentence_transformers"
    status = "disabled"
    dependency = "sentence-transformers"
    dependency_import = "sentence_transformers"
    required_config = []
    optional_config = []
    dimension = "dense"
    description = "sentence-transformers embedding provider is disabled until callers pass explicit provider config."

    def embed_text(self, text: str):
        raise RuntimeError("sentence-transformers embedding 已禁用：ai-server 不读取环境变量，请由 Java/调用方显式传入配置后再启用。")

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"sentence-transformers embedding provider is not configured: {health}")
