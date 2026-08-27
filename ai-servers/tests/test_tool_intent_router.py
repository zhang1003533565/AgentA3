import unittest

from app.multi_agents.tool_intent_router_agent import tool_intent_router_agent
from app.services.tool_index import tool_index


class ToolIntentRouterTest(unittest.TestCase):
    def test_extracts_structured_intent_without_selecting_tools(self):
        result = tool_intent_router_agent.extract("明天上午我有几节课？分别在哪些教室？")

        self.assertIn("明天", result["entities"]["时间"])
        self.assertIn("几节", result["entities"]["数量"])
        self.assertIn("分别", result["constraints"])
        self.assertLessEqual(len(result["queryVariants"]), 3)

    def test_tool_index_ranks_schedule_tool_from_intent(self):
        tools = [
            {"name": "java_schedule_api", "category": "campus_service", "purpose": "查询课程安排", "trigger": "课表"},
            {"name": "java_activity_api", "category": "campus_service", "purpose": "查询校园活动", "trigger": "活动"},
            {"name": "java_meeting_api", "category": "campus_service", "purpose": "查询会议", "trigger": "会议"},
        ]
        intent = tool_intent_router_agent.extract("明天上午有什么课")
        result = tool_index.search("明天上午有什么课", tools, intent_result=intent)

        self.assertEqual("java_schedule_api", result["candidateTools"][0]["name"])
        self.assertIn("有什么课", result["keywords"])
        self.assertLessEqual(result["candidateCount"], 3)

    def test_router_only_receives_enabled_tools(self):
        intent = tool_intent_router_agent.extract("查询最近校园活动")
        result = tool_index.search(
            "查询最近校园活动",
            [{"name": "java_activity_api", "purpose": "查询校园活动", "trigger": "活动"}],
            intent_result=intent,
        )

        self.assertEqual(["java_activity_api"], [item["name"] for item in result["candidateTools"]])

    def test_capability_query_returns_only_capability_tool(self):
        intent = tool_intent_router_agent.extract("你有哪些工具能力")
        result = tool_index.search(
            "你有哪些工具能力",
            [
                {"name": "java_activity_api", "purpose": "查询校园活动"},
                {"name": "tool_capability_query", "purpose": "查询当前已启用工具能力"},
                {"name": "generate_image_tool", "purpose": "生成图片"},
            ],
            intent_result=intent,
        )

        self.assertEqual("capability_inquiry", result["intent"])
        self.assertEqual(["tool_capability_query"], [item["name"] for item in result["candidateTools"]])
        self.assertEqual(1, result["candidateCount"])

    def test_capability_query_understands_tool_list_wording(self):
        result = tool_intent_router_agent.extract("查询当前可用的工具列表")

        self.assertEqual("capability_inquiry", result["intent"])


if __name__ == "__main__":
    unittest.main()
