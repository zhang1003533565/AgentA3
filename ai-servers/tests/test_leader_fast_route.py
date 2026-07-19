import json
import os
import unittest
from unittest.mock import patch

from app.multi_agents.catalog import LEADER_CALLABLE_AGENT_ORDER
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

    def test_clear_schedule_lookup_phrasings_remain_on_fast_route(self):
        queries = (
            "我今天的课表是什么",
            "明天有课吗",
            "想知道明天有没有课",
            "帮我查一下课表",
            "周三有没有课",
            "数据库今天有课吗",
        )

        for query in queries:
            with self.subTest(query=query):
                plan = self.agent.plan(query, chat_service=self.provider)
                self.assertEqual("call_tool", plan.action)
                self.assertEqual("java_schedule_api", plan.tool_name)
                self.assertEqual("rules", plan.route_mode)

        self.assertEqual(0, self.provider.calls)

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

    def test_mind_map_image_agent_returned_by_leader_model_is_accepted(self):
        plan = self.agent._parse_llm_plan({
            "intent": "diagram_mind_map_image",
            "target_agent": "diagram_mind_map_agent",
            "need_retrieval": False,
            "rag_strategy": "",
            "action": "delegate_agent",
            "tool_name": "",
            "route_reason": "用户偏好图解，生成学习路线思维导图。",
            "answer": "正在生成思维导图。",
        }, "")

        self.assertIsNotNone(plan)
        self.assertEqual("diagram_mind_map_image", plan.intent)
        self.assertEqual("diagram_mind_map_agent", plan.target_agent)
        self.assertEqual("delegate_agent", plan.action)
        self.assertEqual("llm", plan.route_mode)

    def test_every_catalogued_leader_callable_agent_passes_plan_validation(self):
        for agent_name in LEADER_CALLABLE_AGENT_ORDER:
            with self.subTest(agent_name=agent_name):
                plan = self.agent._parse_llm_plan({
                    "intent": "catalog_agent_test",
                    "target_agent": agent_name,
                    "action": "delegate_agent",
                    "route_reason": "验证后台可调用智能体与 Leader 路由白名单一致。",
                }, "")
                self.assertIsNotNone(plan)
                self.assertEqual(agent_name, plan.target_agent)

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

    def test_service_meta_questions_fall_back_to_llm(self):
        queries = (
            "课表接口怎么实现",
            "课表系统为何报错",
            "课表的设计原则是什么",
            "如何开发课表查询功能",
            "今天的课表为什么加载失败",
            "校园活动接口如何开发",
            "活动列表系统为什么报错",
            "会议列表接口返回哪些字段",
            "会议系统的设计原则是什么",
            "食堂菜单接口怎么实现",
            "食堂系统为什么报错",
        )

        for query in queries:
            with self.subTest(query=query):
                plan = self.agent.plan(query, chat_service=self.provider)
                self.assertEqual("llm_fallback", plan.intent)
                self.assertEqual("llm", plan.route_mode)

        self.assertEqual(len(queries), self.provider.calls)

    def test_broad_service_nouns_need_lookup_signal_or_typical_shorthand(self):
        fallback_queries = (
            "请介绍校园活动的发展历史",
            "分析会议列表的使用场景",
            "谈谈食堂文化",
            "解释课程安排方法",
            "告诉我食堂文化的发展历史",
            "给我介绍校园活动的教育意义",
            "告诉我今天食堂文化的发展历史",
            "今天校园活动有什么教育意义",
        )

        for query in fallback_queries:
            with self.subTest(query=query):
                plan = self.agent.plan(query, chat_service=self.provider)
                self.assertEqual("llm_fallback", plan.intent)
                self.assertEqual("llm", plan.route_mode)

        shorthand_cases = (
            ("校园活动", "java_activity_api"),
            ("我的会议", "java_meeting_api"),
            ("食堂菜单", "java_canteen_api"),
        )
        for query, tool_name in shorthand_cases:
            with self.subTest(query=query):
                plan = self.agent.plan(query, chat_service=self.provider)
                self.assertEqual("call_tool", plan.action)
                self.assertEqual(tool_name, plan.tool_name)
                self.assertEqual("rules", plan.route_mode)

        self.assertEqual(len(fallback_queries), self.provider.calls)

    def test_polite_prefixes_do_not_block_real_service_queries(self):
        cases = (
            ("告诉我今天食堂有什么菜", "java_canteen_api"),
            ("想知道明天有没有课", "java_schedule_api"),
            ("给我看看今天校园有什么讲座", "java_activity_api"),
            ("告诉我本周有什么会议", "java_meeting_api"),
        )

        for query, tool_name in cases:
            with self.subTest(query=query):
                plan = self.agent.plan(query, chat_service=self.provider)
                self.assertEqual("call_tool", plan.action)
                self.assertEqual(tool_name, plan.tool_name)
                self.assertEqual("rules", plan.route_mode)

        self.assertEqual(0, self.provider.calls)

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

    def test_capability_answer_only_lists_currently_enabled_items(self):
        plan = self.agent.plan(
            "你能生图吗，有哪些功能？",
            chat_service=self.provider,
            callable_catalog={
                "agents": [
                    {"name": "image_agent", "role": "图片智能体", "category": "image", "enabled": False},
                    {"name": "textbook_knowledge_agent", "role": "教材知识点智能体", "category": "textbook", "enabled": True},
                ],
                "tools": [
                    {"name": "java_schedule_api", "displayName": "课表查询", "purpose": "查询课表", "enabled": True},
                    {"name": "java_meeting_api", "displayName": "会议查询", "purpose": "查询会议", "enabled": False},
                ],
                "contentTools": [],
            },
        )

        self.assertEqual("leader_callable_catalog", plan.intent)
        self.assertEqual("rules", plan.route_mode)
        self.assertEqual(0, self.provider.calls)
        self.assertIn("教材知识点智能体", plan.answer)
        self.assertIn("课表查询", plan.answer)
        self.assertNotIn("图片智能体", plan.answer)
        self.assertNotIn("会议查询", plan.answer)
        self.assertNotIn("已关闭", plan.answer)

    def test_direct_image_capability_question_uses_verified_catalog_route(self):
        unavailable_plan = self.agent.plan(
            "你支持生图吗？",
            chat_service=self.provider,
            callable_catalog={"agents": [], "tools": [], "contentTools": []},
        )
        available_plan = self.agent.plan(
            "你支持生图吗？",
            chat_service=self.provider,
            callable_catalog={
                "agents": [{
                    "name": "image_agent",
                    "role": "图片智能体",
                    "category": "image",
                    "requiredModelModalities": ["image"],
                    "enabled": True,
                }],
                "tools": [],
                "contentTools": [],
            },
        )

        self.assertEqual("leader_callable_catalog", unavailable_plan.intent)
        self.assertEqual("rules", unavailable_plan.route_mode)
        self.assertEqual(0, self.provider.calls)
        self.assertIn("当前不支持生图", unavailable_plan.answer)
        self.assertIn("当前支持生图", available_plan.answer)
        self.assertIn("图片智能体", available_plan.answer)


if __name__ == "__main__":
    unittest.main()
