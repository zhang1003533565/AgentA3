import json
import importlib
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

from app.services.data_store import data_store  # noqa: E402
from app.models.schemas import ChatRequest  # noqa: E402
from app.services import chat_orchestrator  # noqa: E402
from app.langgraph.nodes import extract_keyword as extract_keyword_node_module  # noqa: E402
from app.langgraph.nodes import call_llm as call_llm_node_module  # noqa: E402
from app.model_providers.runtime_config import LlmRuntimeConfig, reset_active_llm_config, set_active_llm_config  # noqa: E402


class FakeJavaHandler(BaseHTTPRequestHandler):
    def _send_json(self, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        return

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)

        if path == "/api/v1/facility/list":
            self._send_json({
                "code": 200,
                "msg": "success",
                "data": {
                    "records": [
                        {"id": 1, "facilityName": "第一学生餐厅", "location": "南区", "description": "餐厅"},
                        {"id": 2, "facilityName": "第二学生餐厅", "location": "北区", "description": "餐厅"},
                    ]
                }
            })
            return

        if path == "/api/v1/canteen-stall/list":
            self._send_json({
                "code": 200,
                "msg": "success",
                "data": [
                    {"id": 11, "stallName": "黄焖鸡档口", "category": "米饭", "location": "一楼", "description": "招牌黄焖鸡", "restaurantId": 1, "score": 4.8, "avgPrice": 16},
                    {"id": 12, "stallName": "面食档口", "category": "面食", "location": "二楼", "description": "拉面", "restaurantId": 2, "score": 4.2, "avgPrice": 13},
                ]
            })
            return

        if path == "/api/v1/dish/list":
            name_kw = "".join(qs.get("name", []))
            category_kw = "".join(qs.get("category", []))
            taste_kw = "".join(qs.get("taste", []))
            if "黄焖鸡" in name_kw or "黄焖鸡" in category_kw or "黄焖鸡" in taste_kw:
                data = [{"id": 21, "name": "黄焖鸡米饭", "stallId": 11, "category": "米饭", "taste": "香辣", "rating": 4.9, "price": 18}]
            else:
                data = []
            self._send_json({"code": 200, "msg": "success", "data": data})
            return

        if path == "/api/v1/promotion-coupon/list":
            self._send_json({
                "code": 200,
                "msg": "success",
                "data": [
                    {"id": 31, "couponName": "黄焖鸡满减券", "category": "coupon", "tagType": "hot", "pickupLocation": "第一学生餐厅", "description": "黄焖鸡立减3元", "merchantName": "校园餐饮", "stallName": "黄焖鸡档口", "facilityName": "第一学生餐厅", "stallId": 11, "facilityId": 1, "startDate": "2026-01-01", "endDate": "2026-12-31"}
                ]
            })
            return

        if path == "/api/schedule/current-week":
            self._send_json({
                "code": 200,
                "msg": "success",
                "data": [
                    {"id": 41, "courseName": "高等数学", "teacherName": "王老师", "location": "A101", "weekday": 1, "classSessions": "1-2节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "MATH101", "credit": 4}
                ]
            })
            return

        if path.startswith("/api/schedule/week/"):
            self._send_json({
                "code": 200,
                "msg": "success",
                "data": [
                    {"id": 42, "courseName": "Java程序设计", "teacherName": "李老师", "location": "B202", "weekday": 2, "classSessions": "3-4节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "CS102", "credit": 3}
                ]
            })
            return

        self.send_response(404)
        self.end_headers()


class FakeLLM:
    def plan_leader_intent(self, input_text, rag_strategy=""):
        return {
            "intent": "campus_search",
            "target_agent": "textbook_knowledge_agent",
            "need_retrieval": True,
            "rag_strategy": rag_strategy or "naive_rag",
            "action": "delegate_agent",
            "tool_name": "",
            "route_reason": "测试 LLM 路由到教材知识点智能体。",
            "answer": "",
        }

    def extract_search_keyword(self, input_text):
        return "黄焖鸡"

    def answer(self, prompt, input_text, history, search_keyword, search_results):
        return f"已检索到{len(search_results)}条候选，关键词={search_keyword}"

    def generate_specialist_answer(self, agent_name, input_text, evidence):
        if agent_name == "mind_map_agent":
            return "```mermaid\nmindmap\n  root((操作系统进程调度))\n```"
        return f"{agent_name}: 已生成，证据数={len(evidence or [])}"


class JavaReuseIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(("127.0.0.1", 0), FakeJavaHandler)
        cls.port = cls.server.server_port
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def setUp(self):
        data_store.enabled = True
        data_store.java_base_url = f"http://127.0.0.1:{self.port}"
        data_store.timeout_seconds = 3
        self._llm_token = set_active_llm_config(LlmRuntimeConfig(
            provider="deepseek",
            base_url="https://llm.test/v1",
            api_key="test-key",
            model="test-model",
        ))
        self._patched_modules = []
        self._patch_chat_services()

    def tearDown(self):
        for module, old_get_chat_service in reversed(self._patched_modules):
            module.get_chat_service = old_get_chat_service
        reset_active_llm_config(self._llm_token)

    def _patch_chat_services(self):
        leader_agent_module = importlib.import_module("app.multi_agents.leader_agent.agent")
        mind_map_agent_module = importlib.import_module("app.multi_agents.mind_map_agent.agent")
        textbook_agent_module = importlib.import_module("app.multi_agents.textbook_knowledge_agent.agent")

        for module in (
            extract_keyword_node_module,
            call_llm_node_module,
            leader_agent_module,
            mind_map_agent_module,
            textbook_agent_module,
        ):
            self._patched_modules.append((module, module.get_chat_service))
            module.get_chat_service = lambda service=FakeLLM(): service

    def test_search_keyword_via_java_apis(self):
        results = data_store.search_keyword("Bearer t", "黄焖鸡")
        self.assertTrue(len(results) > 0)
        types = {item.get("type") for item in results}
        self.assertIn("restaurant", types)
        self.assertIn("stall", types)
        self.assertIn("dish", types)
        self.assertIn("coupon", types)

    def test_search_schedule_via_java_apis(self):
        results = data_store.search_schedule("Bearer t", "本周有什么课")
        self.assertTrue(len(results) > 0)
        self.assertEqual("course_schedule", results[0].get("type"))
        self.assertIn("name", results[0])

    def test_run_chat_core_uses_graph_and_java_data(self):
        req = ChatRequest(sessionId="s1", prompt="你是助手", input="推荐黄焖鸡")
        resp = chat_orchestrator.run_chat_core(req, "Bearer token-x", user_id=1001)
        self.assertEqual("s1", resp.sessionId)
        self.assertEqual("黄焖鸡", resp.searchKeyword)
        self.assertTrue(len(resp.matchedResults) > 0)
        self.assertIn("已检索到", resp.answer)

    def test_run_chat_core_rejects_removed_md_agent(self):
        req = ChatRequest(
            sessionId="agent-md",
            agentName="md_knowledge_agent",
            input="# 数据结构\n- 栈遵循后进先出\n- 队列遵循先进先出",
        )

        with self.assertRaisesRegex(Exception, "智能体不存在"):
            chat_orchestrator.run_chat_core(req, "Bearer token-x", user_id=1001)

    def test_forced_specialist_uses_java_forwarded_llm_config(self):
        req = ChatRequest(
            sessionId="agent-llm",
            agentName="mind_map_agent",
            input="操作系统进程调度思维导图",
        )

        resp = chat_orchestrator.run_chat_core(req, "Bearer token-x", user_id=1001)

        self.assertEqual("mind_map_agent", resp.agentName)
        self.assertEqual("test-model", resp.model)
        self.assertIn("mindmap", resp.answer)


if __name__ == "__main__":
    unittest.main()
