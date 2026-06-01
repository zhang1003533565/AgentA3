from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider


class DashScopeEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "dashscope"
    status = "disabled"
    dependency = "dashscope"
    dependency_import = "dashscope"
    required_env = []
    optional_env = []
    dimension = "dense"
    description = "DashScope embedding provider is disabled until callers pass explicit provider config."

    def embed_text(self, text: str):
        raise RuntimeError("DashScope embedding 已禁用：ai-server 不读取环境变量，请由 Java/调用方显式传入配置后再启用。")

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"DashScope embedding provider is not configured: {health}")
