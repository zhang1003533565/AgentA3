import importlib
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app.model_providers.multimodal import collect_request_image_references
from app.models.schemas import RagQueryRequest
from app.multi_agents.catalog import get_agent_catalog, normalize_agent_name


class VisionAgentTest(unittest.TestCase):
    def setUp(self):
        self.rag_routes = importlib.import_module("app.api.routes.rag")

    def test_catalog_exposes_vision_agent_and_tool_binding(self):
        agents = {item["name"]: item for item in get_agent_catalog()["agents"]}
        self.assertEqual(["vision"], agents["vision_agent"]["requiredModelModalities"])
        self.assertEqual("vision_agent", normalize_agent_name("识图"))

        callable_catalog = self.rag_routes._build_leader_callable_catalog()
        tools = {item["name"]: item for item in callable_catalog["tools"]}
        self.assertEqual("vision_agent", tools["recognize_image_tool"]["boundAgent"])
        self.assertNotIn("vision_agent", {item["name"] for item in callable_catalog["agents"]})

    def test_only_image_attachments_trigger_recognition_plan(self):
        image_request = RagQueryRequest(
            input="请分析我上传的资源",
            attachments=[{
                "type": "image",
                "mimeType": "image/png",
                "name": "screen.png",
                "url": "https://files.test/screen.png",
            }],
        )
        plan = self.rag_routes._requested_image_recognition_plan(image_request)
        self.assertIsNotNone(plan)
        self.assertEqual("recognize_image_tool", plan.tool_name)
        self.assertEqual("attachment", plan.route_mode)

        pdf_request = RagQueryRequest(
            input="请分析我上传的资源",
            attachments=[{
                "type": "document",
                "mimeType": "application/pdf",
                "name": "notes.pdf",
                "url": "https://files.test/notes.pdf",
            }],
        )
        self.assertEqual([], collect_request_image_references(pdf_request))
        self.assertIsNone(self.rag_routes._requested_image_recognition_plan(pdf_request))

    def test_recognition_tool_calls_bound_agent(self):
        request = RagQueryRequest(
            input="这张图说明了什么？\n\n![用户上传图片1](https://files.test/chart.png)",
            attachments=[{
                "type": "image",
                "url": "https://files.test/chart.png",
            }],
            metadata={
                "agentToggles": {"vision_agent": True},
                "toolToggles": {"recognize_image_tool": True},
                "agentModelConfigs": {
                    "vision_agent": {
                        "provider": "qwen",
                        "baseUrl": "https://vision.test/v1",
                        "apiKey": "test-key",
                        "model": "qwen-vl-test",
                        "tested": True,
                    },
                },
            },
        )
        plan = self.rag_routes._requested_image_recognition_plan(request)
        with patch.object(
            self.rag_routes,
            "_run_specialist_agent_with_bound_model",
            return_value=("图中是一张趋势图。", {
                "modelProvider": "qwen",
                "model": "qwen-vl-test",
            }),
        ) as run_agent:
            response = self.rag_routes._run_image_recognition_tool(request, plan)

        self.assertEqual("image_analysis", response.answerType)
        self.assertEqual("recognize_image_tool", response.strategy)
        self.assertEqual("vision_agent", response.metadata["boundAgent"])
        self.assertEqual(1, response.metadata["imageCount"])
        self.assertEqual("vision_agent", run_agent.call_args.args[1])

    def test_tool_is_unavailable_without_vision_model_binding(self):
        request = SimpleNamespace(metadata={
            "agentToggles": {"vision_agent": True},
            "toolToggles": {"recognize_image_tool": True},
            "agentModelConfigs": {},
        })
        catalog = self.rag_routes._build_leader_callable_catalog(request)
        tool = next(item for item in catalog["tools"] if item["name"] == "recognize_image_tool")
        self.assertFalse(tool["enabled"])


if __name__ == "__main__":
    unittest.main()
