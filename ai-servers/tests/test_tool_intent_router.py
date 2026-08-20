import unittest

from app.multi_agents.tool_intent_router_agent import tool_intent_router_agent


class ToolIntentRouterTest(unittest.TestCase):
    def test_ranks_schedule_tool_from_user_keywords(self):
        tools = [
            {"name": "java_schedule_api", "category": "campus_service", "purpose": "查询课程安排", "trigger": "课表"},
            {"name": "java_activity_api", "category": "campus_service", "purpose": "查询校园活动", "trigger": "活动"},
            {"name": "java_meeting_api", "category": "campus_service", "purpose": "查询会议", "trigger": "会议"},
        ]
        result = tool_intent_router_agent.select_candidates("明天上午有什么课", tools)

        self.assertEqual("java_schedule_api", result["candidateTools"][0]["name"])
        self.assertIn("有什么课", result["keywords"])
        self.assertLessEqual(result["candidateCount"], 3)

    def test_router_only_receives_enabled_tools(self):
        result = tool_intent_router_agent.select_candidates(
            "查询最近校园活动",
            [{"name": "java_activity_api", "purpose": "查询校园活动", "trigger": "活动"}],
        )

        self.assertEqual(["java_activity_api"], [item["name"] for item in result["candidateTools"]])


if __name__ == "__main__":
    unittest.main()
