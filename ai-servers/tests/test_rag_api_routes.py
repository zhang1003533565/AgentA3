import os
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app


class RagApiRoutesTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.headers = {"Authorization": "Bearer test-token"}

    def test_list_strategies_returns_all_rag_modes(self):
        response = self.client.get("/internal/rag/strategies", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        names = {item["name"] for item in payload["strategies"]}
        self.assertEqual(16, len(names))
        self.assertIn("multi_agent_rag", names)
        self.assertIn("text_to_sql", names)

    def test_query_endpoint_runs_strategy(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "统计食堂优惠券数量",
                "keyword": "优惠券",
                "ragStrategy": "text_to_sql",
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("text_to_sql", payload["strategy"])
        self.assertTrue(payload["metadata"]["readonly"])
        self.assertIn("SELECT", payload["metadata"]["sql"])

    def test_ingest_and_list_documents(self):
        old_root = os.environ.get("RAG_KNOWLEDGE_BASE_DIR")
        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                os.environ["RAG_KNOWLEDGE_BASE_DIR"] = temp_dir
                response = self.client.post(
                    "/internal/rag/documents",
                    headers=self.headers,
                    json={
                        "documents": [
                            {
                                "source": "校园卡服务.md",
                                "content": "校园卡补办地点在行政楼一楼服务大厅。",
                            }
                        ]
                    },
                )

                self.assertEqual(200, response.status_code)
                payload = response.json()
                self.assertEqual(1, payload["storedCount"])
                self.assertGreaterEqual(payload["indexedChunkCount"], 1)
                self.assertTrue(Path(payload["storedFiles"][0]).exists())
                self.assertTrue(Path(payload["indexPath"]).exists())
                self.assertEqual("markdown", payload["documents"][0]["modality"])
                self.assertIn("index", {step["stage"] for step in payload["trace"]})

                list_response = self.client.get("/internal/rag/documents", headers=self.headers)
                self.assertEqual(200, list_response.status_code)
                self.assertTrue(list_response.json()["documents"])
        finally:
            if old_root is None:
                os.environ.pop("RAG_KNOWLEDGE_BASE_DIR", None)
            else:
                os.environ["RAG_KNOWLEDGE_BASE_DIR"] = old_root

    def test_rag_routes_require_authorization(self):
        response = self.client.get("/internal/rag/strategies")

        self.assertEqual(401, response.status_code)

    def test_vector_store_health_returns_backend_metadata(self):
        response = self.client.get("/internal/rag/vector-store/health", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("local_jsonl", payload["backend"])
        self.assertIn("local_chunks.jsonl", payload["indexPath"])

    def test_vector_store_health_supports_scaffolded_backend(self):
        old_backend = os.environ.get("RAG_VECTOR_STORE_BACKEND")
        try:
            os.environ["RAG_VECTOR_STORE_BACKEND"] = "milvus"
            response = self.client.get("/internal/rag/vector-store/health", headers=self.headers)

            self.assertEqual(200, response.status_code)
            payload = response.json()
            self.assertEqual("milvus", payload["backend"])
            self.assertEqual("scaffolded", payload["status"])
            self.assertIn("RAG_MILVUS_URI", payload["missingEnv"])
        finally:
            if old_backend is None:
                os.environ.pop("RAG_VECTOR_STORE_BACKEND", None)
            else:
                os.environ["RAG_VECTOR_STORE_BACKEND"] = old_backend

    def test_embedding_health_returns_provider_metadata(self):
        response = self.client.get("/internal/rag/embedding/health", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("local_lexical", payload["provider"])
        self.assertEqual("implemented", payload["status"])

    def test_embedding_health_supports_scaffolded_provider(self):
        old_provider = os.environ.get("RAG_EMBEDDING_PROVIDER")
        try:
            os.environ["RAG_EMBEDDING_PROVIDER"] = "openai"
            response = self.client.get("/internal/rag/embedding/health", headers=self.headers)

            self.assertEqual(200, response.status_code)
            payload = response.json()
            self.assertEqual("openai", payload["provider"])
            self.assertEqual("scaffolded", payload["status"])
            self.assertIn("OPENAI_API_KEY", payload["requiredEnv"])
        finally:
            if old_provider is None:
                os.environ.pop("RAG_EMBEDDING_PROVIDER", None)
            else:
                os.environ["RAG_EMBEDDING_PROVIDER"] = old_provider


if __name__ == "__main__":
    unittest.main()
