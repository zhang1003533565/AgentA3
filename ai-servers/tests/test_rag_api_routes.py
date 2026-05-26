import base64
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

    def test_ingest_accepts_base64_multimodal_file(self):
        old_root = os.environ.get("RAG_KNOWLEDGE_BASE_DIR")
        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                os.environ["RAG_KNOWLEDGE_BASE_DIR"] = temp_dir
                raw_bytes = b"\x89PNG\r\n\x1a\nfake-image"
                response = self.client.post(
                    "/internal/rag/documents",
                    headers=self.headers,
                    json={
                        "documents": [
                            {
                                "source": "notice.png",
                                "contentBase64": base64.b64encode(raw_bytes).decode("ascii"),
                                "metadata": {"origin": "unit_test"},
                            }
                        ]
                    },
                )

                self.assertEqual(200, response.status_code)
                payload = response.json()
                self.assertEqual("image", payload["documents"][0]["modality"])
                stored_path = Path(payload["storedFiles"][0])
                self.assertEqual(raw_bytes, stored_path.read_bytes())
                self.assertGreaterEqual(payload["indexedChunkCount"], 1)
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
            self.assertEqual("implemented_optional", payload["status"])
            self.assertIn("RAG_MILVUS_URI", payload["missingEnv"])
        finally:
            if old_backend is None:
                os.environ.pop("RAG_VECTOR_STORE_BACKEND", None)
            else:
                os.environ["RAG_VECTOR_STORE_BACKEND"] = old_backend

    def test_capabilities_endpoint_describes_runtime_framework(self):
        response = self.client.get("/internal/rag/capabilities", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertIn("naive_rag", payload["query"]["strategies"])
        self.assertIn("hybrid", payload["retrieval"]["retrievers"])
        self.assertIn("faithfulness", payload["evaluation"]["metrics"])
        self.assertIn("textbook_knowledge_agent", payload["agents"])

    def test_framework_endpoint_describes_full_runtime_layout(self):
        response = self.client.get("/internal/rag/framework", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(16, len(payload["coverage"]))
        self.assertIn("app/rag/strategies", payload["runtimeFolders"]["strategies"])
        self.assertIn("local_jsonl", {item["name"] for item in payload["vectorStores"]})
        self.assertIn("neo4j", {item["name"] for item in payload["graphStores"]})
        self.assertIn("RAG_KNOWLEDGE_BASE_DIR", {item["name"] for item in payload["runtimeEnv"]})

    def test_agents_endpoint_exposes_skill_catalog(self):
        response = self.client.get("/internal/rag/agents", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        names = {item["name"] for item in payload["agents"]}
        self.assertEqual(7, payload["total"])
        self.assertIn("textbook_knowledge_agent", names)
        self.assertNotIn("answer_agent", names)
        self.assertNotIn("retriever_agent", names)
        textbook_agent = next(item for item in payload["agents"] if item["name"] == "textbook_knowledge_agent")
        self.assertIn("skill.md", textbook_agent["files"]["skill"])
        self.assertIn("教材知识点智能体 Skill", textbook_agent["documents"]["skill"])
        self.assertIn("questionBank", payload["workflow"])
        self.assertEqual("agentName", payload["invocation"]["parameter"])

    def test_agent_detail_endpoint_returns_single_agent(self):
        response = self.client.get("/internal/rag/agents/leader_agent", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("leader_agent", payload["name"])
        self.assertIn("Leader", payload["role"])

    def test_query_endpoint_can_execute_selected_specialist_agent(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "数据结构中的栈与队列",
                "agentName": "ppt_agent",
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("multi_agent_rag", payload["strategy"])
        self.assertEqual("ppt_agent", payload["metadata"]["agentName"])
        self.assertIn("PPT 大纲", payload["answer"])
        self.assertEqual("agent_answer", payload["trace"][-1]["stage"])

    def test_query_endpoint_rejects_unknown_agent(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={"input": "测试", "agentName": "deleted_agent"},
        )

        self.assertEqual(400, response.status_code)

    def test_agent_catalog_examples_are_runnable_for_specialists(self):
        catalog_response = self.client.get("/internal/rag/agents", headers=self.headers)
        self.assertEqual(200, catalog_response.status_code)

        specialists = [
            agent for agent in catalog_response.json()["agents"]
            if agent["name"] != "leader_agent"
        ]

        for agent in specialists:
            with self.subTest(agent=agent["name"]):
                example = agent["invokeExample"]
                response = self.client.post(
                    "/internal/rag/query",
                    headers=self.headers,
                    json={
                        "input": example["input"],
                        "agentName": example["agentName"],
                        "ragStrategy": example["ragStrategy"],
                    },
                )

                self.assertEqual(200, response.status_code)
                payload = response.json()
                self.assertEqual(agent["name"], payload["metadata"]["agentName"])
                self.assertTrue(payload["answer"])

    def test_query_endpoint_synthesizes_answer_when_strategy_has_no_llm_answer(self):
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
        self.assertTrue(payload["answer"])
        self.assertEqual("local_context_synthesizer", payload["metadata"]["answerSynthesizer"])

    def test_embedding_health_returns_provider_metadata(self):
        response = self.client.get("/internal/rag/embedding/health", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("local_lexical", payload["provider"])
        self.assertEqual("implemented", payload["status"])

    def test_embedding_health_supports_scaffolded_provider(self):
        old_provider = os.environ.get("RAG_EMBEDDING_PROVIDER")
        old_key = os.environ.get("OPENAI_API_KEY")
        try:
            os.environ["RAG_EMBEDDING_PROVIDER"] = "openai"
            os.environ.pop("OPENAI_API_KEY", None)
            response = self.client.get("/internal/rag/embedding/health", headers=self.headers)

            self.assertEqual(200, response.status_code)
            payload = response.json()
            self.assertEqual("openai", payload["provider"])
            self.assertEqual("implemented_optional", payload["status"])
            self.assertIn("OPENAI_API_KEY", payload["requiredEnv"])
        finally:
            if old_provider is None:
                os.environ.pop("RAG_EMBEDDING_PROVIDER", None)
            else:
                os.environ["RAG_EMBEDDING_PROVIDER"] = old_provider
            if old_key is not None:
                os.environ["OPENAI_API_KEY"] = old_key

    def test_evaluate_endpoint_returns_rag_metrics(self):
        response = self.client.post(
            "/internal/rag/evaluate",
            headers=self.headers,
            json={
                "query": "校园卡补办地点",
                "answer": "校园卡补办地点在行政楼一楼服务大厅。",
                "documents": [
                    {
                        "id": "doc-1",
                        "content": "校园卡补办地点在行政楼一楼服务大厅。",
                        "source": "card.md",
                        "score": 1.0,
                        "metadata": {},
                    }
                ],
                "expectedSources": ["card.md"],
                "expectedAnswerTerms": ["行政楼"],
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertTrue(payload["passed"])
        self.assertGreater(payload["metrics"]["hitRate"], 0)
        self.assertGreater(payload["metrics"]["faithfulness"], 0)

    def test_graph_store_health_returns_backend(self):
        response = self.client.get("/internal/rag/graph-store/health", headers=self.headers)

        self.assertEqual(200, response.status_code)
        self.assertEqual("local_graph", response.json()["backend"])


if __name__ == "__main__":
    unittest.main()
