import tempfile
import unittest
from pathlib import Path

from app.rag.embeddings import (
    BgeEmbeddingProvider,
    DashScopeEmbeddingProvider,
    LocalLexicalEmbeddingProvider,
    OpenAIEmbeddingProvider,
    SentenceTransformersEmbeddingProvider,
    build_embedding_provider,
)
from app.rag.retrievers.vector import VectorRetriever


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
        self.assertEqual("scaffolded", health["status"])
        self.assertIn("OPENAI_API_KEY", health["requiredEnv"])
        with self.assertRaisesRegex(RuntimeError, "scaffolded only"):
            provider.embed_text("test")

    def test_vector_retriever_uses_configured_embedding_provider(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "card.md").write_text("校园卡补办地点在行政楼一楼服务大厅。", encoding="utf-8")

            retriever = VectorRetriever(root_dir=temp_dir, chunk_size=80, overlap=10)
            results = retriever.search("校园卡补办地点", top_k=1)

            self.assertTrue(results)
            self.assertEqual("local_lexical", retriever.embedding_provider.name)


if __name__ == "__main__":
    unittest.main()
