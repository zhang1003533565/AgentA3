import json
import os
import unittest
from unittest.mock import patch

from app.multi_agents.leader_agent.agent import LeaderAgent


class RecordingChatService:
    def __init__(self) -> None:
        self.calls = 0

    def complete(self, system_prompt, user_prompt):
        self.calls += 1
        return json.dumps(
            {
                "intent": "llm_fallback",
                "target_agent": "leader_agent",
                "need_retrieval": False,
                "rag_strategy": "",
                "action": "direct_answer",
                "tool_name": "",
                "route_reason": "测试中的模型兜底路由。",
                "answer": "模型兜底回答。",
            },
            ensure_ascii=False,
        )


class LeaderFastRouteTest(unittest.TestCase):
    def setUp(self) -> None:
        self.agent = LeaderAgent()
        self.provider = RecordingChatService()
        self.fast_route_patch = patch.dict(
            os.environ,
            {"AI_LEADER_FAST_ROUTE_ENABLED": "true"},
        )
        self.fast_route_patch.start()

    def tearDown(self) -> None:
        self.fast_route_patch.stop()

    def test_explicit_schedule_query_skips_llm_and_preserves_plan_contract(self):
        plan = self.agent.plan("我想查询今日课表", chat_service=self.provider)

        self.assertEqual(0, self.provider.calls)
        self.assertEqual("schedule", plan.intent)
        self.assertEqual("leader_agent", plan.target_agent)
        self.assertEqual("call_tool", plan.action)
        self.assertEqual("java_schedule_api", plan.tool_name)
        self.assertFalse(plan.need_retrieval)
        self.assertEqual("rules", plan.route_mode)
        self.assertIn("高置信度", plan.route_reason)
        self.assertEqual("rules", plan.to_dict()["route_mode"])

    def test_exact_smalltalk_skips_llm_but_greeting_plus_task_does_not(self):
        greeting = self.agent.plan("你好", chat_service=self.provider)
        task = self.agent.plan("你好，帮我分析这门课", chat_service=self.provider)

        self.assertEqual("smalltalk", greeting.intent)
        self.assertEqual("direct_answer", greeting.action)
        self.assertEqual("rules", greeting.route_mode)
        self.assertIn("Leader 智能体", greeting.answer)
        self.assertEqual("llm_fallback", task.intent)
        self.assertEqual("llm", task.route_mode)
        self.assertEqual(1, self.provider.calls)

    def test_explicit_service_queries_use_matching_java_tool_without_llm(self):
        cases = (
            ("今天校园有什么讲座", "activity_query", "java_activity_api"),
            ("查询我的会议列表", "meeting_query", "java_meeting_api"),
            ("食堂今天吃什么", "canteen_query", "java_canteen_api"),
        )

        for query, intent, tool_name in cases:
            with self.subTest(query=query):
                plan = self.agent.plan(query, chat_service=self.provider)
                self.assertEqual(intent, plan.intent)
                self.assertEqual("call_tool", plan.action)
                self.assertEqual(tool_name, plan.tool_name)
                self.assertEqual("rules", plan.route_mode)

        self.assertEqual(0, self.provider.calls)

    def test_ambiguous_or_multi_intent_queries_fall_back_to_llm(self):
        queries = (
            "帮我看看今天怎么安排比较好",
            "下午有课吗，顺便看看有什么校园活动",
            "帮我总结会议纪要",
            "帮我制定课程安排",
            "帮我策划一个校园活动",
            "帮我设计食堂管理系统",
        )

        for query in queries:
            with self.subTest(query=query):
                plan = self.agent.plan(query, chat_service=self.provider)
                self.assertEqual("llm_fallback", plan.intent)
                self.assertEqual("llm", plan.route_mode)

        self.assertEqual(len(queries), self.provider.calls)

    def test_contextual_followup_falls_back_but_standalone_schedule_remains_fast(self):
        context = {
            "turns": [{"user": "查询会议", "assistant": "找到一场会议"}],
            "lastSubjects": ["教学例会"],
        }

        followup = self.agent.plan(
            "刚才我的会议还有吗",
            chat_service=self.provider,
            conversation_context=context,
        )
        standalone = self.agent.plan(
            "今天有什么课",
            chat_service=self.provider,
            conversation_context=context,
        )

        self.assertEqual("llm_fallback", followup.intent)
        self.assertEqual("llm", followup.route_mode)
        self.assertEqual("java_schedule_api", standalone.tool_name)
        self.assertEqual("rules", standalone.route_mode)
        self.assertEqual(1, self.provider.calls)

    def test_feature_switch_disables_fast_route(self):
        with patch.dict(os.environ, {"AI_LEADER_FAST_ROUTE_ENABLED": "false"}):
            plan = self.agent.plan("今天有什么课", chat_service=self.provider)

        self.assertEqual("llm_fallback", plan.intent)
        self.assertEqual("llm", plan.route_mode)
        self.assertEqual(1, self.provider.calls)

    def test_disabled_tool_in_catalog_falls_back_to_llm(self):
        plan = self.agent.plan(
            "食堂今天吃什么",
            chat_service=self.provider,
            callable_catalog={
                "tools": [
                    {"name": "java_canteen_api", "enabled": False},
                ]
            },
        )

        self.assertEqual("llm_fallback", plan.intent)
        self.assertEqual("llm", plan.route_mode)
        self.assertEqual(1, self.provider.calls)


if __name__ == "__main__":
    unittest.main()
