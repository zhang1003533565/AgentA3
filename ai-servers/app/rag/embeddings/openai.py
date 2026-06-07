from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class OpenAIEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "openai"
    status = "disabled"
    dependency = "langchain-openai"
    dependency_import = "langchain_openai"
    required_config = []
    optional_config = []
    dimension = "dense"
    description = "OpenAI embedding provider is disabled until callers pass explicit provider config."

    def embed_text(self, text: str):
        raise RuntimeError("OpenAI embedding 已禁用：ai-server 不读取环境变量，请由 Java/调用方显式传入配置后再启用。")

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"OpenAI embedding provider is not configured: {health}")
