from typing import Optional

from app.model_providers.runtime_config import LlmRuntimeConfig
from app.rag.embeddings.openai import OpenAIEmbeddingProvider


class DashScopeEmbeddingProvider(OpenAIEmbeddingProvider):
    name = "dashscope"
    description = "DashScope/Qwen embedding provider using OpenAI-compatible embeddings API."

    def __init__(self, config: Optional[LlmRuntimeConfig] = None) -> None:
        super().__init__(config=config)
        self.name = "dashscope"
