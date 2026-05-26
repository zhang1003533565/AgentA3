import tempfile
import unittest
from pathlib import Path

from app.rag.core.types import RagDocument
from app.rag.vector_stores import (
    ElasticsearchVectorStore,
    FaissVectorStore,
    LocalJsonlVectorStore,
    MilvusVectorStore,
    PgVectorStore,
    build_vector_store,
)


class VectorStoreTest(unittest.TestCase):
    def test_local_jsonl_vector_store_upserts_and_loads_documents(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            store = LocalJsonlVectorStore(root_dir=root)

            count = store.upsert_documents([
                RagDocument(
                    id="doc-1#chunk-0",
                    content="校园卡补办地点在行政楼一楼服务大厅。",
                    source="doc-1.md",
                    metadata={"chunkIndex": 0},
                )
            ])
            loaded = store.load_documents()

            self.assertEqual(1, count)
            self.assertEqual(1, len(loaded))
            self.assertEqual("doc-1#chunk-0", loaded[0].id)
            self.assertEqual("local_jsonl", loaded[0].metadata.get("indexSource"))
            self.assertIn("local_jsonl:", store.signature())

    def test_local_jsonl_vector_store_replaces_same_source(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            store = LocalJsonlVectorStore(root_dir=root)

            store.upsert_documents([
                RagDocument(id="old", content="旧内容", source="same.md"),
                RagDocument(id="keep", content="保留内容", source="other.md"),
            ])
            store.upsert_documents([
                RagDocument(id="new", content="新内容", source="same.md"),
            ])
            loaded = store.load_documents()
            ids = {document.id for document in loaded}

            self.assertNotIn("old", ids)
            self.assertIn("new", ids)
            self.assertIn("keep", ids)

    def test_build_vector_store_uses_local_jsonl_aliases(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)

            self.assertIsInstance(build_vector_store(root, backend="local"), LocalJsonlVectorStore)
            self.assertIsInstance(build_vector_store(root, backend="jsonl"), LocalJsonlVectorStore)
            self.assertIsInstance(build_vector_store(root, backend="local_jsonl"), LocalJsonlVectorStore)

    def test_build_vector_store_supports_scaffolded_backends(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)

            self.assertIsInstance(build_vector_store(root, backend="faiss"), FaissVectorStore)
            self.assertIsInstance(build_vector_store(root, backend="milvus"), MilvusVectorStore)
            self.assertIsInstance(build_vector_store(root, backend="elasticsearch"), ElasticsearchVectorStore)
            self.assertIsInstance(build_vector_store(root, backend="es"), ElasticsearchVectorStore)
            self.assertIsInstance(build_vector_store(root, backend="pgvector"), PgVectorStore)

    def test_scaffolded_vector_store_health_and_write_guard(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            store = build_vector_store(root, backend="milvus")

            health = store.health()
            self.assertEqual("milvus", health["backend"])
            self.assertEqual("scaffolded", health["status"])
            self.assertFalse(health["configured"])
            self.assertIn("RAG_MILVUS_URI", health["missingEnv"])
            with self.assertRaisesRegex(RuntimeError, "scaffolded only"):
                store.upsert_documents([RagDocument(id="a", content="测试", source="a.md")])


if __name__ == "__main__":
    unittest.main()
