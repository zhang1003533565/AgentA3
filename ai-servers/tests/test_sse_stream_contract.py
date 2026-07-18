import json
import os
import unittest
from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.main import app
from app.models.schemas import ChatResponse, RagDocumentResponse, RagQueryRequest, RagQueryResponse
from app.utils.sse import build_sse
import app.api.routes.chat as chat_route
import app.api.routes.rag as rag_route


class SseStreamContractTest(unittest.TestCase):
    def setUp(self):
        self._old_internal_token = os.environ.get("AI_INTERNAL_TOKEN")
        os.environ["AI_INTERNAL_TOKEN"] = "test-internal-token"

    def tearDown(self):
        if self._old_internal_token is None:
            os.environ.pop("AI_INTERNAL_TOKEN", None)
        else:
            os.environ["AI_INTERNAL_TOKEN"] = self._old_internal_token

    def _client(self):
        return TestClient(
            app,
            headers={"X-AI-Internal-Token": "test-internal-token"},
        )

    def test_merge_attachments_prefers_storage_key_and_uses_url_as_fallback(self):
        generated = {
            "storageKey": "11111111-1111-4111-8111-111111111111.md",
            "internalCapability": "capability-1",
            "serverGenerated": True,
        }
        duplicate_storage_key = {
            **generated,
            "url": "/ignored-because-storage-key-is-the-identity",
            "internalCapability": "capability-2",
        }
        legacy = {"url": "https://cdn.example.edu/file.pdf", "name": "file.pdf"}
        duplicate_url = {"url": legacy["url"], "name": "duplicate.pdf"}

        merged = rag_route._merge_attachments(
            [generated, legacy],
            [duplicate_storage_key, duplicate_url, {"name": "missing-identity"}],
        )

        self.assertEqual([generated, legacy], merged)

    def test_server_generated_attachment_reaches_sync_and_sse_done_payload(self):
        client = self._client()
        attachment = {
            "name": "22222222-2222-4222-8222-222222222222.md",
            "storageKey": "22222222-2222-4222-8222-222222222222.md",
            "serverGenerated": True,
            "internalCapability": "internal-capability",
            "sha256": "a" * 64,
            "size": 18,
            "createdAt": "2026-07-14T10:00:00Z",
            "expiresAt": "2026-07-21T10:00:00Z",
            "mimeType": "text/markdown",
            "type": "file",
            "ext": "md",
        }
        original_run_rag_core = rag_route._run_rag_query_core
        original_export_generated_answer = rag_route.export_generated_answer
        try:
            rag_route.export_generated_answer = lambda *args, **kwargs: SimpleNamespace(
                attachments=[dict(attachment)],
                diagnostics={"skipped": False},
            )
            rag_route._run_rag_query_core = lambda request, authorization: rag_route._decorate_output_response(
                RagQueryResponse(
                    strategy="direct_agent",
                    answer="# 导出内容",
                    answerType="markdown",
                    metadata={"executedAgent": "textbook_knowledge_agent"},
                )
            )
            request_payload = {"input": "导出内容", "agentName": "textbook_knowledge_agent"}

            sync_response = client.post(
                "/internal/rag/query",
                headers={"Authorization": "Bearer test-token"},
                json=request_payload,
            )
            self.assertEqual(200, sync_response.status_code)
            sync_attachment = sync_response.json()["attachments"][0]
            self.assertEqual(attachment["storageKey"], sync_attachment["storageKey"])
            self.assertEqual(attachment["internalCapability"], sync_attachment["internalCapability"])

            with client.stream(
                "POST",
                "/internal/rag/query/stream",
                headers={"Authorization": "Bearer test-token"},
                json=request_payload,
            ) as response:
                self.assertEqual(200, response.status_code)
                payload = "".join(response.iter_text())

            done_data = None
            for event in payload.split("\n\n"):
                if event.startswith("event: done\n"):
                    data_line = next(line for line in event.splitlines() if line.startswith("data: "))
                    done_data = json.loads(data_line.removeprefix("data: "))
                    break
            self.assertIsNotNone(done_data)
            stream_attachment = done_data["attachments"][0]
            self.assertEqual(attachment["storageKey"], stream_attachment["storageKey"])
            self.assertEqual(attachment["internalCapability"], stream_attachment["internalCapability"])
        finally:
            rag_route._run_rag_query_core = original_run_rag_core
            rag_route.export_generated_answer = original_export_generated_answer

    def test_build_sse_uses_real_newlines(self):
        event = build_sse("status", {"stage": "processing"})
        self.assertIn("event: status\n", event)
        self.assertIn("\ndata: ", event)
        self.assertTrue(event.endswith("\n\n"))
        self.assertNotIn("\\n", event)

    def test_stream_endpoint_emits_sse_events(self):
        client = self._client()
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

    def test_rag_stream_disables_proxy_buffering_and_reports_timings(self):
        client = self._client()
        original_run_rag_core = rag_route._run_rag_query_core
        original_save_context = rag_route._save_conversation_context
        try:
            rag_route._run_rag_query_core = lambda request, authorization: RagQueryResponse(
                strategy="direct_agent",
                answer="这是立即发送的测试回答。",
                answerType="text",
                metadata={"executedAgent": "textbook_knowledge_agent"},
            )
            rag_route._save_conversation_context = lambda *_args: rag_route.time.sleep(0.01)
            with client.stream(
                "POST",
                "/internal/rag/query/stream",
                headers={"Authorization": "Bearer test-token"},
                json={
                    "input": "测试流式耗时",
                    "agentName": "textbook_knowledge_agent",
                    "metadata": {
                        "profileContextMs": 7,
                        "profileContextSource": "local_snapshot",
                    },
                },
            ) as response:
                self.assertEqual(200, response.status_code)
                self.assertEqual("no-cache, no-transform", response.headers["cache-control"])
                self.assertEqual("no", response.headers["x-accel-buffering"])
                payload = "".join(response.iter_text())

            events = [event for event in payload.split("\n\n") if event.strip()]
            self.assertTrue(events[0].startswith("event: status\n"))
            done_event = next(event for event in events if event.startswith("event: done\n"))
            data_line = next(line for line in done_event.splitlines() if line.startswith("data: "))
            done_data = json.loads(data_line.removeprefix("data: "))
            metadata = done_data["retrievalMeta"]
            timings = metadata["timings"]
            self.assertEqual(7, timings["profileMs"])
            self.assertIn("planMs", timings)
            self.assertIn("executionMs", timings)
            self.assertIn("finalizeMs", timings)
            self.assertIn("persistMs", timings)
            self.assertIn("firstEventMs", timings)
            self.assertIn("firstTokenMs", timings)
            self.assertIn("totalMs", timings)
            self.assertLessEqual(timings["firstEventMs"], timings["firstTokenMs"])
            self.assertLessEqual(timings["firstTokenMs"], timings["totalMs"])
            self.assertGreaterEqual(timings["persistMs"], 5)
            self.assertLessEqual(timings["persistMs"], timings["totalMs"])
            self.assertEqual("python_processing_including_persistence", metadata["timingScope"])
            self.assertEqual("local_snapshot", metadata["profileContextSource"])
        finally:
            rag_route._run_rag_query_core = original_run_rag_core
            rag_route._save_conversation_context = original_save_context

    def test_rag_sync_total_timing_is_recorded_after_persistence(self):
        client = self._client()
        original_run_rag_core = rag_route._run_rag_query_core
        original_save_context = rag_route._save_conversation_context
        observed = {}
        try:
            rag_route._run_rag_query_core = lambda request, authorization: RagQueryResponse(
                strategy="direct_agent",
                answer="同步耗时测试回答。",
                answerType="text",
                metadata={"executedAgent": "textbook_knowledge_agent"},
            )

            def observe_persistence(request, authorization, response):
                timings = (response.metadata or {}).get("timings") or {}
                observed["totalPresentBeforePersistence"] = "totalMs" in timings
                rag_route.time.sleep(0.01)

            rag_route._save_conversation_context = observe_persistence
            response = client.post(
                "/internal/rag/query",
                headers={"Authorization": "Bearer test-token"},
                json={"input": "同步耗时测试", "agentName": "textbook_knowledge_agent"},
            )
        finally:
            rag_route._run_rag_query_core = original_run_rag_core
            rag_route._save_conversation_context = original_save_context

        self.assertEqual(200, response.status_code)
        metadata = response.json()["metadata"]
        timings = metadata["timings"]
        self.assertFalse(observed["totalPresentBeforePersistence"])
        self.assertIn("finalizeMs", timings)
        self.assertIn("persistMs", timings)
        self.assertIn("totalMs", timings)
        self.assertGreaterEqual(timings["persistMs"], 5)
        self.assertLessEqual(timings["persistMs"], timings["totalMs"])
        self.assertEqual("python_processing_including_persistence", metadata["timingScope"])

    def test_rag_done_payload_uses_resource_evidence_finalizer(self):
        client = self._client()
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
