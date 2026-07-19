import json
import unittest

from app.multi_agents.textbook_knowledge_agent.agent import (
    MODEL_GENERATED_NOTICE,
    TextbookKnowledgeAgent,
    resolve_knowledge_source_mode,
)


class RecordingProvider:
    def __init__(self, answer="# Python 知识材料\n\n- 核心知识点") -> None:
        self.answer = answer
        self.calls = []

    def complete(self, system_prompt, user_prompt):
        self.calls.append({
            "systemPrompt": system_prompt,
            "userPrompt": json.loads(user_prompt),
        })
        return self.answer


class TextbookKnowledgeAgentTest(unittest.TestCase):
    def setUp(self) -> None:
        self.agent = TextbookKnowledgeAgent()

    def test_explicit_self_generation_without_material_creates_labeled_draft(self):
        provider = RecordingProvider(f"{MODEL_GENERATED_NOTICE}\n\n# Python 知识材料\n\n- 核心知识点")

        answer = self.agent.summarize_knowledge_points(
            "我没有上传材料，请根据 Python 发展历史自行生成知识材料",
            [],
            chat_service=provider,
        )

        self.assertEqual(1, len(provider.calls))
        request_payload = json.loads(provider.calls[0]["userPrompt"]["user_input"])
        self.assertEqual("model_generated", request_payload["knowledgeSourceMode"])
        self.assertIn("明确授权无材料生成", request_payload["sourcePolicy"])
        self.assertTrue(answer.startswith(MODEL_GENERATED_NOTICE))

    def test_missing_material_without_generation_choice_asks_for_source(self):
        provider = RecordingProvider("你希望上传材料或选择已有材料，还是授权我根据 Python 发展历史自行生成知识材料？")

        answer = self.agent.summarize_knowledge_points(
            "Python 发展历史知识点",
            [],
            chat_service=provider,
        )

        self.assertEqual(1, len(provider.calls))
        request_payload = json.loads(provider.calls[0]["userPrompt"]["user_input"])
        self.assertEqual("source_selection_required", request_payload["knowledgeSourceMode"])
        self.assertIn("上传材料", answer)
        self.assertIn("自行生成知识材料", answer)

    def test_unlabeled_model_generated_content_is_rejected_instead_of_locally_patched(self):
        provider = RecordingProvider("# Python 知识材料\n\n- 核心知识点")

        with self.assertRaisesRegex(RuntimeError, "未按约定标注"):
            self.agent.summarize_knowledge_points(
                "没有材料，请根据 Python 自己生成知识材料",
                [],
                chat_service=provider,
            )

    def test_provided_material_stays_grounded_and_has_no_model_notice(self):
        provider = RecordingProvider()
        evidence = [{
            "id": "ev-1",
            "source": "uploaded_document",
            "content": "Python 由 Guido van Rossum 设计。",
        }]

        answer = self.agent.summarize_knowledge_points(
            "整理这份 Python 材料",
            evidence,
            chat_service=provider,
        )

        request_payload = json.loads(provider.calls[0]["userPrompt"]["user_input"])
        self.assertEqual("provided_material", request_payload["knowledgeSourceMode"])
        self.assertNotIn(MODEL_GENERATED_NOTICE, answer)

    def test_profile_context_is_not_mistaken_for_uploaded_material(self):
        mode = resolve_knowledge_source_mode(
            "请帮我生成 Python 知识点",
            [{"id": "user_profile_snapshot", "source": "user_profile", "content": "偏好图解"}],
        )

        self.assertEqual("model_generated", mode)


if __name__ == "__main__":
    unittest.main()
