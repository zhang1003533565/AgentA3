# -*- coding: utf-8 -*-
"""Tests for text-to-file tools (text -> md/txt/docx)."""

import importlib
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from app.rag.document_conversion import export_text_to_file
from app.rag.document_conversion import generated_exporter

SAMPLE_CONTENT = "# 智慧校园二手交易\n\n- 当面验货\n- 确认商品状态\n- 线下完成交易"


class TextToFileExporterTest(unittest.TestCase):
    def setUp(self):
        self.original_root = generated_exporter.EXPORT_ROOT
        self._test_root = Path(tempfile.mkdtemp(prefix="text-to-file-test-"))
        generated_exporter.EXPORT_ROOT = self._test_root

    def tearDown(self):
        generated_exporter.EXPORT_ROOT = self.original_root
        shutil.rmtree(self._test_root, ignore_errors=True)

    def test_md_and_txt_preserve_original_text(self):
        md_result = export_text_to_file(SAMPLE_CONTENT, "md", {})
        self.assertEqual(["md"], [item["ext"] for item in md_result.attachments])
        txt_result = export_text_to_file(SAMPLE_CONTENT, "txt", {})
        self.assertEqual(["txt"], [item["ext"] for item in txt_result.attachments])

        md_text = (generated_exporter.EXPORT_ROOT / md_result.attachments[0]["storageKey"]).read_text(encoding="utf-8")
        txt_text = (generated_exporter.EXPORT_ROOT / txt_result.attachments[0]["storageKey"]).read_text(encoding="utf-8")
        self.assertIn("智慧校园二手交易", md_text)
        self.assertEqual(SAMPLE_CONTENT, txt_text)

    def test_docx_is_a_valid_word_document(self):
        from docx import Document
        result = export_text_to_file(SAMPLE_CONTENT, "docx", {})
        self.assertEqual(["docx"], [item["ext"] for item in result.attachments])
        document = Document(generated_exporter.EXPORT_ROOT / result.attachments[0]["storageKey"])
        text = "\n".join(paragraph.text for paragraph in document.paragraphs)
        self.assertIn("智慧校园二手交易", text)
        self.assertIn("当面验货", text)

    def test_word_alias_maps_to_docx(self):
        result = export_text_to_file(SAMPLE_CONTENT, "word", {})
        self.assertEqual(["docx"], [item["ext"] for item in result.attachments])

    def test_markdown_alias_maps_to_md(self):
        result = export_text_to_file(SAMPLE_CONTENT, "markdown", {})
        self.assertEqual(["md"], [item["ext"] for item in result.attachments])

    def test_unsupported_format_is_rejected(self):
        result = export_text_to_file(SAMPLE_CONTENT, "pdf", {})
        self.assertEqual([], result.attachments)
        self.assertEqual("unsupported_format", result.diagnostics["reason"])
        result = export_text_to_file(SAMPLE_CONTENT, "weird-format", {})
        self.assertEqual([], result.attachments)
        self.assertEqual("unsupported_format", result.diagnostics["reason"])

    def test_empty_content_is_skipped(self):
        result = export_text_to_file("   ", "md", {})
        self.assertEqual([], result.attachments)
        self.assertEqual("empty_answer", result.diagnostics["reason"])

    def test_disabled_toggle_skips_export(self):
        result = export_text_to_file(
            SAMPLE_CONTENT,
            "md",
            {"toolToggles": {"text_to_markdown_tool": False}},
        )
        self.assertEqual([], result.attachments)
        self.assertEqual("tool_disabled", result.diagnostics["reason"])
        self.assertEqual("text_to_markdown_tool", result.diagnostics["disabledTool"])

        result = export_text_to_file(
            SAMPLE_CONTENT,
            "txt",
            {"toolToggles": {"text_to_txt_tool": False}},
        )
        self.assertEqual("text_to_txt_tool", result.diagnostics["disabledTool"])


class TextToFileRagRouteTest(unittest.TestCase):
    def setUp(self):
        self._rag_routes = importlib.import_module("app.api.routes.rag")

    def _transform_request(self, output_type, content="# 数据结构\n\n- 栈：后进先出", toggles=None):
        metadata = {
            "interactionType": "transform",
            "requestedOutputType": output_type,
            "sourceMessageId": 1,
            "sourceMessageContent": content,
        }
        if toggles is not None:
            metadata["toolToggles"] = toggles
        return SimpleNamespace(metadata=metadata, input="transform")

    def test_file_type_detected_from_text(self):
        self.assertEqual("txt", self._rag_routes._requested_file_type_from_text("请把这段文字转成txt文件"))
        self.assertEqual("docx", self._rag_routes._requested_file_type_from_text("帮我整理成word文档"))
        self.assertEqual("md", self._rag_routes._requested_file_type_from_text("导出为markdown文件"))

    def test_export_instruction_is_stripped_from_content(self):
        content = self._rag_routes._extract_text_content_from_export_request(
            "请把以下内容转换为 txt 文件：校园二手交易应当当面验货。\n第二行内容。"
        )
        self.assertEqual("校园二手交易应当当面验货。\n第二行内容。", content)
        content = self._rag_routes._extract_text_content_from_export_request(
            "请把以下内容转换为 Word 文件\n校园二手交易应当当面验货。"
        )
        self.assertEqual("校园二手交易应当当面验货。", content)

    def test_transform_plan_routes_txt_md_docx_to_format_tools(self):
        expected = {
            "txt": "text_to_txt_tool",
            "md": "text_to_markdown_tool",
            "docx": "text_to_docx_tool",
        }
        for output_type, tool_name in expected.items():
            with self.subTest(output_type=output_type):
                plan = self._rag_routes._requested_file_transform_plan(self._transform_request(output_type))
                self.assertIsNotNone(plan)
                self.assertEqual("call_tool", plan.action)
                self.assertEqual(tool_name, plan.tool_name)
                self.assertEqual("rules", plan.route_mode)

    def test_transform_plan_respects_format_tool_toggle(self):
        plan = self._rag_routes._requested_file_transform_plan(
            self._transform_request("md", content="内容", toggles={"text_to_markdown_tool": False})
        )
        self.assertEqual("direct_answer", plan.action)
        self.assertEqual("capability_unavailable", plan.route_mode)
        self.assertNotIn("text_to_markdown_tool", plan.answer)

    def test_catalog_advertises_split_text_to_file_tools_and_respects_toggle(self):
        catalog = self._rag_routes._build_leader_callable_catalog(None)
        names = {item["name"] for item in catalog["tools"]}
        self.assertIn("text_to_markdown_tool", names)
        self.assertIn("text_to_txt_tool", names)
        self.assertIn("text_to_docx_tool", names)
        self.assertNotIn("text_to_file_tool", names)

        md_tool = next(item for item in catalog["tools"] if item["name"] == "text_to_markdown_tool")
        self.assertEqual("content_export", md_tool["category"])
        self.assertEqual(["md"], md_tool["outputs"])

        txt_tool = next(item for item in catalog["tools"] if item["name"] == "text_to_txt_tool")
        self.assertEqual(["txt"], txt_tool["outputs"])

        docx_tool = next(item for item in catalog["tools"] if item["name"] == "text_to_docx_tool")
        self.assertEqual(["docx"], docx_tool["outputs"])

        self.assertTrue(self._rag_routes._is_tool_enabled(SimpleNamespace(metadata={}), "text_to_markdown_tool"))
        disabled_request = SimpleNamespace(metadata={"toolToggles": {"text_to_markdown_tool": False}})
        self.assertFalse(self._rag_routes._is_tool_enabled(disabled_request, "text_to_markdown_tool"))

    def test_transform_plan_keeps_generated_export_tool_for_pptx_and_xlsx(self):
        for output_type, expected in (("pptx", "generated_export_tools"), ("xlsx", "generated_export_tools")):
            with self.subTest(output_type=output_type):
                plan = self._rag_routes._requested_file_transform_plan(self._transform_request(output_type))
                self.assertEqual(expected, plan.tool_name)

    def test_transform_plan_does_not_route_pdf_to_text_to_file_tool(self):
        plan = self._rag_routes._requested_file_transform_plan(self._transform_request("pdf"))
        self.assertIsNone(plan)

    def test_general_content_offers_md_txt_docx_choices(self):
        actions = self._rag_routes._file_format_follow_up_actions(
            "markdown",
            {
                "toolToggles": {
                    "text_to_markdown_tool": True,
                    "text_to_txt_tool": True,
                    "text_to_docx_tool": True,
                },
            },
            "textbook_knowledge_agent",
        )
        self.assertEqual(
            ["Markdown 文件", "纯文本文件", "Word 文件"],
            [item["label"] for item in actions],
        )
        self.assertEqual(["md", "txt", "docx"], [item["outputType"] for item in actions])

    def test_general_format_choices_respect_split_tool_toggles(self):
        actions = self._rag_routes._file_format_follow_up_actions(
            "markdown",
            {
                "toolToggles": {
                    "text_to_markdown_tool": False,
                    "text_to_txt_tool": False,
                    "text_to_docx_tool": False,
                },
            },
            "textbook_knowledge_agent",
        )
        self.assertEqual([], actions)

        actions = self._rag_routes._file_format_follow_up_actions(
            "markdown",
            {
                "toolToggles": {
                    "text_to_markdown_tool": True,
                    "text_to_txt_tool": False,
                    "text_to_docx_tool": False,
                },
            },
            "textbook_knowledge_agent",
        )
        self.assertEqual(["Markdown 文件"], [item["label"] for item in actions])


class TextToFileLeaderRouteTest(unittest.TestCase):
    def setUp(self):
        from app.multi_agents.leader_agent.agent import LeaderAgent
        self._agent = LeaderAgent()
        self._enabled = os.environ.get("AI_LEADER_FAST_ROUTE_ENABLED")
        os.environ["AI_LEADER_FAST_ROUTE_ENABLED"] = "true"

    def tearDown(self):
        if self._enabled is None:
            os.environ.pop("AI_LEADER_FAST_ROUTE_ENABLED", None)
        else:
            os.environ["AI_LEADER_FAST_ROUTE_ENABLED"] = self._enabled

    def _plan_for(self, query):
        return self._agent._plan_explicit_file_export_request(query)

    def test_txt_md_word_requests_route_to_format_tools(self):
        cases = [
            ("请把这段文字转成txt文件：校园二手交易应当当面验货", "text_to_txt_tool"),
            ("请把这段文字转成md：校园二手交易应当当面验货", "text_to_markdown_tool"),
            ("请把这段文字转成Word文件：校园二手交易应当当面验货", "text_to_docx_tool"),
            ("请把这段文字转成Markdown文件：校园二手交易应当当面验货", "text_to_markdown_tool"),
            ("请把这段话保存为纯文本文件", "text_to_txt_tool"),
        ]
        for query, tool_name in cases:
            with self.subTest(query=query):
                plan = self._plan_for(query)
                self.assertIsNotNone(plan, query)
                self.assertEqual("call_tool", plan.action, query)
                self.assertEqual(tool_name, plan.tool_name, query)
                self.assertEqual("rules", plan.route_mode, query)

    def test_admin_test_prompts_route_by_selected_format(self):
        prompts = {
            "text_to_markdown_tool": "请把以下内容按原文转成Markdown文件：校园二手交易应当当面验货、确认商品状态后再完成交易。",
            "text_to_txt_tool": "请把以下内容按原文转成纯文本文件：校园二手交易应当当面验货、确认商品状态后再完成交易。",
            "text_to_docx_tool": "请把以下内容按原文转成Word文件：校园二手交易应当当面验货、确认商品状态后再完成交易。",
        }
        for tool_name, query in prompts.items():
            with self.subTest(tool_name=tool_name):
                plan = self._plan_for(query)
                self.assertIsNotNone(plan, query)
                self.assertEqual(tool_name, plan.tool_name, query)
                self.assertEqual("call_tool", plan.action, query)

    def test_word_requests_still_route_to_generated_export_tools(self):
        plan = self._plan_for("请把这段内容整理成 word 文档：校园二手交易流程介绍")
        self.assertIsNotNone(plan)
        self.assertEqual("generated_export_tools", plan.tool_name)
        plan = self._plan_for("请把这段内容整理成 PPT：校园二手交易流程介绍")
        self.assertIsNotNone(plan)
        self.assertEqual("generated_export_tools", plan.tool_name)

    def test_ppt_and_pdf_requests_do_not_route_to_text_to_file_tool(self):
        plan = self._plan_for("请把这段文字转成PPT文件：校园二手交易应当当面验货")
        self.assertEqual("generated_export_tools", plan.tool_name)
        plan = self._plan_for("请把这份 pdf 转成 word 文档")
        self.assertIsNotNone(plan)
        self.assertEqual("generated_export_tools", plan.tool_name)


if __name__ == "__main__":
    unittest.main()
