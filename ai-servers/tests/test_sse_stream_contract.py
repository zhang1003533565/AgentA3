import unittest

from fastapi.testclient import TestClient

from app.main import app
from app.models.schemas import ChatResponse
from app.utils.sse import build_sse
import app.api.routes.chat as chat_route


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


if __name__ == "__main__":
    unittest.main()
