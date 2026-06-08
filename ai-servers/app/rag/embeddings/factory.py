from typing import Optional

from app.model_providers.runtime_config import LlmRuntimeConfig
from app.rag.embeddings.base import BaseEmbeddingProvider
from app.rag.embeddings.bge import BgeEmbeddingProvider
from app.rag.embeddings.dashscope import DashScopeEmbeddingProvider
from app.rag.embeddings.runtime_config import get_active_embedding_config
from app.rag.embeddings.local_lexical import LocalLexicalEmbeddingProvider
from app.rag.embeddings.openai import OpenAIEmbeddingProvider
from app.rag.embeddings.sentence_transformers import SentenceTransformersEmbeddingProvider


def build_embedding_provider(provider: Optional[str] = None, config: Optional[LlmRuntimeConfig] = None) -> BaseEmbeddingProvider:
    runtime_config = config or get_active_embedding_config()
    selected = (provider or (runtime_config.normalized_provider() if runtime_config else "") or "local_lexical").lower()
    if selected in {"local", "lexical", "local_lexical"}:
        return LocalLexicalEmbeddingProvider()
    if selected == "openai":
        return OpenAIEmbeddingProvider(config=runtime_config)
    if selected in {"dashscope", "qwen", "aliyun", "tongyi"}:
        return DashScopeEmbeddingProvider(config=runtime_config)
    if selected == "bge":
        return BgeEmbeddingProvider()
    if selected in {"sentence_transformers", "sentence-transformers", "st"}:
        return SentenceTransformersEmbeddingProvider()
    raise ValueError(f"Unsupported RAG embedding provider: {selected}")
