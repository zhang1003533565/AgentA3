import tempfile
import unittest
from contextlib import contextmanager
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from app.rag.embeddings import (
    BgeEmbeddingProvider,
    DashScopeEmbeddingProvider,
    LocalLexicalEmbeddingProvider,
    OpenAIEmbeddingProvider,
    SentenceTransformersEmbeddingProvider,
    build_embedding_provider,
)
from app.rag.embeddings.runtime_config import build_embedding_runtime_config, reset_active_embedding_config, set_active_embedding_config
from app.rag.retrievers.vector import VectorRetriever


class FakeOpenAIEmbeddings:
    def __init__(self, **kwargs):
        self.kwargs = kwargs

    def embed_query(self, text):
        return [1.0, 0.5, 0.25]


@contextmanager
def fake_langchain_openai():
    module = SimpleNamespace(OpenAIEmbeddings=FakeOpenAIEmbeddings)
    with patch.dict("sys.modules", {"langchain_openai": module}):
        yield


class EmbeddingProviderTest(unittest.TestCase):
    def test_local_lexical_embedding_tokenizes_chinese_and_english(self):
        provider = LocalLexicalEmbeddingProvider()
        vector = provider.embed_text("校园卡 Card 123")

        self.assertGreater(vector.get("校", 0), 0)
        self.assertGreater(vector.get("校园", 0), 0)
        self.assertGreater(vector.get("card", 0), 0)
        self.assertGreater(vector.get("123", 0), 0)
        self.assertEqual("local_lexical", provider.health()["provider"])

    def test_build_embedding_provider_supports_aliases_and_scaffolds(self):
        self.assertIsInstance(build_embedding_provider("local"), LocalLexicalEmbeddingProvider)
        self.assertIsInstance(build_embedding_provider("lexical"), LocalLexicalEmbeddingProvider)
        self.assertIsInstance(build_embedding_provider("openai"), OpenAIEmbeddingProvider)
        self.assertIsInstance(build_embedding_provider("dashscope"), DashScopeEmbeddingProvider)
        self.assertIsInstance(build_embedding_provider("bge"), BgeEmbeddingProvider)
        self.assertIsInstance(build_embedding_provider("sentence_transformers"), SentenceTransformersEmbeddingProvider)

    def test_scaffolded_embedding_provider_health_and_guard(self):
        provider = build_embedding_provider("openai")
        health = provider.health()

        self.assertEqual("openai", health["provider"])
        self.assertEqual("disabled", health["status"])
        self.assertEqual([], health["requiredConfig"])
        self.assertEqual([], health["missingConfig"])
        with self.assertRaisesRegex(RuntimeError, "选择已测试成功"):
            provider.embed_text("test")

    def test_active_embedding_config_selects_runtime_provider(self):
        config = build_embedding_runtime_config(
            provider="qwen",
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
            api_key="test-key",
            model="text-embedding-v4",
        )
        token = set_active_embedding_config(config)
        try:
            with fake_langchain_openai():
                provider = build_embedding_provider()
            self.assertIsInstance(provider, DashScopeEmbeddingProvider)
            self.assertEqual("dashscope", provider.name)
            self.assertEqual("text-embedding-v4", provider.health()["model"])
        finally:
            reset_active_embedding_config(token)

    def test_vector_retriever_uses_configured_embedding_provider(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "card.txt").write_text("校园卡补办地点在行政楼一楼服务大厅。", encoding="utf-8")

            retriever = VectorRetriever(root_dir=temp_dir, chunk_size=80, overlap=10, backend="local_jsonl")
            results = retriever.search("校园卡补办地点", top_k=1)

            self.assertTrue(results)
            self.assertEqual("local_lexical", retriever.embedding_provider.name)


if __name__ == "__main__":
    unittest.main()
