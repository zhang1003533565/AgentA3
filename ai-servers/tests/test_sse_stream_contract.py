import json
import unittest

from fastapi.testclient import TestClient

from app.main import app
from app.models.schemas import ChatResponse, RagDocumentResponse, RagQueryRequest, RagQueryResponse
from app.utils.sse import build_sse
import app.api.routes.chat as chat_route
import app.api.routes.rag as rag_route


class SseStreamContractTest(unittest.TestCase):
    def test_build_sse_uses_real_newlines(self):
        event = build_sse("status", {"stage": "processing"})
        self.assertIn("event: status\n", event)
        self.assertIn("\ndata: ", event)
        self.assertTrue(event.endswith("\n\n"))
        self.assertNotIn("\\n", event)

    def test_stream_endpoint_emits_sse_events(self):
        client = TestClient(app)
        original_run_chat_core = chat_route.run_chat_core
        try:
            chat_route.run_chat_core = lambda request, authorization, user_id: ChatResponse(
                sessionId=request.sessionId or "s-default",
                sessionToken="tok-1",
                model="test-model",
                searchKeyword="黄焖鸡",
                matchedResults=[{"type": "stall", "name": "黄焖鸡档口"}],
                answer="你好，这里是测试回答。",
            )

            with client.stream(
                "POST",
                "/internal/chat/stream",
                headers={"Authorization": "Bearer test-token"},
                json={"sessionId": "s1", "prompt": "p", "input": "你好"},
            ) as response:
                self.assertEqual(200, response.status_code)
                chunks = []
                for text in response.iter_text():
                    if text:
                        chunks.append(text)
                    if len("".join(chunks)) > 120:
                        break

            payload = "".join(chunks)
            self.assertIn("event: status\n", payload)
            self.assertIn("event: session\n", payload)
            self.assertIn("event: done\n", payload)
        finally:
            chat_route.run_chat_core = original_run_chat_core

    def test_rag_done_payload_uses_resource_evidence_finalizer(self):
        client = TestClient(app)
        original_run_rag_core = rag_route._run_rag_query_core
        try:
            rag_route._run_rag_query_core = lambda request, authorization: RagQueryResponse(
                strategy="hybrid_rag",
                answer="循环队列的队尾按容量取模。",
                answerType="text",
                documents=[
                    RagDocumentResponse(
                        id="kb:queue:1",
                        content="循环队列的队尾下标按容量取模。",
                        source="knowledge_base",
                        metadata={"title": "数据结构教材"},
                    )
                ],
            )
            request_payload = {"input": "循环队列是什么？", "agentName": "textbook_knowledge_agent"}

            sync_response = client.post(
                "/internal/rag/query",
                headers={"Authorization": "Bearer test-token"},
                json=request_payload,
            )
            self.assertEqual(200, sync_response.status_code)
            sync_data = sync_response.json()
            self.assertTrue(sync_data["resources"])
            self.assertEqual("grounded", sync_data["evidenceChain"]["status"])

            with client.stream(
                "POST",
                "/internal/rag/query/stream",
                headers={"Authorization": "Bearer test-token"},
                json=request_payload,
            ) as response:
                self.assertEqual(200, response.status_code)
                payload = "".join(response.iter_text())

            done_data = None
            events = payload.split("\n\n")
            for event in events:
                if event.startswith("event: done\n"):
                    data_line = next(line for line in event.splitlines() if line.startswith("data: "))
                    done_data = json.loads(data_line.removeprefix("data: "))
                    break

            self.assertIsNotNone(done_data)
            self.assertTrue(done_data["resources"])
            self.assertEqual("grounded", done_data["evidenceChain"]["status"])
            self.assertEqual(
                done_data["resources"][0]["evidenceIds"],
                done_data["evidenceChain"]["resourceLinks"][0]["evidenceIds"],
            )
            self.assertEqual(
                sync_data["evidenceChain"]["sources"][0]["evidenceId"],
                done_data["evidenceChain"]["sources"][0]["evidenceId"],
            )
        finally:
            rag_route._run_rag_query_core = original_run_rag_core

    def test_conversation_context_keeps_only_safe_resource_evidence_summaries(self):
        captured = {}
        original_save_context = rag_route.leader_agent.save_context
        try:
            rag_route.leader_agent.save_context = lambda *args, **kwargs: captured.update(kwargs)
            request = RagQueryRequest(input="继续讲", metadata={"sessionId": "session-1"})
            response = RagQueryResponse(
                strategy="direct_agent",
                answer="回答",
                resources=[{
                    "id": "res-1",
                    "kind": "document",
                    "deliveryType": "document",
                    "groundingStatus": "model_only",
                    "title": "资料",
                    "url": "https://internal.example/secret.docx",
                    "payload": {"content": "不能进入上下文的全文"},
                }],
                evidenceChain={
                    "chainId": "chain-1",
                    "status": "model_only",
                    "evidenceState": "available",
                    "sources": [{"excerpt": "不能进入上下文的来源全文"}],
                    "generation": {"agent": "leader_agent", "answerType": "text", "model": "private-model"},
                    "integrity": {"digest": "secret"},
                },
            )

            rag_route._save_conversation_context(request, "Bearer test-token", response)

            saved_metadata = captured["metadata"]
            self.assertEqual(
                {"id", "kind", "deliveryType", "groundingStatus", "title"},
                set(saved_metadata["resources"][0]),
            )
            self.assertEqual(1, saved_metadata["evidenceChain"]["sourceCount"])
            serialized = json.dumps(saved_metadata, ensure_ascii=False)
            self.assertNotIn("secret.docx", serialized)
            self.assertNotIn("不能进入上下文", serialized)
            self.assertNotIn("private-model", serialized)
        finally:
            rag_route.leader_agent.save_context = original_save_context


if __name__ == "__main__":
    unittest.main()
