from typing import Optional

from app.rag.embeddings.base import BaseEmbeddingProvider
from app.rag.embeddings.bge import BgeEmbeddingProvider
from app.rag.embeddings.dashscope import DashScopeEmbeddingProvider
from app.rag.embeddings.local_lexical import LocalLexicalEmbeddingProvider
from app.rag.embeddings.openai import OpenAIEmbeddingProvider
from app.rag.embeddings.sentence_transformers import SentenceTransformersEmbeddingProvider


def build_embedding_provider(provider: Optional[str] = None) -> BaseEmbeddingProvider:
    selected = (provider or "local_lexical").lower()
    if selected in {"local", "lexical", "local_lexical"}:
        return LocalLexicalEmbeddingProvider()
    if selected == "openai":
        return OpenAIEmbeddingProvider()
    if selected in {"dashscope", "qwen"}:
        return DashScopeEmbeddingProvider()
    if selected == "bge":
        return BgeEmbeddingProvider()
    if selected in {"sentence_transformers", "sentence-transformers", "st"}:
        return SentenceTransformersEmbeddingProvider()
    raise ValueError(f"Unsupported RAG embedding provider: {selected}")
