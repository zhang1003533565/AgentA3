from typing import Optional

from app.model_providers.runtime_config import LlmRuntimeConfig, resolve_llm_config
from app.rag.embeddings.scaffolded import ScaffoldedEmbeddingProvider
from app.utils.text_utils import normalize_base_url


class OpenAIEmbeddingProvider(ScaffoldedEmbeddingProvider):
    name = "openai"
    status = "disabled"
    dependency = "langchain-openai"
    dependency_import = "langchain_openai"
    required_config = []
    optional_config = []
    dimension = "dense"
    description = "OpenAI-compatible embedding provider is disabled until callers pass explicit provider config."

    def __init__(self, config: Optional[LlmRuntimeConfig] = None) -> None:
        self.config = config
        self.client = None
        if config is None:
            return
        try:
            from langchain_openai import OpenAIEmbeddings
        except Exception as exc:
            raise RuntimeError(f"缺少 langchain_openai 依赖: {exc}") from exc

        runtime_config = resolve_llm_config(config)
        if not runtime_config.base_url:
            raise RuntimeError("Embedding Base URL 未配置：缺少 X-AI-Base-Url")
        if not runtime_config.api_key:
            raise RuntimeError("Embedding API Key 未配置：缺少 X-AI-Api-Key")
        if not runtime_config.model:
            raise RuntimeError("Embedding 模型未配置：缺少 X-AI-Model")

        self.config = runtime_config
        self.status = "implemented"
        self.description = "OpenAI-compatible embedding provider configured by caller request headers."
        self.client = OpenAIEmbeddings(
            api_key=runtime_config.api_key,
            base_url=normalize_base_url(runtime_config.base_url),
            model=runtime_config.model,
            timeout=60,
            max_retries=1,
        )

    def embed_text(self, text: str):
        if self.client is None:
            raise RuntimeError("OpenAI embedding 已禁用：请先在知识库页面选择已测试成功的向量模型。")
        values = self.client.embed_query(text or "")
        return self._dense_to_embedding_vector(values)

    def health(self):
        data = super().health()
        if self.client is not None and self.config is not None:
            data.update({
                "configured": True,
                "provider": self.name,
                "status": self.status,
                "model": self.config.model,
                "baseUrl": self.config.base_url,
            })
        return data
