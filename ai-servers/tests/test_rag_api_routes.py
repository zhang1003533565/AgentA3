import base64
import importlib
import os
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app
from app.model_providers.multimodal import build_multimodal_human_content, extract_image_references
from app.model_providers.runtime_config import LlmRuntimeConfig
from app.services import langchain_chat_service


class RagApiRoutesTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.headers = {
            "Authorization": "Bearer test-token",
            "X-AI-Provider": "deepseek",
            "X-AI-Base-Url": "https://llm.test/v1",
            "X-AI-Api-Key": "test-key",
            "X-AI-Model": "test-model",
        }
        self._patched_modules = []
        self._patch_chat_services()

    def tearDown(self):
        for module, old_get_chat_service in reversed(self._patched_modules):
            module.get_chat_service = old_get_chat_service

    def _patch_chat_services(self):
        module_names = [
            "app.multi_agents.leader_agent.agent",
            "app.multi_agents.mind_map_agent.agent",
            "app.multi_agents.textbook_knowledge_agent.agent",
            "app.multi_agents.question_type_agents",
            "app.multi_agents.meeting_agents",
            "app.multi_agents.ppt_agents",
            "app.multi_agents.image_agent.agent",
        ]
        for module_name in module_names:
            module = importlib.import_module(module_name)
            self._patched_modules.append((module, module.get_chat_service))
            module.get_chat_service = lambda service=FakeRagChatService(): service

    def test_list_strategies_returns_all_rag_modes(self):
        response = self.client.get("/internal/rag/strategies", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        names = {item["name"] for item in payload["strategies"]}
        self.assertEqual(16, len(names))
        self.assertIn("multi_agent_rag", names)
        self.assertIn("text_to_sql", names)

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

    def test_ingest_and_list_documents(self):
        old_root = os.environ.get("RAG_KNOWLEDGE_BASE_DIR")
        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                os.environ["RAG_KNOWLEDGE_BASE_DIR"] = temp_dir
                response = self.client.post(
                    "/internal/rag/documents",
                    headers=self.headers,
                    json={
                        "documents": [
                            {
                                "source": "校园卡服务.md",
                                "content": "校园卡补办地点在行政楼一楼服务大厅。",
                            }
                        ]
                    },
                )

                self.assertEqual(200, response.status_code)
                payload = response.json()
                self.assertEqual(1, payload["storedCount"])
                self.assertGreaterEqual(payload["indexedChunkCount"], 1)
                self.assertTrue(Path(payload["storedFiles"][0]).exists())
                self.assertTrue(Path(payload["indexPath"]).exists())
                self.assertEqual("markdown", payload["documents"][0]["modality"])
                self.assertIn("index", {step["stage"] for step in payload["trace"]})

                list_response = self.client.get("/internal/rag/documents", headers=self.headers)
                self.assertEqual(200, list_response.status_code)
                self.assertTrue(list_response.json()["documents"])
        finally:
            if old_root is None:
                os.environ.pop("RAG_KNOWLEDGE_BASE_DIR", None)
            else:
                os.environ["RAG_KNOWLEDGE_BASE_DIR"] = old_root

    def test_ingest_accepts_base64_multimodal_file(self):
        old_root = os.environ.get("RAG_KNOWLEDGE_BASE_DIR")
        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                os.environ["RAG_KNOWLEDGE_BASE_DIR"] = temp_dir
                raw_bytes = b"\x89PNG\r\n\x1a\nfake-image"
                response = self.client.post(
                    "/internal/rag/documents",
                    headers=self.headers,
                    json={
                        "documents": [
                            {
                                "source": "notice.png",
                                "contentBase64": base64.b64encode(raw_bytes).decode("ascii"),
                                "metadata": {"origin": "unit_test"},
                            }
                        ]
                    },
                )

                self.assertEqual(200, response.status_code)
                payload = response.json()
                self.assertEqual("image", payload["documents"][0]["modality"])
                stored_path = Path(payload["storedFiles"][0])
                self.assertEqual(raw_bytes, stored_path.read_bytes())
                self.assertGreaterEqual(payload["indexedChunkCount"], 1)
        finally:
            if old_root is None:
                os.environ.pop("RAG_KNOWLEDGE_BASE_DIR", None)
            else:
                os.environ["RAG_KNOWLEDGE_BASE_DIR"] = old_root

    def test_rag_routes_require_authorization(self):
        response = self.client.get("/internal/rag/strategies")

        self.assertEqual(401, response.status_code)

    def test_vector_store_health_returns_backend_metadata(self):
        response = self.client.get("/internal/rag/vector-store/health", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("local_jsonl", payload["backend"])
        self.assertIn("local_chunks.jsonl", payload["indexPath"])

    def test_vector_store_health_supports_scaffolded_backend(self):
        old_backend = os.environ.get("RAG_VECTOR_STORE_BACKEND")
        try:
            os.environ["RAG_VECTOR_STORE_BACKEND"] = "milvus"
            response = self.client.get("/internal/rag/vector-store/health", headers=self.headers)

            self.assertEqual(200, response.status_code)
            payload = response.json()
            self.assertEqual("milvus", payload["backend"])
            self.assertEqual("implemented_optional", payload["status"])
            self.assertIn("RAG_MILVUS_URI", payload["missingEnv"])
        finally:
            if old_backend is None:
                os.environ.pop("RAG_VECTOR_STORE_BACKEND", None)
            else:
                os.environ["RAG_VECTOR_STORE_BACKEND"] = old_backend

    def test_capabilities_endpoint_describes_runtime_framework(self):
        response = self.client.get("/internal/rag/capabilities", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertIn("naive_rag", payload["query"]["strategies"])
        self.assertIn("hybrid", payload["retrieval"]["retrievers"])
        self.assertIn("faithfulness", payload["evaluation"]["metrics"])
        self.assertIn("textbook_knowledge_agent", payload["agents"])

    def test_framework_endpoint_describes_full_runtime_layout(self):
        response = self.client.get("/internal/rag/framework", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(16, len(payload["coverage"]))
        self.assertIn("app/rag/strategies", payload["runtimeFolders"]["strategies"])
        self.assertIn("local_jsonl", {item["name"] for item in payload["vectorStores"]})
        self.assertIn("neo4j", {item["name"] for item in payload["graphStores"]})
        self.assertIn("RAG_KNOWLEDGE_BASE_DIR", {item["name"] for item in payload["runtimeEnv"]})
        self.assertIn("xiaomi", {item["name"] for item in payload["modelProviders"]})
        self.assertIn("qwen", {item["name"] for item in payload["modelProviders"]})

    def test_chat_service_selects_xiaomi_provider(self):
        class FakeXiaomiProvider:
            def __init__(self, config):
                self.config = config

        old_xiaomi_provider = langchain_chat_service.XiaomiProvider
        try:
            langchain_chat_service.XiaomiProvider = FakeXiaomiProvider
            provider = langchain_chat_service.build_chat_model_provider(LlmRuntimeConfig(
                provider="xiaomi",
                base_url="https://api.xiaomimimo.com/v1",
                api_key="test-key",
                model="mimo-v2.5-pro",
            ))
        finally:
            langchain_chat_service.XiaomiProvider = old_xiaomi_provider

        self.assertIsInstance(provider, FakeXiaomiProvider)
        self.assertEqual("mimo-v2.5-pro", provider.config.model)


    def test_chat_service_selects_qwen_provider(self):
        class FakeQwenProvider:
            def __init__(self, config):
                self.config = config

        old_qwen_provider = langchain_chat_service.QwenProvider
        try:
            langchain_chat_service.QwenProvider = FakeQwenProvider
            provider = langchain_chat_service.build_chat_model_provider(LlmRuntimeConfig(
                provider="qwen",
                base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
                api_key="test-key",
                model="qwen-vl-plus",
            ))
        finally:
            langchain_chat_service.QwenProvider = old_qwen_provider

        self.assertIsInstance(provider, FakeQwenProvider)
        self.assertEqual("qwen-vl-plus", provider.config.model)

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
        self.assertEqual(21, payload["total"])
        self.assertIn("textbook_knowledge_agent", names)
        self.assertIn("textbook_question_single_choice_agent", names)
        self.assertIn("textbook_question_programming_agent", names)
        self.assertIn("meeting_controller_agent", names)
        self.assertIn("meeting_voice_broadcast_agent", names)
        self.assertIn("ppt_outline_agent", names)
        self.assertIn("ppt_layout_agent", names)
        self.assertIn("ppt_review_agent", names)
        self.assertIn("ppt_image_agent", names)
        self.assertNotIn("textbook_question_bank_agent", names)
        self.assertNotIn("md_knowledge_agent", names)
        self.assertNotIn("answer_agent", names)
        self.assertNotIn("retriever_agent", names)
        textbook_agent = next(item for item in payload["agents"] if item["name"] == "textbook_knowledge_agent")
        self.assertIn("skill.md", textbook_agent["files"]["skill"])
        self.assertIn("教材知识点智能体 Skill", textbook_agent["documents"]["skill"])
        self.assertTrue(textbook_agent["needRetrieval"])
        self.assertTrue(textbook_agent["supportedRagStrategies"])
        leader_agent = next(item for item in payload["agents"] if item["name"] == "leader_agent")
        self.assertFalse(leader_agent["needRetrieval"])
        self.assertEqual([], leader_agent["supportedRagStrategies"])
        self.assertIn("questionBank", payload["workflow"])
        self.assertEqual("agentName", payload["invocation"]["parameter"])

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
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("multi_agent_rag", payload["strategy"])
        self.assertEqual("ppt_outline_agent", payload["metadata"]["agentName"])
        self.assertIn("PPT 大纲", payload["answer"])
        self.assertIn("### 大纲信息", payload["answer"])
        self.assertIn("- 使用场景：", payload["answer"])
        self.assertIn("- 受众：", payload["answer"])
        self.assertNotIn("讲解目标", payload["answer"])
        self.assertNotIn("页面内容建议", payload["answer"])
        self.assertNotIn("课堂互动建议", payload["answer"])
        self.assertEqual("agent_answer", payload["trace"][-1]["stage"])
        self.assertEqual("rag_then_agent", payload["metadata"]["executionMode"])

    def test_ppt_layout_normalizer_rewrites_legacy_fields(self):
        raw = """## PPT 布局方案
### 第 1 页：封面布局
- 版式类型：封面布局
- 标题区：页面上方居中标题
- 正文区：副标题和课程信息
- 图表/图片区：背景示意图
- 视觉层级：标题最强
- 留白：四周留白充足
- 讲解动线：从标题到副标题到背景图
"""
        outline = """## PPT 大纲
### 大纲信息
- 主题：数据结构中的栈与队列
- 使用场景：学术
- 受众：学生
- 建议页数：6 页
- 整体目标：帮助学生理解栈与队列
- 风格建议：结构清晰

### 第1页
- 页标题：数据结构中的栈与队列
- 页面类型：封面页
- 本页目标：引入主题
- 核心内容：
  - 主标题
  - 副标题
- 展示建议：封面大标题布局
- 素材建议：主视觉插图
"""
        normalized = langchain_chat_service._normalize_ppt_layout_answer(
            raw,
            outline,
        )
        self.assertIn("### 布局信息", normalized)
        self.assertIn("- 使用场景：学术", normalized)
        self.assertIn("- 受众：学生", normalized)
        self.assertIn("- 页面类型：封面页", normalized)
        self.assertIn("- 布局结构：", normalized)
        self.assertIn("- 信息层级：", normalized)
        self.assertIn("- 区域安排：", normalized)
        self.assertIn("- 视觉建议：", normalized)
        self.assertIn("- 素材处理：", normalized)
        self.assertNotIn("版式类型", normalized)
        self.assertNotIn("标题区", normalized)
        self.assertNotIn("正文区", normalized)
        self.assertNotIn("图表/图片区", normalized)
        self.assertNotIn("视觉层级", normalized)
        self.assertNotIn("留白", normalized)
        self.assertNotIn("讲解动线", normalized)

    def test_leader_agent_routes_and_executes_specialist(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "帮我把数据结构中的栈与队列整理成 PPT 大纲",
                "agentName": "leader_agent",
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("leader_agent", payload["metadata"]["agentName"])
        self.assertEqual("ppt_outline_agent", payload["metadata"]["targetAgent"])
        self.assertEqual("ppt_outline_agent", payload["metadata"]["executedAgent"])
        self.assertEqual("multi_agent_rag", payload["metadata"]["plannedRagStrategy"])
        self.assertEqual("multi_agent_rag", payload["strategy"])
        self.assertEqual("leader_routed_rag", payload["metadata"]["executionMode"])
        self.assertEqual("leader_route", payload["trace"][0]["stage"])
        self.assertIn("PPT 大纲", payload["answer"])

    def test_leader_agent_answers_smalltalk_without_rag(self):
        response = self.client.post(
            "/internal/rag/query",
            headers=self.headers,
            json={
                "input": "你好",
                "agentName": "leader_agent",
                "ragStrategy": "adaptive_rag",
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("leader_direct_answer", payload["strategy"])
        self.assertEqual("leader_direct_answer", payload["metadata"]["executionMode"])
        self.assertTrue(payload["metadata"]["retrievalSkipped"])
        self.assertEqual([], payload["documents"])
        self.assertIn("LLM 已接入", payload["answer"])

    def test_leader_agent_uses_request_llm_config_when_forwarded_from_java(self):
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
        self.assertEqual("LLM 已接入：你好", payload["answer"])
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

    def test_agent_catalog_examples_are_runnable_for_specialists(self):
        catalog_response = self.client.get("/internal/rag/agents", headers=self.headers)
        self.assertEqual(200, catalog_response.status_code)

        specialists = [
            agent for agent in catalog_response.json()["agents"]
            if agent["name"] != "leader_agent"
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
                        **({"ragStrategy": example["ragStrategy"]} if "ragStrategy" in example else {}),
                    },
                )

                self.assertEqual(200, response.status_code)
                payload = response.json()
                self.assertEqual(agent["name"], payload["metadata"]["agentName"])
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

    def test_embedding_health_returns_provider_metadata(self):
        response = self.client.get("/internal/rag/embedding/health", headers=self.headers)

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("local_lexical", payload["provider"])
        self.assertEqual("implemented", payload["status"])

    def test_embedding_health_supports_scaffolded_provider(self):
        old_provider = os.environ.get("RAG_EMBEDDING_PROVIDER")
        old_key = os.environ.get("OPENAI_API_KEY")
        try:
            os.environ["RAG_EMBEDDING_PROVIDER"] = "openai"
            os.environ.pop("OPENAI_API_KEY", None)
            response = self.client.get("/internal/rag/embedding/health", headers=self.headers)

            self.assertEqual(200, response.status_code)
            payload = response.json()
            self.assertEqual("openai", payload["provider"])
            self.assertEqual("implemented_optional", payload["status"])
            self.assertIn("OPENAI_API_KEY", payload["requiredEnv"])
        finally:
            if old_provider is None:
                os.environ.pop("RAG_EMBEDDING_PROVIDER", None)
            else:
                os.environ["RAG_EMBEDDING_PROVIDER"] = old_provider
            if old_key is not None:
                os.environ["OPENAI_API_KEY"] = old_key

    def test_evaluate_endpoint_returns_rag_metrics(self):
        response = self.client.post(
            "/internal/rag/evaluate",
            headers=self.headers,
            json={
                "query": "校园卡补办地点",
                "answer": "校园卡补办地点在行政楼一楼服务大厅。",
                "documents": [
                    {
                        "id": "doc-1",
                        "content": "校园卡补办地点在行政楼一楼服务大厅。",
                        "source": "card.md",
                        "score": 1.0,
                        "metadata": {},
                    }
                ],
                "expectedSources": ["card.md"],
                "expectedAnswerTerms": ["行政楼"],
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertTrue(payload["passed"])
        self.assertGreater(payload["metrics"]["hitRate"], 0)
        self.assertGreater(payload["metrics"]["faithfulness"], 0)

    def test_graph_store_health_returns_backend(self):
        response = self.client.get("/internal/rag/graph-store/health", headers=self.headers)

        self.assertEqual(200, response.status_code)
        self.assertEqual("local_graph", response.json()["backend"])

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
        normalized = langchain_chat_service._normalize_ppt_outline_answer(
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

class FakeRagChatService:
    def plan_leader_intent(self, input_text, rag_strategy=""):
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
        if "PPT" in text or "课件" in text:
            return {
                "intent": "ppt_outline",
                "target_agent": "ppt_outline_agent",
                "need_retrieval": True,
                "rag_strategy": rag_strategy or "multi_agent_rag",
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

    def generate_specialist_answer(self, agent_name, input_text, evidence):
        labels = {
            "mind_map_agent": "```mermaid\nmindmap\n  root((测试思维导图))\n```",
            "textbook_knowledge_agent": "## 教材知识点\n- 基于 LLM 整理教材知识点",
            "textbook_question_single_choice_agent": "## 选择题\n1. 【单选题】说明核心知识点。",
            "textbook_question_fill_blank_agent": "## 填空题\n1. 核心概念是____。",
            "textbook_question_true_false_agent": "## 判断题\n1. 核心概念判断。",
            "textbook_question_multiple_choice_agent": "## 多选题\n1. 【多选题】说明核心知识点。",
            "textbook_question_short_answer_agent": "## 简答题\n1. 简述核心知识点。",
            "textbook_question_calculation_agent": "## 计算题\n1. 计算核心指标。",
            "textbook_question_programming_agent": "## 编程题\n1. 实现核心算法。",
            "meeting_controller_agent": "## 会议总控\n- 当前状态：进行中",
            "meeting_transcription_agent": "## 会议转写\n- 发言人A：测试发言",
            "meeting_summary_agent": "## 会议总结\n- 主要结论：测试结论",
            "meeting_member_analysis_agent": "## 成员分析\n- 成员A：参与积极",
            "meeting_resource_recommendation_agent": "## 资源推荐\n- 成员A：推荐复习资料",
            "meeting_voice_broadcast_agent": "## 语音播报稿\n请大家关注会议结论。",
            "ppt_outline_agent": """## PPT 大纲
### 第 1 页：课程导入
- **页标题：** 数据结构中的栈与队列
- **讲解目标：** 说明主题。
- **页面内容建议：**
  - 主标题：数据结构中的栈与队列
  - 副标题：课程导入
- **课堂互动建议：** 提问。""",
            "ppt_layout_agent": """## PPT 布局方案
### 第 1 页：封面布局
- 版式类型：封面布局
- 标题区：页面上方居中标题
- 正文区：副标题和课程信息
- 图表/图片区：背景示意图
- 视觉层级：标题最强
- 留白：四周留白充足
- 讲解动线：从标题到副标题到背景图""",
            "ppt_review_agent": "## PPT 审查报告\n置信度评分：86/100",
            "ppt_image_agent": "## PPT 图片提示词\n### 封面图",
            "image_agent": "## 图片智能体提示词\n主题：教学配图",
        }
        answer = labels.get(agent_name, f"{agent_name}: {input_text}")
        if agent_name == "ppt_outline_agent":
            return langchain_chat_service._normalize_ppt_outline_answer(answer, input_text)
        if agent_name == "ppt_layout_agent":
            return langchain_chat_service._normalize_ppt_layout_answer(answer, input_text)
        return answer

    def extract_search_keyword(self, input_text):
        return "测试关键词"


if __name__ == "__main__":
    unittest.main()
