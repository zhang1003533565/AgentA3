import copy
import importlib
import json
import os
import unittest
from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.main import app
from app.model_providers import factory as model_provider_factory
from app.model_providers.multimodal import build_multimodal_human_content, extract_image_references
from app.model_providers.runtime_config import LlmRuntimeConfig
from app.multi_agents.catalog import AGENT_ORDER, LEADER_CALLABLE_AGENT_ORDER
from app.multi_agents.ppt_structure_agent.agent import normalize_structure_answer
from app.multi_agents.ppt_outline_agent.agent import normalize_ppt_outline_answer


INTERNAL_LEARNING_WORKFLOW_AGENTS = {
    "learning_path_agent",
    "python_practice_set_agent",
    "python_code_lab_agent",
    "extension_reading_agent",
    "resource_review_agent",
    "resource_package_agent",
}


class ConfiguredTestClient(TestClient):
    def request(self, method, url, **kwargs):
        payload = kwargs.get("json")
        if str(url).startswith("/internal/rag/query") and isinstance(payload, dict):
            payload = copy.deepcopy(payload)
            metadata = payload.get("metadata")
            metadata = dict(metadata) if isinstance(metadata, dict) else {}
            configs = metadata.get("agentModelConfigs")
            configs = dict(configs) if isinstance(configs, dict) else {}
            for agent_name in AGENT_ORDER:
                configs.setdefault(agent_name, {
                    "configPrefix": "ai.agent.{}".format(agent_name),
                    "provider": "deepseek",
                    "baseUrl": "https://llm.test/v1",
                    "apiKey": "test-key",
                    "model": "test-model",
                    "tested": True,
                })
            metadata["agentModelConfigs"] = configs
            payload["metadata"] = metadata
            kwargs["json"] = payload
        return super().request(method, url, **kwargs)


class RagApiRoutesTest(unittest.TestCase):
    def setUp(self):
        FakeImageProvider.requests.clear()
        self._old_internal_token = os.environ.get("AI_INTERNAL_TOKEN")
        os.environ["AI_INTERNAL_TOKEN"] = "test-internal-token"
        self.client = ConfiguredTestClient(app)
        self.headers = {
            "Authorization": "Bearer test-token",
            "X-AI-Internal-Token": "test-internal-token",
            "X-AI-Provider": "deepseek",
            "X-AI-Base-Url": "https://llm.test/v1",
            "X-AI-Api-Key": "test-key",
            "X-AI-Model": "test-model",
        }
        self.agent_model_configs = {
            item["name"]: {
                "configPrefix": f"ai.agent.{item['name']}",
                "provider": "deepseek",
                "baseUrl": "https://llm.test/v1",
                "apiKey": "test-key",
                "model": "test-model",
                "tested": True,
            }
            for item in ({"name": name} for name in AGENT_ORDER)
        }
        self._patched_modules = []
        self._patched_image_modules = []
        self._patch_model_providers()
        self._patch_image_provider()
        rag_routes = importlib.import_module("app.api.routes.rag")
        self._rag_routes = rag_routes
        self._old_search_service_tool_with_meta = rag_routes.data_store.search_service_tool_with_meta
        rag_routes.data_store.search_service_tool_with_meta = (
            lambda authorization, tool_name, input_text: (
                rag_routes.data_store.search_service_tool(authorization, tool_name, input_text),
                {"toolCache": {}},
            )
        )

    def tearDown(self):
        self._rag_routes.data_store.search_service_tool_with_meta = self._old_search_service_tool_with_meta
        for module, old_get_qwen_image_provider in reversed(self._patched_image_modules):
            module.get_qwen_image_provider = old_get_qwen_image_provider
        for module, old_get_chat_model_provider in reversed(self._patched_modules):
            module.get_chat_model_provider = old_get_chat_model_provider
        if self._old_internal_token is None:
            os.environ.pop("AI_INTERNAL_TOKEN", None)
        else:
            os.environ["AI_INTERNAL_TOKEN"] = self._old_internal_token

    def _patch_model_providers(self):
        module_names = [
            "app.multi_agents.leader_agent.agent",
            "app.multi_agents.runtime",
            "app.langgraph.nodes.extract_keyword",
        ]
        for module_name in module_names:
            module = importlib.import_module(module_name)
            self._patched_modules.append((module, module.get_chat_model_provider))
            module.get_chat_model_provider = lambda provider=FakeRagModelProvider(): provider

    def _patch_image_provider(self):
        module_names = ["app.multi_agents.image_agent.agent"]
        for module_name in module_names:
            module = importlib.import_module(module_name)
            self._patched_image_modules.append((module, module.get_qwen_image_provider))
            module.get_qwen_image_provider = lambda: FakeImageProvider()

    def _install_service_tool_stub(self, rag_routes, stub):
        old = rag_routes.data_store.search_service_tool_with_meta
        rag_routes.data_store.search_service_tool_with_meta = (
            lambda authorization, tool_name, query: (
                stub(authorization, tool_name, query),
                {"toolCache": {}},
            )
        )
        return old

    def test_agent_is_available_only_after_complete_model_config_passes_test(self):
        request = SimpleNamespace(metadata={
            "agentToggles": {"image_agent": True},
            "agentModelConfigs": {
                "image_agent": {
                    "provider": "qwen",
                    "baseUrl": "https://image.test/v1",
                    "apiKey": "test-key",
                    "model": "image-model",
                    "tested": True,
                },
            },
        })

        self.assertTrue(self._rag_routes._is_agent_enabled(request, "image_agent"))

        request.metadata["agentModelConfigs"]["image_agent"]["tested"] = False
        self.assertFalse(self._rag_routes._is_agent_enabled(request, "image_agent"))

        request.metadata["agentModelConfigs"]["image_agent"]["tested"] = True
        request.metadata["agentModelConfigs"]["image_agent"]["apiKey"] = ""
        self.assertFalse(self._rag_routes._is_agent_enabled(request, "image_agent"))

    def test_visual_tool_without_internal_image_binding_is_not_advertised_as_available(self):
        request = SimpleNamespace(metadata={
            "agentToggles": {"image_agent": True},
            "agentModelConfigs": {},
        })

        catalog = self._rag_routes._build_leader_callable_catalog(request)
        image_tool = next(item for item in catalog["tools"] if item["name"] == "generate_image_tool")

        self.assertFalse(image_tool["enabled"])
        self.assertNotIn("image_agent", {item["name"] for item in catalog["agents"]})

    def test_file_export_tool_can_reuse_leader_model_when_planner_has_no_separate_binding(self):
        request = SimpleNamespace(metadata={
            "agentToggles": {"file_content_planner_agent": True},
            "agentModelConfigs": {},
            "toolToggles": {"generated_export_tools": True},
        })

        catalog = self._rag_routes._build_leader_callable_catalog(request)
        export_tool = next(item for item in catalog["tools"] if item["name"] == "generated_export_tools")

        self.assertTrue(export_tool["enabled"])

    def test_ai_ppt_tool_is_configurable_but_not_leader_callable_before_wiring(self):
        request = SimpleNamespace(metadata={
            "toolToggles": {"ai_ppt_generation_tool": False},
        })

        catalog = self._rag_routes._build_leader_callable_catalog(request)
        content_tool = next(
            item
            for item in catalog["tools"]
            if item["name"] == "ai_ppt_generation_tool"
        )

        self.assertFalse(content_tool["enabled"])
        self.assertEqual("unwired", content_tool["invocation"])
        self.assertEqual("registered", content_tool["status"])
        self.assertIn("ai_ppt_generation_tool", {item["name"] for item in catalog["tools"]})

    def test_file_transform_action_forces_real_export_tool(self):
        request = SimpleNamespace(metadata={
            "interactionType": "transform",
            "requestedOutputType": "docx",
            "sourceMessageId": 88,
            "sourceMessageContent": "# 数据结构\n\n- 栈：后进先出",
        })

        plan = self._rag_routes._requested_file_transform_plan(request)

        self.assertIsNotNone(plan)
        self.assertEqual("call_tool", plan.action)
        self.assertEqual("generated_export_tools", plan.tool_name)
        self.assertEqual("rules", plan.route_mode)

    def test_second_source_option_is_expanded_to_explicit_model_authorization(self):
        context = {
            "turns": [{
                "user": "请生成 Python 发展历史知识点",
                "assistant": "请选择两种来源方式之一：1. 上传材料或选择知识库内容；2. 授权模型自行生成。",
                "metadata": {"knowledgeSourceMode": "source_selection_required"},
            }],
        }

        expanded = self._rag_routes._contextualize_followup_input("第二种方式", context)

        self.assertIn("明确授权模型", expanded)
        self.assertIn("Python 发展历史", expanded)

    def test_legacy_third_source_option_is_expanded_to_explicit_model_authorization(self):
        context = {
            "turns": [
                {
                    "user": "请生成 Python 发展历史知识点",
                    "assistant": "请选择来源。",
                    "metadata": {},
                },
                {
                    "user": "第二种方式",
                    "assistant": "1. 上传材料；2. 选择知识库内容；3. 授权模型自行生成。",
                    "metadata": {"knowledgeSourceMode": "source_selection_required"},
                },
            ],
        }

        expanded = self._rag_routes._contextualize_followup_input("3", context)

        self.assertIn("明确授权模型", expanded)
        self.assertIn("Python 发展历史", expanded)

    def test_plain_number_is_not_rewritten_without_source_selection_context(self):
        self.assertEqual("3", self._rag_routes._contextualize_followup_input("3", {"turns": []}))

    def test_confirmed_model_source_is_reused_for_short_continuation(self):
        context = {
            "turns": [{
                "user": "我授权模型生成 Python 发展历史知识材料",
                "assistant": "已生成知识材料。",
                "metadata": {
                    "knowledgeSourceMode": "model_generated",
                    "knowledgeTopic": "Python 发展历史",
                },
            }],
        }

        expanded = self._rag_routes._contextualize_followup_input("继续生成", context)

        self.assertIn("此前已经授权模型", expanded)
        self.assertIn("Python 发展历史", expanded)

    def test_knowledge_source_clarification_does_not_offer_file_export_actions(self):
        response = self._rag_routes.RagQueryResponse(
            strategy="direct_agent",
            answer="请选择材料来源。",
            answerType="markdown",
            metadata={
                "executedAgent": "textbook_knowledge_agent",
                "intent": "knowledge_source_clarification",
                "knowledgeSourceMode": "source_selection_required",
                "toolToggles": {"generated_export_tools": True},
            },
        )

        decorated = self._rag_routes._decorate_output_response(response)

        self.assertEqual([], decorated.outputMeta["followUpActions"])
        self.assertEqual("", decorated.outputMeta["choicePrompt"])

    def test_typed_word_export_without_source_is_clarified_by_content_planner_model(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "给我导出word",
                "agentName": "leader_agent",
                "metadata": {"agentModelConfigs": self.agent_model_configs},
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("file_content_planner_agent", payload["metadata"]["executedAgent"])
        self.assertEqual("file_source_clarification", payload["metadata"]["intent"])
        self.assertEqual([], payload["attachments"])
        self.assertIn("哪一段内容", payload["answer"])

    def test_selected_source_runs_content_planner_then_returns_only_requested_word_file(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "给我导出word",
                "agentName": "leader_agent",
                "metadata": {
                    "requestedOutputType": "docx",
                    "sourceMessageId": 88,
                    "sourceMessageContent": "# 数据结构\n\n- 栈：后进先出",
                    "agentModelConfigs": self.agent_model_configs,
                },
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("generated_export_tools", payload["metadata"]["executedAgent"])
        self.assertEqual("file_content_planner_agent", payload["metadata"]["promptAgent"])
        self.assertEqual(["docx"], [item["ext"] for item in payload["attachments"]])
        self.assertEqual(["leader_route", "agent_answer", "tool_call"], [item["stage"] for item in payload["trace"]])

    def test_free_text_word_export_skips_clarification_message_and_uses_previous_substantive_candidate(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "给我生成word格式",
                "agentName": "leader_agent",
                "metadata": {
                    "sourceMessageCandidates": [
                        {"messageId": 88, "content": "请选择知识来源方式：1. 上传材料；2. 授权模型自行生成。", "answerType": "text"},
                        {"messageId": 87, "content": "# Python 发展历史\n\n- 1991 年发布首个公开版本。", "answerType": "markdown"},
                    ],
                    "agentModelConfigs": self.agent_model_configs,
                },
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(["docx"], [item["ext"] for item in payload["attachments"]])
        self.assertEqual(87, payload["metadata"]["selectedSourceMessageId"])
        self.assertEqual("generated_export_tools", payload["metadata"]["executedAgent"])

    def test_file_format_actions_only_include_enabled_real_tools(self):
        actions = self._rag_routes._file_format_follow_up_actions(
            "question_bank",
            {
                "toolToggles": {
                    "generated_export_tools": True,
                    "excel_export_tool": True,
                    "docx_export_tool": False,
                    "markdown_export_tool": True,
                },
            },
            "textbook_question_single_choice_agent",
        )

        self.assertEqual(["Excel 题库", "Markdown 题库"], [item["label"] for item in actions])
        self.assertEqual(["xlsx", "md"], [item["outputType"] for item in actions])

    def test_agent_answer_does_not_auto_export_every_file_format_before_user_selects_one(self):
        response = self._rag_routes.RagQueryResponse(
            strategy="direct_agent",
            answer="# Python 入门学习路线\n\n- 基础语法\n- 数据结构",
            answerType="markdown",
            metadata={
                "executedAgent": "textbook_knowledge_agent",
                "toolToggles": {"generated_export_tools": True},
            },
        )

        decorated = self._rag_routes._decorate_output_response(response)

        self.assertEqual([], decorated.attachments)
        self.assertEqual("output_format_not_selected", decorated.outputMeta["generatedExports"]["reason"])
        self.assertEqual(
            ["docx", "xlsx", "md", "pptx"],
            [item["outputType"] for item in decorated.outputMeta["followUpActions"]],
        )

    def test_removed_strategy_routes_return_404(self):
        response = self.client.get("/internal/rag/strategies", headers=self.headers)

        self.assertEqual(404, response.status_code)

    def test_query_endpoint_runs_strategy(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "统计食堂优惠券数量",
                "keyword": "优惠券",
                "ragStrategy": "text_to_sql",
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("text_to_sql", payload["strategy"])
        self.assertTrue(payload["metadata"]["readonly"])
        self.assertIn("SELECT", payload["metadata"]["sql"])

    def test_rag_routes_require_authorization(self):
        response = self.client.get(
            "/internal/rag/agents",
            headers={"X-AI-Internal-Token": "test-internal-token"},
        )

        self.assertEqual(401, response.status_code)

    def test_removed_knowledge_base_routes_return_404(self):
        for method, path in (
            ("get", "/internal/rag/knowledge-bases"),
            ("post", "/internal/rag/knowledge-bases"),
            ("get", "/internal/rag/documents"),
            ("post", "/internal/rag/documents"),
            ("post", "/internal/rag/recall-test"),
            ("get", "/internal/rag/vector-store/health"),
            ("get", "/internal/rag/embedding/health"),
            ("get", "/internal/rag/graph-store/health"),
            ("post", "/internal/rag/evaluate"),
        ):
            request = getattr(self.client, method)
            if method == "post":
                response = request(path, headers=self.headers, json={})
            else:
                response = request(path, headers=self.headers)
            self.assertEqual(404, response.status_code, path)

    def test_capabilities_endpoint_describes_runtime_framework(self):
        response = self.client.get("/internal/rag/capabilities", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertFalse(payload["query"]["localKnowledgeBase"])
        self.assertFalse(payload["query"]["localRagStrategies"])
        self.assertTrue(payload["query"]["noLocalFallback"])
        self.assertFalse(payload["agentInvocation"]["ragStrategyAccepted"])
        self.assertNotIn("indexing", payload)
        self.assertNotIn("retrieval", payload)
        self.assertIn("textbook_knowledge_agent", payload["agents"])
        self.assertIn("leader_agent", payload["agents"])
        self.assertTrue(
            INTERNAL_LEARNING_WORKFLOW_AGENTS.isdisjoint(payload["agents"])
        )
        catalog = self.client.get("/internal/rag/agents", headers=self.headers).json()
        callable_names = {
            item["name"] for item in catalog["leaderCallableCatalog"]["agents"]
        }
        self.assertEqual(set(payload["agents"]), {"leader_agent", *callable_names})

    def test_framework_endpoint_describes_full_runtime_layout(self):
        response = self.client.get("/internal/rag/framework", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        coverage = {item["name"]: item for item in payload["coverage"]}
        self.assertGreaterEqual(
            set(coverage),
            {
                "generated_export_tools",
                "question_bank_validation",
                "agent_enabled_gate",
                "campus_service_tools",
                "profile_summary_agent",
            },
        )
        self.assertTrue(all(item["status"] == "implemented" for item in coverage.values()))
        self.assertNotIn("strategies", payload["runtimeFolders"])
        self.assertIn("app/rag/document_conversion", payload["runtimeFolders"]["documentConversion"])
        self.assertIn("app/rag/structured", payload["runtimeFolders"]["textToSql"])
        self.assertNotIn("embeddingProviders", payload)
        self.assertNotIn("vectorStores", payload)
        self.assertNotIn("graphStores", payload)
        self.assertNotIn("indexing", payload)
        self.assertNotIn("knowledgeBaseDir", {item["name"] for item in payload["runtimeEnv"]})
        self.assertIn("xiaomi", {item["name"] for item in payload["modelProviders"]})
        self.assertIn("qwen", {item["name"] for item in payload["modelProviders"]})
        self.assertIn("xfyun", {item["name"] for item in payload["modelProviders"]})
        self.assertIn("volcengine", {item["name"] for item in payload["modelProviders"]})

    def test_model_factory_selects_xiaomi_provider(self):
        class FakeXiaomiProvider:
            def __init__(self, config):
                self.config = config

        old_xiaomi_provider = model_provider_factory.XiaomiProvider
        try:
            model_provider_factory.XiaomiProvider = FakeXiaomiProvider
            provider = model_provider_factory.build_chat_model_provider(LlmRuntimeConfig(
                provider="xiaomi",
                base_url="https://api.xiaomimimo.com/v1",
                api_key="test-key",
                model="mimo-v2.5-pro",
            ))
        finally:
            model_provider_factory.XiaomiProvider = old_xiaomi_provider

        self.assertIsInstance(provider, FakeXiaomiProvider)
        self.assertEqual("mimo-v2.5-pro", provider.config.model)


    def test_model_factory_selects_qwen_provider(self):
        class FakeQwenProvider:
            def __init__(self, config):
                self.config = config

        old_qwen_provider = model_provider_factory.QwenProvider
        try:
            model_provider_factory.QwenProvider = FakeQwenProvider
            provider = model_provider_factory.build_chat_model_provider(LlmRuntimeConfig(
                provider="qwen",
                base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
                api_key="test-key",
                model="qwen-vl-plus",
            ))
        finally:
            model_provider_factory.QwenProvider = old_qwen_provider

        self.assertIsInstance(provider, FakeQwenProvider)
        self.assertEqual("qwen-vl-plus", provider.config.model)

    def test_model_factory_selects_xfyun_provider(self):
        class FakeXfyunProvider:
            def __init__(self, config):
                self.config = config

        old_xfyun_provider = model_provider_factory.XfyunProvider
        try:
            model_provider_factory.XfyunProvider = FakeXfyunProvider
            provider = model_provider_factory.build_chat_model_provider(LlmRuntimeConfig(
                provider="spark",
                base_url="https://spark-api-open.xf-yun.com/v1",
                api_key="test-key",
                model="4.0Ultra",
            ))
        finally:
            model_provider_factory.XfyunProvider = old_xfyun_provider

        self.assertIsInstance(provider, FakeXfyunProvider)
        self.assertEqual("4.0Ultra", provider.config.model)

    def test_model_factory_selects_volcengine_provider(self):
        class FakeVolcengineProvider:
            def __init__(self, config):
                self.config = config

        old_volcengine_provider = model_provider_factory.VolcengineProvider
        try:
            model_provider_factory.VolcengineProvider = FakeVolcengineProvider
            provider = model_provider_factory.build_chat_model_provider(LlmRuntimeConfig(
                provider="ark",
                base_url="https://ark.cn-beijing.volces.com/api/v3",
                api_key="test-key",
                model="deepseek-v3",
            ))
        finally:
            model_provider_factory.VolcengineProvider = old_volcengine_provider

        self.assertIsInstance(provider, FakeVolcengineProvider)
        self.assertEqual("deepseek-v3", provider.config.model)

    def test_model_provider_catalog_contains_xfyun_modalities(self):
        response = self.client.get("/internal/models/providers", headers=self.headers)

        self.assertEqual(200, response.status_code)
        providers = {item["id"]: item for item in response.json()["providers"]}
        self.assertIn("xfyun", providers)
        self.assertGreaterEqual({"text", "vision", "image", "video"}, set(providers["xfyun"]["capabilities"]))
        self.assertEqual("implemented", providers["xfyun"]["capabilities"]["text"]["status"])
        self.assertEqual("planned", providers["xfyun"]["capabilities"]["image"]["status"])

    def test_model_provider_catalog_contains_volcengine_deepseek_models(self):
        response = self.client.get("/internal/models/providers", headers=self.headers)

        self.assertEqual(200, response.status_code)
        providers = {item["id"]: item for item in response.json()["providers"]}
        self.assertIn("volcengine", providers)
        text_models = providers["volcengine"]["capabilities"]["text"]["models"]
        self.assertEqual({"deepseek-v3", "deepseek-r1"}, {item["id"] for item in text_models})
        self.assertEqual("https://ark.cn-beijing.volces.com/api/v3", providers["volcengine"]["baseUrl"])

    def test_qwen_multimodal_content_extracts_image_urls(self):
        text, image_urls = extract_image_references(
            "请分析这张图 ![图](https://example.com/campus.png) 以及 data:image/png;base64,AAAA"
        )

        self.assertIn("请分析这张图", text)
        self.assertEqual([
            "https://example.com/campus.png",
            "data:image/png;base64,AAAA",
        ], image_urls)

        content = build_multimodal_human_content("看图回答 ![图](https://example.com/a.jpg)")
        self.assertIsInstance(content, list)
        self.assertEqual("text", content[0]["type"])
        self.assertEqual("image_url", content[1]["type"])
        self.assertEqual("https://example.com/a.jpg", content[1]["image_url"]["url"])

    def test_agents_endpoint_exposes_skill_catalog(self):
        response = self.client.get("/internal/rag/agents", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        names = {item["name"] for item in payload["agents"]}
        self.assertEqual(payload["total"], len(names))
        self.assertGreaterEqual(payload["total"], 21)
        self.assertIn("textbook_knowledge_agent", names)
        self.assertIn("textbook_question_single_choice_agent", names)
        self.assertIn("textbook_question_programming_agent", names)
        self.assertIn("meeting_controller_agent", names)
        self.assertIn("meeting_voice_broadcast_agent", names)
        self.assertIn("ppt_outline_agent", names)
        self.assertIn("ppt_structure_agent", names)
        self.assertIn("ppt_review_agent", names)
        self.assertIn("ppt_image_agent", names)
        self.assertNotIn("textbook_question_bank_agent", names)
        self.assertNotIn("md_knowledge_agent", names)
        self.assertNotIn("answer_agent", names)
        self.assertNotIn("retriever_agent", names)
        textbook_agent = next(item for item in payload["agents"] if item["name"] == "textbook_knowledge_agent")
        self.assertIn("skill.md", textbook_agent["files"]["skill"])
        self.assertIn("教材知识点智能体 Skill", textbook_agent["documents"]["skill"])
        self.assertFalse(textbook_agent["needRetrieval"])
        self.assertEqual([], textbook_agent["supportedRagStrategies"])
        leader_agent = next(item for item in payload["agents"] if item["name"] == "leader_agent")
        self.assertFalse(leader_agent["needRetrieval"])
        self.assertEqual([], leader_agent["supportedRagStrategies"])
        self.assertIn("questionBank", payload["workflow"])
        self.assertEqual("agentName", payload["invocation"]["parameter"])

        leader_callable_names = {
            item["name"] for item in payload["leaderCallableCatalog"]["agents"]
        }
        self.assertTrue(INTERNAL_LEARNING_WORKFLOW_AGENTS.isdisjoint(leader_callable_names))
        self.assertGreaterEqual(
            leader_callable_names,
            {"textbook_knowledge_agent", "ppt_outline_agent"},
        )
        self.assertTrue({
            "image_agent",
            "mind_map_agent",
            "architecture_prompt_agent",
            "diagram_flowchart_prompt_agent",
            "diagram_activity_prompt_agent",
            "knowledge_graph_prompt_agent",
            "ppt_image_agent",
        }.isdisjoint(leader_callable_names))
        leader_tool_names = {item["name"] for item in payload["leaderCallableCatalog"]["tools"]}
        self.assertGreaterEqual(leader_tool_names, set(self._rag_routes.VISUAL_GENERATION_TOOL_NAMES))
        internal_agents = {
            item["name"]: item
            for item in payload["agents"]
            if item["name"] in INTERNAL_LEARNING_WORKFLOW_AGENTS
        }
        self.assertEqual(set(internal_agents), INTERNAL_LEARNING_WORKFLOW_AGENTS)
        for agent in internal_agents.values():
            self.assertEqual("workflow_internal", agent["executionMode"])
            self.assertEqual(
                "workflow_internal",
                agent["invokeExample"]["executionMode"],
            )

    def test_agent_detail_endpoint_returns_single_agent(self):
        response = self.client.get("/internal/rag/agents/leader_agent", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("leader_agent", payload["name"])
        self.assertIn("Leader", payload["role"])

    def test_query_endpoint_can_execute_selected_specialist_agent(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "数据结构中的栈与队列",
                "agentName": "ppt_outline_agent",
                "metadata": {"agentModelConfigs": self.agent_model_configs},
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("direct_agent", payload["strategy"])
        self.assertEqual("ppt_outline_agent", payload["metadata"]["agentName"])
        self.assertTrue(payload["metadata"]["retrievalSkipped"])
        self.assertFalse(payload["metadata"]["needRetrieval"])
        self.assertIn("PPT 大纲", payload["answer"])
        self.assertIn("### 大纲信息", payload["answer"])
        self.assertIn("- 使用场景：", payload["answer"])
        self.assertIn("- 受众：", payload["answer"])
        self.assertNotIn("讲解目标", payload["answer"])
        self.assertNotIn("页面内容建议", payload["answer"])
        self.assertNotIn("课堂互动建议", payload["answer"])
        self.assertEqual("direct_agent", payload["trace"][-1]["stage"])
        self.assertEqual("direct_agent", payload["metadata"]["executionMode"])

    def test_image_attachment_automatically_calls_bound_vision_tool(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "请分析这张图片",
                "agentName": "leader_agent",
                "attachments": [{
                    "type": "image",
                    "mimeType": "image/png",
                    "name": "screen.png",
                    "url": "https://files.test/screen.png",
                }],
                "metadata": {"agentModelConfigs": self.agent_model_configs},
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("recognize_image_tool", payload["strategy"])
        self.assertEqual("image_analysis", payload["answerType"])
        self.assertEqual("vision_agent", payload["metadata"]["boundAgent"])
        self.assertEqual("attachment", payload["metadata"]["routeMode"])

    def test_stream_image_attachment_uses_same_vision_tool_route(self):
        response = self.client.post(
            "/internal/rag/query/stream",
            headers=self.headers,
            json={
                "input": "请分析这张图片",
                "agentName": "leader_agent",
                "attachments": [{
                    "type": "image",
                    "mimeType": "image/png",
                    "name": "screen.png",
                    "url": "https://files.test/screen.png",
                }],
                "metadata": {"agentModelConfigs": self.agent_model_configs},
            },
        )

        self.assertEqual(200, response.status_code)
        self.assertIn('"ragStrategy": "recognize_image_tool"', response.text)
        self.assertIn('"answerType": "image_analysis"', response.text)
        self.assertIn('"boundAgent": "vision_agent"', response.text)

    def test_presenton_structure_normalizer_accepts_layout_ids(self):
        normalized = normalize_structure_answer(
            '{"layouts":[{"slideIndex":1,"layoutId":"title_intro"}]}'
        )
        self.assertEqual(
            {"layouts": [{"slideIndex": 1, "layoutId": "title_intro"}]},
            normalized,
        )

    def test_leader_agent_routes_and_executes_specialist(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "帮我把数据结构中的栈与队列整理成 PPT 大纲",
                "agentName": "leader_agent",
                "metadata": {"agentModelConfigs": self.agent_model_configs},
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("leader_agent", payload["metadata"]["agentName"])
        self.assertEqual("ppt_outline_agent", payload["metadata"]["targetAgent"])
        self.assertEqual("ppt_outline_agent", payload["metadata"]["executedAgent"])
        self.assertNotIn("plannedRagStrategy", payload["metadata"])
        self.assertEqual("direct_agent", payload["strategy"])
        self.assertEqual("leader_routed_direct_agent", payload["metadata"]["executionMode"])
        self.assertTrue(payload["metadata"]["retrievalSkipped"])
        self.assertEqual("leader_route", payload["trace"][0]["stage"])
        self.assertIn("PPT 大纲", payload["answer"])

    def test_production_leader_planning_excludes_internal_dag_agents_and_keeps_routes(self):
        class RecordingLeaderProvider(FakeRagModelProvider):
            def __init__(self):
                self.callable_catalogs = []

            def complete(self, system_prompt, user_prompt):
                if "Leader 智能体" in system_prompt:
                    payload = json.loads(user_prompt)
                    self.callable_catalogs.append(payload["leader_callable_catalog"])
                    if "流程图" in (payload.get("user_input") or ""):
                        return json.dumps(
                            {
                                "intent": "diagram_flowchart",
                                "target_agent": "leader_agent",
                                "need_retrieval": False,
                                "rag_strategy": "",
                                "action": "call_tool",
                                "tool_name": "generate_flowchart_image_tool",
                                "route_reason": "LLM 根据 Leader 可调用清单选择流程图图片生成工具。",
                                "answer": "",
                            },
                            ensure_ascii=False,
                        )
                return super().complete(system_prompt, user_prompt)

        rag_routes = importlib.import_module("app.api.routes.rag")
        leader_module = importlib.import_module("app.multi_agents.leader_agent.agent")
        provider = RecordingLeaderProvider()
        old_get_chat_model_provider = leader_module.get_chat_model_provider
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        try:
            leader_module.get_chat_model_provider = lambda: provider
            rag_routes.data_store.search_service_tool_with_meta = lambda *_args: ([
                    {
                        "type": "course_schedule_summary",
                        "name": "Python程序设计",
                        "semesterLabel": "2025-2026 第 2 学期",
                        "teacherName": "范老师",
                        "scheduleCount": 1,
                        "scheduleItems": ["周三 1-2节 A101 1-16周"],
                    }
                ], {"toolCache": {}})
            schedule_response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={"input": "这个学期都有什么课啊", "agentName": "leader_agent"},
            )
            agent_model_configs = {
                agent_name: {
                    "configPrefix": f"ai.agent.{agent_name}",
                    "provider": "deepseek",
                    "baseUrl": "https://llm.test/v1",
                    "apiKey": "test-key",
                    "model": "test-model",
                    "tested": True,
                }
                for agent_name in (
                    "ppt_outline_agent",
                    "diagram_flowchart_agent",
                    "diagram_flowchart_prompt_agent",
                    "image_agent",
                )
            }
            ppt_response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "生成 Python 课程 PPT",
                    "agentName": "leader_agent",
                    "metadata": {"agentModelConfigs": agent_model_configs},
                },
            )
            diagram_response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "生成 Python 循环流程图",
                    "agentName": "leader_agent",
                    "metadata": {"agentModelConfigs": agent_model_configs},
                },
            )
        finally:
            leader_module.get_chat_model_provider = old_get_chat_model_provider
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool

        self.assertEqual(200, schedule_response.status_code)
        self.assertEqual("java_schedule_api", schedule_response.json()["metadata"]["toolName"])
        self.assertEqual(200, ppt_response.status_code)
        self.assertEqual("generated_export_tools", ppt_response.json()["metadata"]["targetAgent"])
        self.assertEqual(["pptx"], [item["ext"] for item in ppt_response.json()["attachments"]])
        self.assertEqual(200, diagram_response.status_code)
        diagram_payload = diagram_response.json()
        self.assertEqual("generate_flowchart_image_tool", diagram_payload["metadata"]["targetAgent"])
        self.assertEqual("generate_flowchart_image_tool", diagram_payload["metadata"]["executedAgent"])
        self.assertEqual("generate_flowchart_image_tool", diagram_payload["metadata"]["toolName"])
        self.assertEqual("diagram_flowchart_prompt_agent", diagram_payload["metadata"]["promptAgent"])
        self.assertEqual("generate_flowchart_image_tool", diagram_payload["strategy"])
        self.assertEqual(1, len(FakeImageProvider.requests))
        self.assertEqual(
            "专业流程图图片提示词：蓝白教学风格，清晰展示开始、处理步骤、判断分支和结束节点。",
            FakeImageProvider.requests[0].prompt,
        )
        # 明确的课表和文件格式请求走规则快速路由；流程图仍由模型读取可调用清单后路由。
        self.assertEqual(1, len(provider.callable_catalogs))
        for catalog in provider.callable_catalogs:
            callable_names = {item["name"] for item in catalog["agents"]}
            callable_tools = {item["name"] for item in catalog["tools"]}
            self.assertTrue(INTERNAL_LEARNING_WORKFLOW_AGENTS.isdisjoint(callable_names))
            self.assertGreaterEqual(
                callable_names,
                {"ppt_outline_agent"},
            )
            self.assertIn("generate_flowchart_image_tool", callable_tools)
            self.assertNotIn("diagram_flowchart_prompt_agent", callable_names)
            self.assertNotIn("image_agent", callable_names)

    def test_leader_agent_answers_smalltalk_with_model_without_rag(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "你好",
                "agentName": "leader_agent",
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("leader_direct_answer", payload["strategy"])
        self.assertEqual("leader_direct_answer", payload["metadata"]["executionMode"])
        self.assertTrue(payload["metadata"]["retrievalSkipped"])
        self.assertEqual("llm", payload["metadata"]["routeMode"])
        self.assertEqual([], payload["documents"])
        self.assertEqual("LLM 已接入：你好", payload["answer"])
        self.assertEqual([], payload["outputMeta"]["followUpActions"])
        self.assertEqual("", payload["outputMeta"]["choicePrompt"])

    def test_leader_agent_uses_request_llm_config_when_forwarded_from_java(self):
        input_text = "请用一句话鼓励我"
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": input_text,
                "agentName": "leader_agent",
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("leader_direct_answer", payload["strategy"])
        self.assertEqual(f"LLM 已接入：{input_text}", payload["answer"])
        self.assertEqual("llm", payload["metadata"]["routeMode"])
        self.assertIn("Java 后台模型配置", payload["metadata"]["routeReason"])

    def test_query_without_agent_uses_leader_by_default(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={"input": "你好"},
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("leader_agent", payload["metadata"]["agentName"])
        self.assertEqual("leader_direct_answer", payload["metadata"]["executionMode"])
        self.assertEqual("leader_direct_answer", payload["strategy"])

    def test_leader_service_tool_result_is_summarized_by_model(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        calls = []
        try:
            def fake_search_service_tool(authorization, tool_name, query):
                calls.append((authorization, tool_name, query))
                return [{
                    "type": "course_schedule_summary",
                    "name": "数据结构",
                    "semesterLabel": "2025-2026 第 2 学期",
                    "teacherName": "张老师",
                    "scheduleCount": 1,
                    "scheduleItems": ["周一 1-2节 A101 1-16周"],
                }]

            self._install_service_tool_stub(rag_routes, fake_search_service_tool)
            response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "这个学期都有什么课啊",
                    "agentName": "leader_agent",
                },
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("java_schedule_api", payload["strategy"])
        self.assertEqual("java_schedule_api", payload["metadata"]["toolName"])
        self.assertEqual("正在为你查询本学期课表。", payload["metadata"]["planningAnswer"])
        self.assertTrue(payload["metadata"]["toolResultSummarized"])
        self.assertEqual("model", payload["metadata"]["toolResultSummaryMode"])
        self.assertEqual("service_tool_result", payload["answerType"])
        self.assertEqual(["text"], payload["outputTypes"])
        self.assertEqual([], payload["attachments"])
        self.assertIn("本学期有 1 门课", payload["answer"])
        self.assertIn("数据结构", payload["answer"])
        self.assertNotIn("周一 1-2节", payload["answer"])
        self.assertEqual("java_schedule_api", calls[0][1])
        self.assertEqual("tool_result_summary", payload["trace"][-1]["stage"])

    def test_leader_empty_schedule_result_is_answered_by_model(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        old_summarize_tool_result = rag_routes.leader_agent.summarize_tool_result
        summary_calls = []
        try:
            rag_routes.data_store.search_service_tool_with_meta = lambda *_args: (
                [],
                {
                    "toolCache": {
                        "requestCount": 2,
                        "hitCount": 0,
                        "missCount": 2,
                        "events": [{"status": "miss"}, {"status": "miss"}],
                    }
                },
            )

            def summarize_empty_result(**kwargs):
                summary_calls.append(kwargs)
                return "模型确认：今天暂未查询到课表安排。"

            rag_routes.leader_agent.summarize_tool_result = summarize_empty_result
            response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "我今天的课表是什么",
                    "agentName": "leader_agent",
                    "metadata": {"profileContextMs": 3},
                },
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool
            rag_routes.leader_agent.summarize_tool_result = old_summarize_tool_result

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(1, len(summary_calls))
        self.assertEqual([], summary_calls[0]["tool_results"])
        self.assertEqual("java_schedule_api", payload["strategy"])
        self.assertTrue(payload["metadata"]["toolResultSummarized"])
        self.assertEqual("model", payload["metadata"]["toolResultSummaryMode"])
        self.assertEqual("rules", payload["metadata"]["routeMode"])
        self.assertEqual(3, payload["metadata"]["timings"]["profileMs"])
        self.assertEqual("模型确认：今天暂未查询到课表安排。", payload["answer"])
        self.assertEqual("model", payload["trace"][-1]["detail"]["summaryMode"])

    def test_leader_empty_tool_result_reports_java_backend_error_instead_of_no_data(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        old_summarize_tool_result = rag_routes.leader_agent.summarize_tool_result
        summary_calls = []
        try:
            rag_routes.data_store.search_service_tool_with_meta = lambda *_args: (
                [],
                {
                    "toolCache": {
                        "requestCount": 1,
                        "hitCount": 0,
                        "missCount": 1,
                        "events": [{"status": "error", "elapsedMs": 8000}],
                    }
                },
            )

            def summarize_backend_failure(**kwargs):
                summary_calls.append(kwargs)
                return "模型确认：课表系统本次调用失败，当前结果不能用于判断是否有数据。"

            rag_routes.leader_agent.summarize_tool_result = summarize_backend_failure
            response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={"input": "我今天的课表是什么", "agentName": "leader_agent"},
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool
            rag_routes.leader_agent.summarize_tool_result = old_summarize_tool_result

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(1, len(summary_calls))
        self.assertEqual("tool_execution_error", summary_calls[0]["tool_results"][0]["type"])
        self.assertEqual("model", payload["metadata"]["toolResultSummaryMode"])
        self.assertEqual("request_error", payload["metadata"]["serviceToolBackendStatus"])
        self.assertTrue(payload["metadata"]["serviceToolBackendFailure"])
        self.assertIn("本次调用失败", payload["answer"])
        self.assertIn("不能用于判断是否有数据", payload["answer"])
        self.assertNotIn("暂未查询到今天的课表安排", payload["answer"])
        self.assertEqual("model", payload["trace"][-1]["detail"]["summaryMode"])
        self.assertEqual(
            "java_backend_request_failed",
            payload["trace"][-1]["detail"]["backendFailure"]["reason"],
        )

    def test_service_tool_backend_failure_classifies_auth_timeout_and_circuit_open(self):
        rag_routes = importlib.import_module("app.api.routes.rag")

        unauthorized = rag_routes._service_tool_backend_failure({
            "toolCache": {
                "requestCount": 1,
                "events": [{"status": "error", "statusCode": 401}],
            }
        })
        timeout = rag_routes._service_tool_backend_failure({
            "toolCache": {
                "requestCount": 1,
                "events": [{"status": "timeout"}],
            }
        })

        retriever = rag_routes.data_store._retriever
        old_disabled_until = retriever.disabled_until
        try:
            retriever.disabled_until = rag_routes.time.monotonic() + 10
            circuit_open = rag_routes._service_tool_backend_failure({
                "toolCache": {"requestCount": 0, "events": []}
            })
        finally:
            retriever.disabled_until = old_disabled_until

        self.assertEqual("unauthorized", unauthorized["status"])
        self.assertEqual("timeout", timeout["status"])
        self.assertEqual("circuit_open", circuit_open["status"])

    def test_leader_routes_course_teacher_query_to_schedule_tool(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        calls = []
        try:
            def fake_search_service_tool(authorization, tool_name, query):
                calls.append((authorization, tool_name, query))
                return [
                    {
                        "type": "course_schedule_summary",
                        "name": "操作系统",
                        "semesterLabel": "2025-2026 第 2 学期",
                        "teacherName": "孙老师",
                        "scheduleItems": ["周五 5-6节 D402 1-16周"],
                    },
                    {
                        "type": "course_schedule_summary",
                        "name": "Linux系统",
                        "semesterLabel": "2025-2026 第 2 学期",
                        "teacherName": "庄老师",
                        "scheduleItems": ["周二 5-6节 D403 1-16周"],
                    },
                ]

            self._install_service_tool_stub(rag_routes, fake_search_service_tool)
            response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "Linux操作系统的老师是谁",
                    "agentName": "leader_agent",
                },
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("java_schedule_api", payload["metadata"]["toolName"])
        self.assertEqual("service_tool_result", payload["answerType"])
        self.assertIn("Linux系统的老师是庄老师", payload["answer"])
        self.assertNotIn("教材知识点", payload["metadata"].get("targetAgent", ""))
        self.assertNotIn("周二 5-6节", payload["answer"])
        self.assertEqual("java_schedule_api", calls[0][1])

    def test_leader_routes_course_count_query_to_schedule_tool(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        calls = []
        try:
            def fake_search_service_tool(authorization, tool_name, query):
                calls.append((authorization, tool_name, query))
                return [
                    {
                        "type": "course_schedule_summary",
                        "name": "操作系统",
                        "semesterLabel": "2025-2026 第 2 学期",
                        "teacherName": "孙老师",
                        "scheduleCount": 1,
                        "scheduleItems": ["周五 5-6节 D402 1-16周"],
                    },
                    {
                        "type": "course_schedule_summary",
                        "name": "Linux系统",
                        "semesterLabel": "2025-2026 第 2 学期",
                        "teacherName": "庄老师",
                        "scheduleCount": 1,
                        "scheduleItems": ["周二 5-6节 D403 1-16周"],
                    },
                ]

            self._install_service_tool_stub(rag_routes, fake_search_service_tool)
            response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "linux这个学期有几节课?",
                    "agentName": "leader_agent",
                },
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("java_schedule_api", payload["metadata"]["toolName"])
        self.assertEqual("service_tool_result", payload["answerType"])
        self.assertIn("Linux系统", payload["answer"])
        self.assertIn("1", payload["answer"])
        self.assertEqual("java_schedule_api", calls[0][1])

    def test_leader_formats_course_time_answer_as_plain_text(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        try:
            def fake_search_service_tool(authorization, tool_name, query):
                return [
                    {
                        "type": "course_schedule_summary",
                        "name": "Python程序设计",
                        "semesterLabel": "2025-2026 第 2 学期",
                        "teacherName": "范晶晶",
                        "scheduleItems": [
                            "周三 1-2节 第3周、第8-10周双周、第11-12周 明德楼阶梯110",
                            "周三 3-4节 第13周 明德楼阶梯110",
                        ],
                    }
                ]

            self._install_service_tool_stub(rag_routes, fake_search_service_tool)
            response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "python这个课程我是什么时候学的",
                    "agentName": "leader_agent",
                },
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("java_schedule_api", payload["metadata"]["toolName"])
        self.assertIn("课程名：Python程序设计", payload["answer"])
        self.assertNotIn("**", payload["answer"])
        self.assertNotIn("---", payload["answer"])
        self.assertNotIn("你需要的是哪部分", payload["answer"])

    def test_leader_formats_canteen_tool_answer_as_plain_text(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        calls = []
        try:
            def fake_search_service_tool(authorization, tool_name, query):
                calls.append((authorization, tool_name, query))
                return [
                    {
                        "type": "canteen_dish",
                        "name": "黄焖鸡米饭",
                        "stallName": "黄焖鸡档口",
                        "price": 18,
                        "location": "第一学生餐厅",
                    }
                ]

            self._install_service_tool_stub(rag_routes, fake_search_service_tool)
            response = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "食堂有黄焖鸡吗",
                    "agentName": "leader_agent",
                },
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("java_canteen_api", payload["metadata"]["toolName"])
        self.assertIn("黄焖鸡米饭", payload["answer"])
        self.assertNotIn("**", payload["answer"])
        self.assertNotIn("---", payload["answer"])
        self.assertNotIn("如果需要", payload["answer"])
        self.assertEqual("java_canteen_api", calls[0][1])

    def test_leader_contextualizes_followup_course_count(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        calls = []
        try:
            def fake_search_service_tool(authorization, tool_name, query):
                calls.append((authorization, tool_name, query))
                if "上几次" in query or "几次课" in query:
                    return [{
                        "type": "course_schedule_summary",
                        "name": "Python程序设计",
                        "semesterLabel": "2025-2026 第 2 学期",
                        "teacherName": "范晶晶",
                        "scheduleCount": 2,
                        "scheduleItems": [
                            "周三 1-2节 第3周、第8-10周双周、第11-12周 明德楼阶梯110",
                            "周五 1-2节 第7周、第8-10周双周、第11-13周 图书馆一楼公共机房4",
                        ],
                    }]
                return [{
                    "type": "course_schedule_summary",
                    "name": "Python程序设计",
                    "semesterLabel": "2025-2026 第 2 学期",
                    "teacherName": "范晶晶",
                    "scheduleItems": [
                        "周三 1-2节 第3周、第8-10周双周、第11-12周 明德楼阶梯110",
                    ],
                }]

            self._install_service_tool_stub(rag_routes, fake_search_service_tool)
            first = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "python是什么时候学的了",
                    "agentName": "leader_agent",
                    "metadata": {"sessionId": "ctx-course-followup"},
                },
            )
            second = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "上几次呢",
                    "agentName": "leader_agent",
                    "metadata": {"sessionId": "ctx-course-followup"},
                },
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool

        self.assertEqual(200, first.status_code)
        self.assertEqual(200, second.status_code)
        payload = second.json()
        self.assertEqual("java_schedule_api", payload["metadata"]["toolName"])
        self.assertEqual("course_count", payload["metadata"]["intent"])
        self.assertTrue(payload["metadata"]["conversationContextUsed"])
        self.assertIn("Python程序设计", payload["metadata"]["contextualizedInput"])
        self.assertIn("Python程序设计", calls[-1][2])
        self.assertIn("Python程序设计", payload["answer"])
        self.assertNotIn("哪门课", payload["answer"])

    def test_leader_does_not_override_explicit_no_class_intent_with_context_subject(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        old_search_service_tool = rag_routes.data_store.search_service_tool_with_meta
        calls = []
        session_id = "ctx-no-class-intent"
        try:
            def fake_search_service_tool(authorization, tool_name, query):
                calls.append((authorization, tool_name, query))
                if "没有课" in query or "没课" in query or "有课吗" in query:
                    return []
                return [{
                    "type": "course_schedule_summary",
                    "name": "深度学习",
                    "semesterLabel": "2025-2026 第 2 学期",
                    "teacherName": "赵明皓",
                    "scheduleItems": ["周一 1-2节 明德楼阶梯110"],
                }]

            self._install_service_tool_stub(rag_routes, fake_search_service_tool)
            first = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "深度学习什么时候上课",
                    "agentName": "leader_agent",
                    "metadata": {"sessionId": session_id},
                },
            )
            second = self.client.post(
                "/internal/rag/query",
                headers=self.headers,
                json={
                    "input": "从什么时候开始没有课的",
                    "agentName": "leader_agent",
                    "metadata": {"sessionId": session_id},
                },
            )
        finally:
            rag_routes.data_store.search_service_tool_with_meta = old_search_service_tool

        self.assertEqual(200, first.status_code)
        self.assertEqual(200, second.status_code)
        payload = second.json()
        self.assertEqual("java_schedule_api", payload["metadata"]["toolName"])
        self.assertNotIn("contextualizedInput", payload["metadata"])
        self.assertEqual("从什么时候开始没有课的", calls[-1][2])
        self.assertNotIn("深度学习", calls[-1][2])

    def test_removed_md_agent_is_rejected(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "# 数据结构\n- 栈遵循后进先出\n- 队列遵循先进先出",
                "agentName": "md_knowledge_agent",
            },
        )

        self.assertEqual(400, response.status_code)
        payload = response.json()
        self.assertEqual("智能体不存在", payload["detail"])

    def test_query_endpoint_rejects_unknown_agent(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={"input": "测试", "agentName": "deleted_agent"},
        )

        self.assertEqual(400, response.status_code)

    def test_learning_workflow_internal_agents_cannot_be_directly_requested(self):
        for requested_agent in ("learning_path_agent", "Python 学习路径智能体"):
            with self.subTest(requested_agent=requested_agent):
                response = self.client.post(
                    "/internal/rag/query",
                    headers=self.headers,
                    json={"input": "生成学习路径", "agentName": requested_agent},
                )

                self.assertEqual(400, response.status_code)
                self.assertEqual("智能体不存在", response.json()["detail"])

    def test_agent_catalog_examples_are_runnable_for_specialists(self):
        catalog_response = self.client.get("/internal/rag/agents", headers=self.headers)
        self.assertEqual(200, catalog_response.status_code)

        callable_names = {
            item["name"]
            for item in catalog_response.json()["leaderCallableCatalog"]["agents"]
        }
        specialists = [
            agent for agent in catalog_response.json()["agents"]
            if agent["name"] in callable_names
        ]

        for agent in specialists:
            with self.subTest(agent=agent["name"]):
                example = agent["invokeExample"]
                response = self.client.post(
                    "/internal/rag/query",
                    headers=self.headers,
                    json={
                        "input": example["input"],
                        "agentName": example["agentName"],
                        "metadata": {"agentModelConfigs": self.agent_model_configs},
                    },
                )

                self.assertEqual(200, response.status_code)
                payload = response.json()
                self.assertEqual(agent["name"], payload["metadata"]["agentName"])
                self.assertEqual("direct_agent", payload["strategy"])
                self.assertTrue(payload["answer"])

    def test_query_endpoint_text_to_sql_returns_tool_answer_without_local_synthesizer(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "统计食堂优惠券数量",
                "keyword": "优惠券",
                "ragStrategy": "text_to_sql",
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertTrue(payload["answer"])
        self.assertNotIn("answerSynthesizer", payload["metadata"])

    def test_ppt_outline_normalizer_rewrites_legacy_fields(self):
        raw = """## PPT 大纲

**提示：** 未检索到外部证据，以下大纲基于通用教学知识生成。

### 第 1 页：封面
- **页标题：** 数据结构中的栈与队列
- **讲解目标：** 明确主题。
- **页面内容建议：**
  - 主标题：数据结构中的栈与队列
  - 副标题：基础概念与应用
- **课堂互动建议：** 提问。
"""
        normalized = normalize_ppt_outline_answer(
            raw,
            "topic: 数据结构中的栈与队列; scene_type: academic; audience: 学生; slide_count: 6",
        )
        self.assertIn("### 大纲信息", normalized)
        self.assertIn("- 使用场景：学术", normalized)
        self.assertIn("- 受众：学生", normalized)
        self.assertIn("- 页面类型：封面页", normalized)
        self.assertIn("- 本页目标：明确主题。", normalized)
        self.assertIn("- 展示建议：", normalized)
        self.assertIn("- 素材建议：", normalized)
        self.assertNotIn("讲解目标", normalized)
        self.assertNotIn("页面内容建议", normalized)
        self.assertNotIn("课堂互动建议", normalized)

def _fake_question_payload(question_type):
    question = {
        "id": f"{question_type}-1",
        "type": question_type,
        "stem": "数据结构测试题",
        "score": 4,
        "difficulty": "easy",
        "knowledgePoints": ["栈"],
        "tags": ["数据结构"],
        "body": {},
        "answer": {},
        "analysis": "依据测试材料生成。",
        "scoring": {"mode": "exact", "rubrics": []},
        "sourceBasis": ["测试材料"],
    }
    if question_type == "single_choice":
        question["body"] = {"options": [{"key": "A", "text": "后进先出"}, {"key": "B", "text": "先进先出"}]}
        question["answer"] = {"correctOption": "A"}
    elif question_type == "multiple_choice":
        question["body"] = {"options": [{"key": "A", "text": "栈顶入栈"}, {"key": "B", "text": "栈顶出栈"}]}
        question["answer"] = {"correctOptions": ["A", "B"]}
    elif question_type == "true_false":
        question["body"] = {"statement": "栈遵循后进先出原则。"}
        question["answer"] = {"correct": True}
    elif question_type == "fill_blank":
        question["body"] = {"text": "栈遵循{{blank_1}}原则。", "blanks": [{"id": "blank_1", "score": 4}]}
        question["answer"] = {"blanks": [{"id": "blank_1", "answers": ["后进先出", "LIFO"]}]}
        question["scoring"] = {"mode": "blank", "rubrics": [{"criterion": "blank_1 正确", "score": 4}]}
    elif question_type == "short_answer":
        question["body"] = {"answerLengthHint": "50字以内"}
        question["answer"] = {"referenceAnswer": "栈遵循后进先出原则。", "answerPoints": ["说明后进先出"]}
        question["scoring"] = {"mode": "rubric", "rubrics": [{"criterion": "说明后进先出", "score": 4}]}
    elif question_type == "calculation":
        question["answer"] = {"finalAnswer": "3", "steps": ["模拟三次入栈"]}
        question["scoring"] = {"mode": "step", "rubrics": [{"criterion": "过程与结果正确", "score": 4}]}
    elif question_type == "programming":
        question["body"] = {
            "title": "括号匹配",
            "description": "判断圆括号字符串是否匹配。",
            "language": "Python",
            "inputFormat": "一行字符串",
            "outputFormat": "true 或 false",
            "constraints": ["长度不超过 1000"],
            "examples": [{"input": "()", "output": "true"}],
        }
        question["answer"] = {
            "solutionOutline": ["使用栈匹配左右括号"],
            "referenceSolution": "def ok(text):\n    return text == '()'",
            "testCases": [{"input": "()", "expectedOutput": "true", "hidden": False}],
        }
        question["scoring"] = {"mode": "program", "rubrics": [{"criterion": "通过测试", "score": 4}]}
    return {"questions": [question], "missingInfo": []}


class FakeRagModelProvider:
    def complete(self, system_prompt, user_prompt):
        if "系统接口返回的数据" in system_prompt:
            payload = json.loads(user_prompt)
            if payload.get("answer_policy", {}).get("mode") == "canteen_query":
                return (
                    "**黄焖鸡米饭**\n"
                    "---\n"
                    "食堂/档口：第一学生餐厅，黄焖鸡档口\n"
                    "价格：18 元\n"
                    "如果需要，我可以继续帮你筛选其他菜品。"
                )
            if payload.get("answer_policy", {}).get("mode") == "course_time":
                rows = payload.get("tool_results") or [{}]
                first = rows[0] if isinstance(rows[0], dict) else {}
                name = first.get("name") or "Python程序设计"
                semester = first.get("semesterLabel") or "2025-2026 第 2 学期"
                teacher = first.get("teacherName") or "范晶晶"
                schedule_items = first.get("scheduleItems") if isinstance(first.get("scheduleItems"), list) else []
                schedule_text = schedule_items[0] if schedule_items else "周三 1-2节（第3周、第8-10周双周、第11-12周）；地点：明德楼阶梯110"
                prefix = "当前学期没查到，已自动扩大到所有学期。\n" if first.get("queryScope") == "all_semesters_fallback" else ""
                return (
                    f"{prefix}**课程名：{name}**\n"
                    "---\n"
                    f"**学期：{semester}**\n"
                    f"理论课（{teacher}）：{schedule_text}\n"
                    "你需要的是哪部分的具体信息？"
                )
            if payload.get("answer_policy", {}).get("mode") == "course_count":
                rows = payload.get("tool_results") or [{}]
                first = next((item for item in rows if "Linux" in str(item.get("name") or "")), rows[0])
                return f"{first.get('name')}本学期查到 {first.get('scheduleCount') or payload.get('tool_result_count')} 条上课安排。"
            if payload.get("answer_policy", {}).get("mode") == "course_teacher":
                rows = payload.get("tool_results") or [{}]
                first = next((item for item in rows if "Linux" in str(item.get("name") or "")), rows[0])
                return f"{first.get('name')}的老师是{first.get('teacherName')}。"
            if payload.get("answer_policy", {}).get("mode") == "course_list":
                first = (payload.get("tool_results") or [{}])[0]
                schedule_text = "；".join(first.get("scheduleItems") or []) if isinstance(first, dict) else ""
                teacher = first.get("teacherName") if isinstance(first, dict) else ""
                teacher_text = f"（{teacher}）" if teacher else ""
                return f"本学期有 {payload.get('tool_result_count')} 门课：\n- {first.get('name')}{teacher_text}{schedule_text}"
            return f"模型整理：{payload.get('tool_display_name')} 返回 {payload.get('tool_result_count')} 条数据。"
        if "Leader 智能体" in system_prompt:
            payload = json.loads(user_prompt)
            text = payload.get("user_input") or ""
            rag_strategy = payload.get("requested_rag_strategy") or ""
            return json.dumps(self._build_leader_plan(text, rag_strategy), ensure_ascii=False)
        return self._specialist_answer(system_prompt, user_prompt)

    def _build_leader_plan(self, input_text, rag_strategy=""):
        text = input_text or ""
        if "统计" in text:
            return {
                "intent": "structured_query",
                "target_agent": "leader_agent",
                "need_retrieval": False,
                "rag_strategy": "text_to_sql",
                "action": "call_tool",
                "tool_name": "text_to_sql",
                "route_reason": "LLM 根据 Java 后台模型配置完成结构化查询识别。",
                "answer": "",
            }
        if "食堂" in text or "黄焖鸡" in text or "吃什么" in text:
            return {
                "intent": "canteen_query",
                "target_agent": "leader_agent",
                "need_retrieval": False,
                "rag_strategy": "",
                "action": "call_tool",
                "tool_name": "java_canteen_api",
                "route_reason": "LLM 根据启用工具清单选择食堂餐饮查询工具。",
                "answer": "正在为你查询食堂餐饮信息。",
            }
        if "课表" in text or "有什么课" in text or "有课吗" in text or "没有课" in text or "没课" in text or "老师是谁" in text or "谁教" in text or "几节课" in text or "几次课" in text or "上几次" in text or "多少次课" in text or "什么时候" in text:
            schedule_status_query = "有课吗" in text or "没有课" in text or "没课" in text
            teacher_query = "老师是谁" in text or "谁教" in text
            count_query = "几节课" in text or "几次课" in text or "上几次" in text or "多少次课" in text
            time_query = "什么时候" in text and not schedule_status_query
            return {
                "intent": "course_teacher" if teacher_query else ("course_count" if count_query else ("course_time" if time_query else "schedule")),
                "target_agent": "leader_agent",
                "need_retrieval": False,
                "rag_strategy": "",
                "action": "call_tool",
                "tool_name": "java_schedule_api",
                "route_reason": "LLM 根据启用工具清单选择课表查询工具。",
                "answer": "正在为你查询课程老师。" if teacher_query else ("正在为你查询课程次数。" if count_query else ("正在为你查询课程时间。" if time_query else "正在为你查询本学期课表。")),
            }
        if "PPT" in text or "课件" in text:
            return {
                "intent": "ppt_outline",
                "target_agent": "ppt_outline_agent",
                "need_retrieval": False,
                "rag_strategy": "",
                "action": "delegate_agent",
                "tool_name": "",
                "route_reason": "LLM 根据 Java 后台模型配置路由到 PPT 大纲智能体。",
                "answer": "",
            }
        return {
            "intent": "smalltalk",
            "target_agent": "leader_agent",
            "need_retrieval": False,
            "rag_strategy": "",
            "action": "direct_answer",
            "tool_name": "",
            "route_reason": "LLM 根据 Java 后台模型配置完成意图识别。",
            "answer": f"LLM 已接入：{input_text}",
        }

    def _specialist_answer(self, system_prompt, user_prompt=""):
        if "文件内容编排智能体" in system_prompt:
            outer_payload = json.loads(user_prompt)
            planner_payload = json.loads(outer_payload.get("user_input") or "{}")
            source_content = str(planner_payload.get("sourceContent") or "").strip()
            source_candidates = planner_payload.get("sourceCandidates") or []
            user_request = str(planner_payload.get("userRequest") or "").strip()
            selected_candidate = next((
                item for item in source_candidates
                if isinstance(item, dict)
                and str(item.get("content") or "").strip()
                and not any(token in str(item.get("content") or "") for token in (
                    "请选择知识来源", "需要您确认来源", "资源生成失败", "正在处理",
                ))
            ), None)
            if not source_content and not selected_candidate and user_request.replace(" ", "").lower() in {
                "给我导出word", "导出word", "生成word", "给我导出docx", "导出docx",
            }:
                return json.dumps({
                    "action": "clarify",
                    "title": "",
                    "content": "",
                    "question": "你希望把哪一段内容转换成 Word？可以指定当前对话中的内容，或直接告诉我主题。",
                }, ensure_ascii=False)
            selected_content = str((selected_candidate or {}).get("content") or "").strip()
            content = source_content or selected_content or f"# {user_request}\n\n## 内容\n\n- 由模型根据用户主题整理"
            return json.dumps({
                "action": "export",
                "title": "数据结构学习资料" if source_content else "主题学习资料",
                "content": content,
                "question": "",
                "selectedSourceMessageId": (selected_candidate or {}).get("messageId"),
            }, ensure_ascii=False)
        if "diagram_mind_map_agent" in system_prompt:
            return "```mermaid\nmindmap\n  root((测试思维导图))\n```"
        if "diagram_flowchart_prompt_agent" in system_prompt:
            return "专业流程图图片提示词：蓝白教学风格，清晰展示开始、处理步骤、判断分支和结束节点。"
        if "diagram_activity_prompt_agent" in system_prompt:
            return "专业活动图图片提示词：使用泳道清晰展示角色、任务和状态变化。"
        if "architecture_prompt_agent" in system_prompt:
            return "专业架构图图片提示词：分层展示客户端、服务层和数据层及其依赖关系。"
        if "mind_map_agent" in system_prompt:
            return "专业思维导图图片提示词：中心主题向外分层展开，节点清晰可读。"
        if "knowledge_graph_prompt_agent" in system_prompt:
            return "专业知识图谱图片提示词：实体节点分组清晰，关系名称明确，使用带方向箭头连接。"
        if "思维导图智能体" in system_prompt:
            return "```mermaid\nmindmap\n  root((测试思维导图))\n```"
        if "diagram_flowchart_agent" in system_prompt:
            return "```mermaid\nflowchart TD\n  Start([开始]) --> End([结束])\n```"
        if "diagram_activity_agent" in system_prompt:
            return "```mermaid\nflowchart TD\n  Start([开始]) --> Task[执行任务] --> End([结束])\n```"
        if "diagram_architecture_agent" in system_prompt:
            return "```mermaid\nflowchart LR\n  Web[Web] --> API[API]\n```"
        if "教材知识点智能体" in system_prompt:
            return "## 教材知识点\n- 基于 LLM 整理教材知识点"
        question_markers = {
            "单选题智能体": "single_choice",
            "多选题智能体": "multiple_choice",
            "判断题智能体": "true_false",
            "填空题智能体": "fill_blank",
            "简答题智能体": "short_answer",
            "计算题智能体": "calculation",
            "编程题智能体": "programming",
        }
        for marker, question_type in question_markers.items():
            if marker in system_prompt:
                return json.dumps(_fake_question_payload(question_type), ensure_ascii=False)
        if "会议总控智能体" in system_prompt:
            return "## 会议总控\n- 当前状态：进行中"
        if "语音转写智能体" in system_prompt:
            return "## 会议转写\n- 发言人A：测试发言"
        if "会议总结智能体" in system_prompt:
            return "## 会议总结\n- 主要结论：测试结论"
        if "成员分析智能体" in system_prompt:
            return "## 成员分析\n- 成员A：参与积极"
        if "资源推荐智能体" in system_prompt:
            return "## 资源推荐\n- 成员A：推荐复习资料"
        if "语音播报智能体" in system_prompt:
            return "## 语音播报稿\n请大家关注会议结论。"
        if "PPT 大纲智能体" in system_prompt:
            return """## PPT 大纲
### 第 1 页：课程导入
- **页标题：** 数据结构中的栈与队列
- **讲解目标：** 说明主题。
- **页面内容建议：**
  - 主标题：数据结构中的栈与队列
  - 副标题：课程导入
- **课堂互动建议：** 提问。"""
        if "PPT 布局智能体" in system_prompt:
            return """## PPT 布局方案
### 第 1 页：封面布局
- 版式类型：封面布局
- 标题区：页面上方居中标题
- 正文区：副标题和课程信息
- 图表/图片区：背景示意图
- 视觉层级：标题最强
- 留白：四周留白充足
- 讲解动线：从标题到副标题到背景图"""
        if "PPT 审查智能体" in system_prompt:
            return "## PPT 审查报告\n置信度评分：86/100"
        if "PPT 图片智能体" in system_prompt:
            return "## PPT 图片提示词\n### 封面图"
        if "`image_agent`" in system_prompt or "图片智能体" in system_prompt:
            return "中文提示词：测试教学配图\nEnglish prompt: test educational image"
        return "测试回答"

    def extract_search_keyword(self, input_text):
        return "测试关键词"

    def answer(self, prompt, input_text, history, search_keyword, search_results):
        return f"已检索到{len(search_results or [])}条候选，关键词={search_keyword}"

    def stream_complete(self, system_prompt, user_prompt):
        yield self.complete(system_prompt, user_prompt)


class FakeImageProvider:
    requests = []

    def generate(self, request):
        self.__class__.requests.append(request)
        return SimpleNamespace(model_dump=lambda: {
            "taskId": "fake-image-task",
            "providerTaskId": "fake-provider-task",
            "mode": "single",
            "status": "success",
            "prompt": request.prompt,
            "style": request.style,
            "size": request.size,
            "count": 1,
            "seed": request.seed,
            "negativePrompt": request.negativePrompt,
            "images": [{"index": 0, "url": "https://example.com/fake.png", "status": "success"}],
            "message": "测试图片生成成功",
            "metadata": request.metadata,
        })

    def batch(self, request):
        return SimpleNamespace(model_dump=lambda: {
            "taskId": "fake-image-batch-task",
            "providerTaskId": "fake-provider-batch-task",
            "mode": "batch",
            "status": "success",
            "prompt": request.prompt,
            "style": request.style,
            "size": request.size,
            "count": len(request.prompts or []),
            "seed": request.seed,
            "negativePrompt": request.negativePrompt,
            "images": [
                {"index": index, "url": f"https://example.com/fake-{index}.png", "status": "success"}
                for index, _ in enumerate(request.prompts or [])
            ],
            "message": "测试批量图片生成成功",
            "metadata": request.metadata,
        })


if __name__ == "__main__":
    unittest.main()
