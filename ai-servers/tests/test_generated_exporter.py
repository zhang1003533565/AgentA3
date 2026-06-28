import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from app.rag.document_conversion import generated_exporter


class GeneratedExporterTest(unittest.TestCase):
    def test_question_bank_exports_markdown_docx_and_xlsx(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            generated_exporter.EXPORT_ROOT = Path(temp_dir)
            payload = {
                "questions": [
                    {
                        "id": "SC1",
                        "type": "single_choice",
                        "stem": "栈的典型访问规则是什么？",
                        "score": 2,
                        "difficulty": "easy",
                        "knowledgePoints": ["栈"],
                        "tags": ["数据结构"],
                        "body": {
                            "options": [
                                {"key": "A", "text": "后进先出"},
                                {"key": "B", "text": "先进先出"},
                            ],
                        },
                        "answer": {"correctOption": "A"},
                        "analysis": "栈遵循后进先出。",
                        "scoring": {"mode": "exact", "rubrics": [{"criterion": "答对", "score": 2}]},
                        "sourceBasis": ["教材"],
                    },
                ],
                "missingInfo": [],
            }

            result = generated_exporter.export_generated_answer(
                json.dumps(payload, ensure_ascii=False),
                "question_bank",
                {"executedAgent": "textbook_question_single_choice_agent"},
            )

            self.assertEqual(["md", "docx", "xlsx", "zip"], [item["ext"] for item in result.attachments])
            self.assertEqual("question_bank", result.diagnostics["contentKind"])
            for attachment in result.attachments:
                path = generated_exporter.EXPORT_ROOT / attachment["name"]
                self.assertTrue(path.exists(), path)
            xlsx_name = next(item["name"] for item in result.attachments if item["ext"] == "xlsx")
            with zipfile.ZipFile(generated_exporter.EXPORT_ROOT / xlsx_name) as archive:
                self.assertIn("xl/worksheets/sheet1.xml", archive.namelist())

    def test_markdown_exports_reading_files(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            generated_exporter.EXPORT_ROOT = Path(temp_dir)
            result = generated_exporter.export_generated_answer(
                "# 栈与队列\n\n- 栈：后进先出\n- 队列：先进先出",
                "markdown",
                {"executedAgent": "textbook_knowledge_agent"},
            )

            self.assertEqual(["md", "docx", "xlsx", "zip"], [item["ext"] for item in result.attachments])
            self.assertEqual("markdown_content", result.diagnostics["contentKind"])

    def test_mermaid_exports_source_files(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            generated_exporter.EXPORT_ROOT = Path(temp_dir)
            result = generated_exporter.export_generated_answer(
                "```mermaid\nflowchart TD\n  A[开始] --> B[结束]\n```",
                "mermaid_flowchart",
                {"executedAgent": "diagram_flowchart_agent"},
            )

            self.assertEqual(["mmd", "md", "zip"], [item["ext"] for item in result.attachments])
            self.assertEqual("diagram_source", result.diagnostics["contentKind"])
            mmd_name = next(item["name"] for item in result.attachments if item["ext"] == "mmd")
            self.assertIn("flowchart TD", (generated_exporter.EXPORT_ROOT / mmd_name).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
