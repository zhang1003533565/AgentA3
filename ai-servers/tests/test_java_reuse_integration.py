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
                    {"id": 41, "courseName": "高等数学", "teacherName": "王老师", "location": "A101", "weekday": 1, "classSessions": "1-2节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "MATH101", "credit": 4},
                    {"id": 46, "courseName": "大学物理", "teacherName": "周老师", "location": "E501", "weekday": 3, "classSessions": "3-4节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "PHY101", "credit": 3}
                ]
            })
            return

        if path == "/api/schedule/settings":
            self._send_json({
                "code": 200,
                "msg": "success",
                "data": {
                    "academicYear": "2025-2026",
                    "semesterTerm": 2,
                    "semesterStart": "2026-03-02",
                }
            })
            return

        if path == "/api/schedule":
            records = [
                {"id": 43, "academicYear": "2025-2026", "semesterTerm": 2, "courseName": "数据结构", "teacherName": "赵老师", "location": "C301", "weekday": 1, "classSessions": "1-2节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "CS201", "credit": 3},
                {"id": 44, "academicYear": "2025-2026", "semesterTerm": 2, "courseName": "数据结构", "teacherName": "赵老师", "location": "C301", "weekday": 3, "classSessions": "3-4节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "CS201", "credit": 3},
                {"id": 45, "academicYear": "2025-2026", "semesterTerm": 2, "courseName": "操作系统", "teacherName": "孙老师", "location": "D402", "weekday": 5, "classSessions": "5-6节", "weekRange": "1-16周", "assessmentType": "考查", "campus": "主校区", "classCode": "CS301", "credit": 3},
                {"id": 49, "academicYear": "2025-2026", "semesterTerm": 2, "courseName": "Linux系统", "teacherName": "庄老师", "location": "D403", "weekday": 2, "classSessions": "5-6节", "weekRange": "1-16周", "assessmentType": "考查", "campus": "主校区", "classCode": "LINUX101", "credit": 2},
            ]
            if "true" in qs.get("allSemesters", []):
                records.extend([
                    {"id": 47, "academicYear": "2024-2025", "semesterTerm": 1, "courseName": "大学英语", "teacherName": "钱老师", "location": "A201", "weekday": 2, "classSessions": "1-2节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "ENG101", "credit": 2},
                    {"id": 50, "academicYear": "2024-2025", "semesterTerm": 1, "courseName": "Java程序设计", "teacherName": "李老师", "location": "B202", "weekday": 2, "classSessions": "3-4节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "JAVA101", "credit": 3},
                ])
            self._send_json({
                "code": 200,
                "msg": "success",
                "data": records
            })
            return

        if path.startswith("/api/schedule/week/"):
            self._send_json({
                "code": 200,
                "msg": "success",
                "data": [
                    {"id": 42, "courseName": "Java程序设计", "teacherName": "李老师", "location": "B202", "weekday": 2, "classSessions": "3-4节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "CS102", "credit": 3},
                    {"id": 48, "courseName": "数据库原理", "teacherName": "吴老师", "location": "F601", "weekday": 3, "classSessions": "3-4节", "weekRange": "1-16周", "assessmentType": "考试", "campus": "主校区", "classCode": "DB101", "credit": 3}
                ]
            })
            return

        self.send_response(404)
        self.end_headers()


class FakeLLM:
    def complete(self, system_prompt, user_prompt):
        if "Leader 智能体" in system_prompt:
            payload = json.loads(user_prompt)
            input_text = payload.get("user_input") or ""
            return json.dumps({
                "intent": "campus_search",
                "target_agent": "textbook_knowledge_agent",
                "need_retrieval": False,
                "rag_strategy": "",
                "action": "delegate_agent",
                "tool_name": "",
                "route_reason": "测试 LLM 路由到教材知识点智能体。",
                "answer": "",
            }, ensure_ascii=False)
        if "思维导图智能体" in system_prompt:
            return "```mermaid\nmindmap\n  root((操作系统进程调度))\n```"
        return "textbook_knowledge_agent: 已生成，证据数=1"

    def extract_search_keyword(self, input_text):
        return "黄焖鸡"

    def answer(self, prompt, input_text, history, search_keyword, search_results):
        return f"已检索到{len(search_results)}条候选，关键词={search_keyword}"

    def stream_complete(self, system_prompt, user_prompt):
        yield self.complete(system_prompt, user_prompt)

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
        self._patch_model_providers()

    def tearDown(self):
        for module, old_get_chat_model_provider in reversed(self._patched_modules):
            module.get_chat_model_provider = old_get_chat_model_provider
        reset_active_llm_config(self._llm_token)

    def _patch_model_providers(self):
        leader_agent_module = importlib.import_module("app.multi_agents.leader_agent.agent")
        runtime_module = importlib.import_module("app.multi_agents.runtime")

        for module in (
            extract_keyword_node_module,
            leader_agent_module,
            runtime_module,
        ):
            self._patched_modules.append((module, module.get_chat_model_provider))
            module.get_chat_model_provider = lambda provider=FakeLLM(): provider

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

    def test_search_semester_schedule_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "这个学期都有什么课啊")
        self.assertEqual(3, len(results))
        by_name = {item.get("name"): item for item in results}
        self.assertEqual("course_schedule_summary", by_name["数据结构"].get("type"))
        self.assertEqual(2, by_name["数据结构"].get("scheduleCount"))

    def test_search_weekday_and_session_schedule_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "周三第3节有什么课")
        self.assertEqual(1, len(results))
        self.assertEqual("大学物理", results[0].get("name"))

    def test_search_all_semester_schedule_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "所有学期都有什么课程")
        names = {item.get("name") for item in results}
        self.assertIn("大学英语", names)
        self.assertIn("数据结构", names)

    def test_search_course_teacher_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "linux系统的老师是谁")
        names = {item.get("name") for item in results}
        self.assertIn("Linux系统", names)
        by_name = {item.get("name"): item for item in results}
        self.assertEqual("庄老师", by_name["Linux系统"].get("teacherName"))
        self.assertEqual("course_schedule_summary", by_name["Linux系统"].get("type"))

    def test_search_course_teacher_with_alias_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "Linux操作系统的老师是谁")
        names = {item.get("name") for item in results}
        self.assertIn("Linux系统", names)
        self.assertIn("操作系统", names)

    def test_search_course_keyword_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "linux")
        names = {item.get("name") for item in results}
        self.assertIn("Linux系统", names)

    def test_search_course_count_with_short_keyword_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "linux这个学期有几节课?")
        names = {item.get("name") for item in results}
        self.assertIn("Linux系统", names)
        by_name = {item.get("name"): item for item in results}
        self.assertEqual("庄老师", by_name["Linux系统"].get("teacherName"))

    def test_course_lookup_falls_back_to_all_semesters_when_current_semester_misses(self):
        results = data_store.search_schedule("Bearer t", "java是什么时候上的呢？我上过java吗")
        names = {item.get("name") for item in results}
        self.assertIn("Java程序设计", names)
        by_name = {item.get("name"): item for item in results}
        java_course = by_name["Java程序设计"]
        self.assertEqual("2024-2025 第 1 学期", java_course.get("semesterLabel"))
        self.assertEqual("all_semesters_fallback", java_course.get("queryScope"))
        self.assertEqual("current_semester_no_course_match", java_course.get("fallbackReason"))

    def test_search_date_and_session_schedule_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "2026年3月4日第3节有什么课")
        self.assertEqual(1, len(results))
        self.assertEqual("数据库原理", results[0].get("name"))

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
