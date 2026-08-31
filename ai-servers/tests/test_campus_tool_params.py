import unittest

from app.services.campus_tool_params import (
    build_activity_params,
    build_schedule_params,
    build_service_tool_request_urls,
    extract_activity_keyword,
    resolve_campus_tool_params,
)


class CampusToolParamsTest(unittest.TestCase):
    def test_named_activity_query_builds_search_params(self):
        params = build_activity_params("AI学习工坊这个活动怎么样")

        self.assertEqual("search", params["mode"])
        self.assertEqual("AI学习工坊", params["keyword"])
        self.assertEqual("PUBLISHED", params["status"])

    def test_activity_list_query_uses_list_mode(self):
        for query in ("今天校园有什么讲座", "现在有什么活动", "最近有什么活动", "校园有什么活动"):
            with self.subTest(query=query):
                params = build_activity_params(query)
                self.assertEqual("list", params["mode"], query)
                self.assertEqual("", params["keyword"], query)

    def test_extract_activity_keyword_handles_detail_query(self):
        self.assertEqual("AI学习工坊", extract_activity_keyword("AI学习工坊这个活动怎么样"))

    def test_schedule_current_week_default(self):
        params = build_schedule_params("我想查询今日课表")

        self.assertEqual("current_week", params["scope"])
        self.assertIsNone(params["week"])

    def test_schedule_course_keyword_scope(self):
        params = build_schedule_params("数据库今天有课吗")

        self.assertEqual("week", params["scope"])
        self.assertEqual("数据库", params["courseKeyword"])
        self.assertIsNotNone(params.get("date"))

    def test_llm_params_override_rule_defaults(self):
        params = resolve_campus_tool_params(
            "java_activity_api",
            "今天有什么活动",
            {"mode": "search", "keyword": "AI学习工坊"},
        )

        self.assertEqual("search", params["mode"])
        self.assertEqual("AI学习工坊", params["keyword"])

    def test_resolve_schedule_all_semesters(self):
        params = resolve_campus_tool_params(
            "java_schedule_api",
            "所有学期都有什么课",
        )

        self.assertEqual("all_semesters", params["scope"])

    def test_activity_list_builds_request_url(self):
        urls = build_service_tool_request_urls(
            "java_activity_api",
            {"mode": "list", "status": "PUBLISHED", "page": 1, "size": 10},
            base_url="http://localhost:8080",
        )

        self.assertEqual(1, len(urls))
        self.assertIn("/api/activities", urls[0])
        self.assertIn("status=PUBLISHED", urls[0])
        self.assertNotIn("/api/activities/search", urls[0])

    def test_secondhand_list_query_omits_keyword(self):
        from app.services.campus_tool_params import build_secondhand_params

        for query in ("现在有什么二手的东西", "有什么闲置物品", "最近有哪些旧物"):
            with self.subTest(query=query):
                params = build_secondhand_params(query)
                self.assertNotIn("keyword", params, query)
                self.assertEqual(1, params["current"])
                self.assertEqual(10, params["size"])


if __name__ == "__main__":
    unittest.main()
