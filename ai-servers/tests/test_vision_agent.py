import base64
import io
import importlib
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app.model_providers.catalog import model_supports_vision
from app.model_providers.multimodal import build_explicit_multimodal_content, build_multimodal_human_content, collect_request_image_references
from app.multi_agents.runtime import build_agent_user_prompt, complete_vision_agent_or_raise
from app.models.schemas import RagQueryRequest
from app.multi_agents.catalog import get_agent_catalog, normalize_agent_name
from app.services.image_stitching import StitchImage, collect_stitch_images, stitch_images


class VisionAgentTest(unittest.TestCase):
    def setUp(self):
        self.rag_routes = importlib.import_module("app.api.routes.rag")

    def test_catalog_exposes_vision_agent_and_tool_binding(self):
        agents = {item["name"]: item for item in get_agent_catalog()["agents"]}
        self.assertEqual(["vision"], agents["vision_agent"]["requiredModelModalities"])
        self.assertEqual("vision_agent", normalize_agent_name("识图"))

        callable_catalog = self.rag_routes._build_leader_callable_catalog()
        tools = {item["name"]: item for item in callable_catalog["tools"]}
        self.assertNotIn("boundAgent", tools["recognize_image_tool"])
        self.assertIn("purpose", tools["recognize_image_tool"])
        self.assertNotIn("vision_agent", {item["name"] for item in tools.values()})

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

    def test_build_explicit_multimodal_content_requires_images(self):
        content = build_explicit_multimodal_content(
            "请识别图片",
            ["data:image/jpeg;base64,/9j/ABC"],
        )
        self.assertEqual(["text", "image_url"], [part["type"] for part in content])
        self.assertEqual("请识别图片", content[0]["text"])
        self.assertTrue(str(content[1]["image_url"]["url"]).startswith("data:image/jpeg"))

    def test_vision_agent_user_prompt_keeps_image_references_for_multimodal(self):
        input_text = "请识别图片\n\n![用户上传图片1](data:image/jpeg;base64,/9j/ABC)"
        user_prompt = build_agent_user_prompt("vision_agent", input_text, [])
        self.assertIn("data:image/jpeg;base64,/9j/ABC", user_prompt)
        self.assertNotIn("agent_name", user_prompt)
        content = build_multimodal_human_content(user_prompt)
        self.assertIsInstance(content, list)
        self.assertEqual(["text", "image_url"], [part["type"] for part in content])
        self.assertIn("请识别图片", content[0]["text"])

    def test_model_supports_vision_detects_catalog_and_heuristics(self):
        self.assertTrue(model_supports_vision("deepseek", "deepseek-v4-flash-vision-exp"))
        self.assertFalse(model_supports_vision("deepseek", "deepseek-v4-flash"))
        self.assertTrue(model_supports_vision("qwen", "qwen-vl-max"))

    def test_vision_agent_rejects_text_only_model_binding(self):
        request = RagQueryRequest(
            input="请识别图片",
            metadata={
                "agentModelConfigs": {
                    "vision_agent": {
                        "provider": "deepseek",
                        "baseUrl": "https://api.deepseek.com",
                        "apiKey": "test-key",
                        "model": "deepseek-v4-flash",
                        "tested": True,
                    },
                },
            },
        )
        with self.assertRaises(self.rag_routes.AgentExecutionError) as ctx:
            self.rag_routes._require_agent_runtime_config(request, "vision_agent")
        self.assertIn("不支持图片输入", str(ctx.exception))

    def test_admin_direct_recognition_injects_images_before_vision_call(self):
        tiny_png = (
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
        request = RagQueryRequest(
            input="请识别我上传的图片，概括主要内容并读取其中清晰可见的文字。",
            imageDataUrls=[f"data:image/png;base64,{tiny_png}"],
            metadata={
                "testFrom": "admin_tool_console",
                "directToolTest": True,
                "expectedToolName": "recognize_image_tool",
                "agentToggles": {"vision_agent": True},
                "toolToggles": {"recognize_image_tool": True},
                "agentModelConfigs": {
                    "vision_agent": {
                        "provider": "qwen",
                        "baseUrl": "https://vision.test/v1",
                        "apiKey": "test-key",
                        "model": "qwen3.6-plus",
                        "tested": True,
                    },
                },
            },
        )
        with patch.object(
            self.rag_routes,
            "_run_specialist_agent_with_bound_model",
            return_value=("画面里有一个红点。", {
                "modelProvider": "qwen",
                "model": "qwen3.6-plus",
            }),
        ) as run_agent:
            response = self.rag_routes._run_admin_direct_tool_test(request, "Bearer test")

        self.assertIsNotNone(response)
        self.assertIn("data:image/png;base64", run_agent.call_args.args[2])
        self.assertEqual(1, response.metadata["imageCount"])

    def test_tool_is_unavailable_without_vision_model_binding(self):
        request = SimpleNamespace(
            input="请识别图片",
            metadata={"toolToggles": {"recognize_image_tool": False}},
        )
        catalog = self.rag_routes._build_leader_callable_catalog(request)
        self.assertNotIn("recognize_image_tool", {item["name"] for item in catalog["tools"]})

    def test_multiple_uploaded_images_route_to_stitching_before_recognition(self):
        request = RagQueryRequest(
            input="请把这些图片拼接起来",
            imageDataUrls=[
                "data:image/png;base64,AA==",
                "data:image/png;base64,AA==",
            ],
            metadata={"uploadOnly": True, "toolToggles": {"image_stitching_tool": True}},
        )
        plan = self.rag_routes._requested_image_stitching_plan(request)
        self.assertIsNotNone(plan)
        self.assertEqual("image_stitching_tool", plan.tool_name)
        self.assertEqual("attachment", plan.route_mode)

    def test_stitching_tool_returns_one_image_attachment_and_trace(self):
        request = RagQueryRequest(
            input="请拼接这些图片",
            metadata={"toolToggles": {"image_stitching_tool": True}},
        )
        plan = self.rag_routes.LeaderPlan(
            intent="image_stitching",
            target_agent="leader_agent",
            need_retrieval=False,
            rag_strategy="",
            action="call_tool",
            tool_name="image_stitching_tool",
            route_reason="测试图片拼接",
            route_mode="attachment",
        )
        images = [
            StitchImage(b"first", "one.png", "image/png"),
            StitchImage(b"second", "two.png", "image/png"),
        ]
        tiny_png = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
        with patch.object(self.rag_routes, "collect_stitch_images", return_value=images), \
                patch.object(self.rag_routes, "stitch_images", return_value=tiny_png):
            response = self.rag_routes._run_image_stitching_tool(request, plan)

        self.assertEqual("image_stitching_tool", response.strategy)
        self.assertEqual("image_stitching", response.answerType)
        self.assertEqual("grid", response.metadata["layout"])
        self.assertEqual(2, response.metadata["columns"])
        self.assertTrue(response.metadata["numbered"])
        self.assertEqual(2, response.metadata["imageCount"])
        self.assertEqual("image_stitching_tool", response.metadata["toolName"])
        self.assertEqual("image_stitching_tool", response.trace[-1].stage)
        self.assertEqual(1, len(response.attachments))

    def test_stitch_images_decodes_png_uploads(self):
        png = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
        result = stitch_images([
            StitchImage(png, "one.png", "image/png"),
            StitchImage(png, "two.png", "image/png"),
        ])
        self.assertTrue(result.startswith(b"\x89PNG\r\n\x1a\n"))

    def test_stitch_images_uses_three_column_grid(self):
        png = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
        result = stitch_images([StitchImage(png, f"{index}.png", "image/png") for index in range(9)])
        from PIL import Image

        with Image.open(io.BytesIO(result)) as output:
            self.assertEqual(3, output.height)
            self.assertGreater(output.width, 3)

    def test_stitch_images_draws_sequence_label_on_each_image(self):
        from PIL import Image

        source = io.BytesIO()
        Image.new("RGB", (100, 80), "white").save(source, format="PNG")
        png = source.getvalue()
        result = stitch_images([
            StitchImage(png, "one.png", "image/png"),
            StitchImage(png, "two.png", "image/png"),
        ])

        with Image.open(io.BytesIO(result)).convert("RGB") as output:
            item_width = output.width // 2
            gutter_width = item_width - 100
            self.assertEqual(80, output.height)
            self.assertGreater(gutter_width, 0)
            for left in (0, item_width):
                label_area = output.crop((left, 0, left + gutter_width, 80)).convert("L")
                image_area = output.crop((left + gutter_width, 0, left + item_width, 80))
                self.assertLess(label_area.getextrema()[0], 255)
                self.assertEqual(((255, 255), (255, 255), (255, 255)), image_area.getextrema())

    def test_stitch_images_aligns_image_edges_within_each_column(self):
        from PIL import Image

        def make_png(size, color):
            source = io.BytesIO()
            Image.new("RGB", size, color).save(source, format="PNG")
            return source.getvalue()

        result = stitch_images([
            StitchImage(make_png((100, 80), (220, 40, 40)), "one.png", "image/png"),
            StitchImage(make_png((100, 80), (40, 180, 80)), "two.png", "image/png"),
            StitchImage(make_png((100, 80), (40, 80, 220)), "three.png", "image/png"),
            StitchImage(make_png((100, 300), (220, 180, 40)), "four.png", "image/png"),
        ])

        with Image.open(io.BytesIO(result)).convert("RGB") as output:
            red_x = next(x for x in range(output.width) if output.getpixel((x, 20)) == (220, 40, 40))
            yellow_x = next(x for x in range(output.width) if output.getpixel((x, 100)) == (220, 180, 40))
            self.assertEqual(red_x, yellow_x)

    def test_stitching_does_not_cap_image_count(self):
        request = RagQueryRequest(
            input="请拼接这些图片",
            imageDataUrls=["data:image/png;base64,AA=="] * 20,
        )
        self.assertEqual(20, len(collect_stitch_images(request)))

    def test_attachment_pipeline_groups_by_nine_then_returns_results_to_leader(self):
        tiny_png = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
        encoded = base64.b64encode(tiny_png).decode("ascii")
        request = RagQueryRequest(
            input="识别后总结",
            attachments=[{
                "name": f"{index}.png",
                "mimeType": "image/png",
                "contentBase64": f"data:image/png;base64,{encoded}",
            } for index in range(10)],
            metadata={},
        )
        leader_plan = self.rag_routes.LeaderPlan(
            intent="summary", target_agent="leader_agent", need_retrieval=False,
            rag_strategy="", action="direct_answer", answer="综合结果",
            route_reason="已完成输入处理", route_mode="rules",
        )
        leader_response = self.rag_routes.RagQueryResponse(
            strategy="leader_direct", answer="综合结果", trace=[], metadata={},
        )
        with patch.object(self.rag_routes, "stitch_images", return_value=tiny_png) as stitch, \
                patch.object(self.rag_routes, "_run_specialist_agent_with_bound_model", return_value=("视觉摘要", {})) as vision, \
                patch.object(self.rag_routes.leader_agent, "plan", return_value=leader_plan) as leader, \
                patch.object(self.rag_routes, "_execute_leader_plan", return_value=leader_response):
            response = self.rag_routes._run_attachment_input_pipeline(request, "Bearer test", {}, {"tools": []})

        self.assertEqual(1, stitch.call_count)
        self.assertEqual(2, len(vision.call_args.args[0].imageDataUrls))
        self.assertIn("视觉摘要", leader.call_args.args[0])
        grouping = next(item for item in response.trace if item.stage == "image_grouping")
        self.assertEqual([9, 1], grouping.detail["groupImageCounts"])
        self.assertEqual("system", grouping.detail["triggerType"])

    def test_attachment_pipeline_skips_image_payload_for_leader_when_recognition_disabled(self):
        tiny_png = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
        encoded = base64.b64encode(tiny_png).decode("ascii")
        request = RagQueryRequest(
            input="这个图片有什么",
            attachments=[{
                "name": "photo.png",
                "mimeType": "image/png",
                "contentBase64": f"data:image/png;base64,{encoded}",
            }],
            metadata={"contextOriginalInput": "这个图片有什么"},
        )
        with patch.object(self.rag_routes, "_is_tool_enabled", return_value=False), \
                patch.object(self.rag_routes.leader_agent, "plan") as leader_plan, \
                patch.object(self.rag_routes, "_run_specialist_agent_with_bound_model") as vision:
            response = self.rag_routes._run_attachment_input_pipeline(request, "Bearer test", {}, {"tools": []})

        vision.assert_not_called()
        leader_plan.assert_not_called()
        self.assertIn("图片识别能力当前未启用", response.answer)


if __name__ == "__main__":
    unittest.main()
