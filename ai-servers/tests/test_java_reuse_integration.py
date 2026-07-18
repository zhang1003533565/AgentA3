import json
import importlib
import unittest
from urllib.parse import parse_qs, urlparse

from app.services.data_store import data_store  # noqa: E402
from app.services import java_backend as java_backend_module  # noqa: E402
from app.models.schemas import ChatRequest  # noqa: E402
from app.services import chat_orchestrator  # noqa: E402
from app.langgraph.nodes import extract_keyword as extract_keyword_node_module  # noqa: E402
from app.model_providers.runtime_config import LlmRuntimeConfig, reset_active_llm_config, set_active_llm_config  # noqa: E402


class FakeUrlResponse:
    def __init__(self, payload):
        self.body = json.dumps(payload, ensure_ascii=False).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self):
        return self.body


class FakeJavaUrlopen:
    def __init__(self):
        self.calls = []
        self.path = ""
        self.status = None
        self.payload = None

    def __call__(self, request, timeout):
        parsed = urlparse(request.full_url)
        self.calls.append({
            "method": request.get_method(),
            "url": request.full_url,
            "path": parsed.path,
            "query": parse_qs(parsed.query),
            "authorization": request.get_header("Authorization"),
            "accept": request.get_header("Accept"),
            "timeout": timeout,
        })
        self.path = request.full_url
        self.status = None
        self.payload = None
        self.do_GET()
        if self.status != 200 or self.payload is None:
            raise AssertionError(f"unexpected fake Java request: {request.full_url}")
        return FakeUrlResponse(self.payload)

    def _send_json(self, payload):
        self.status = 200
        self.payload = payload

    def send_response(self, status):
        self.status = status

    def end_headers(self):
        return None

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
            if "黄焖鸡" in input_text:
                return json.dumps({
                    "intent": "campus_search",
                    "target_agent": "leader_agent",
                    "need_retrieval": True,
                    "rag_strategy": "",
                    "action": "call_tool",
                    "tool_name": "java_canteen_api",
                    "route_reason": "测试 LLM 路由到 Java 食堂数据。",
                    "answer": "",
                }, ensure_ascii=False)
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
        if "思维导图图片提示词智能体" in system_prompt:
            return "教学用思维导图图片，中心主题为操作系统进程调度，分支清晰，蓝绿配色。"
        return "textbook_knowledge_agent: 已生成，证据数=1"

    def extract_search_keyword(self, input_text):
        return "黄焖鸡"

    def answer(self, prompt, input_text, history, search_keyword, search_results):
        return f"已检索到{len(search_results)}条候选，关键词={search_keyword}"

    def stream_complete(self, system_prompt, user_prompt):
        yield self.complete(system_prompt, user_prompt)

class JavaReuseIntegrationTest(unittest.TestCase):
    def setUp(self):
        data_store.enabled = True
        data_store.java_base_url = "http://java-backend.test"
        data_store.timeout_seconds = 3
        data_store._retriever.disabled_until = None
        data_store.clear_tool_cache()
        self.fake_java_urlopen = FakeJavaUrlopen()
        self._original_urlopen = java_backend_module.urlopen
        java_backend_module.urlopen = self.fake_java_urlopen
        self._llm_token = set_active_llm_config(LlmRuntimeConfig(
            provider="deepseek",
            base_url="https://llm.test/v1",
            api_key="test-key",
            model="test-model",
        ))
        self._patched_modules = []
        self._patch_model_providers()

    def tearDown(self):
        calls = list(self.fake_java_urlopen.calls)
        java_backend_module.urlopen = self._original_urlopen
        data_store.clear_tool_cache()
        for module, old_get_chat_model_provider in reversed(self._patched_modules):
            module.get_chat_model_provider = old_get_chat_model_provider
        reset_active_llm_config(self._llm_token)
        for call in calls:
            self.assertEqual("GET", call["method"])
            self.assertEqual("java-backend.test", urlparse(call["url"]).netloc)
            self.assertTrue(call["authorization"])
            self.assertEqual("application/json", call["accept"])
            self.assertEqual(3, call["timeout"])

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
        calls_after_first_search = len(self.fake_java_urlopen.calls)
        requested_paths = {call["path"] for call in self.fake_java_urlopen.calls}
        self.assertEqual({
            "/api/v1/facility/list",
            "/api/v1/canteen-stall/list",
            "/api/v1/dish/list",
            "/api/v1/promotion-coupon/list",
        }, requested_paths)
        dish_queries = [
            call["query"]
            for call in self.fake_java_urlopen.calls
            if call["path"] == "/api/v1/dish/list"
        ]
        self.assertEqual([
            {"name": ["黄焖鸡"]},
            {"category": ["黄焖鸡"]},
            {"taste": ["黄焖鸡"]},
        ], dish_queries)

        cached_results = data_store.search_keyword("Bearer t", "黄焖鸡")

        self.assertEqual(results, cached_results)
        self.assertEqual(calls_after_first_search, len(self.fake_java_urlopen.calls))

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
        self.assertIn(
            {"allSemesters": ["true"]},
            [call["query"] for call in self.fake_java_urlopen.calls if call["path"] == "/api/schedule"],
        )

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
        self.assertEqual(
            [{}, {"allSemesters": ["true"]}],
            [call["query"] for call in self.fake_java_urlopen.calls if call["path"] == "/api/schedule"],
        )

    def test_search_date_and_session_schedule_via_java_api(self):
        results = data_store.search_schedule("Bearer t", "2026年3月4日第3节有什么课")
        self.assertEqual(1, len(results))
        self.assertEqual("数据库原理", results[0].get("name"))
        self.assertIn("/api/schedule/week/1", [call["path"] for call in self.fake_java_urlopen.calls])

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
        self.assertIn("思维导图图片", resp.answer)


if __name__ == "__main__":
    unittest.main()
