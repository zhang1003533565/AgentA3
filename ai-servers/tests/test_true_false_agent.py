import json
import unittest

from fastapi import HTTPException

from app.multi_agents.textbook_question_true_false_agent.agent import textbook_question_true_false_agent


class FakeTrueFalseProvider:
    def __init__(self, answer):
        self.answer = answer
        self.user_prompt = ""

    def complete(self, system_prompt, user_prompt):
        self.user_prompt = user_prompt
        return self.answer


class TrueFalseQuestionAgentTest(unittest.TestCase):
    def test_generates_strict_json_without_fixed_default_count(self):
        provider = FakeTrueFalseProvider(json.dumps({
            "questions": [
                {
                    "id": "TF1",
                    "type": "true_false",
                    "stem": "列表是 Python 中的可变序列类型。",
                    "score": 2,
                    "difficulty": "easy",
                    "knowledgePoints": ["列表"],
                    "tags": ["Python"],
                    "body": {"statement": "列表是 Python 中的可变序列类型。"},
                    "answer": {"correct": True},
                    "analysis": "列表创建后可以增删改元素。",
                    "scoring": {"mode": "exact", "rubrics": []},
                    "sourceBasis": ["列表是可变序列"],
                },
                {
                    "id": "TF2",
                    "type": "true_false",
                    "stem": "字典的键可以是列表类型。",
                    "score": 2,
                    "difficulty": "easy",
                    "knowledgePoints": ["字典"],
                    "tags": ["Python"],
                    "body": {"statement": "字典的键可以是列表类型。"},
                    "answer": {"correct": False},
                    "analysis": "列表是可变类型，不能作为字典键。",
                    "scoring": {"mode": "exact", "rubrics": []},
                    "sourceBasis": ["字典键必须可哈希"],
                },
            ],
            "missingInfo": [],
        }, ensure_ascii=False))

        result = textbook_question_true_false_agent.generate_questions(
            "列表和字典的核心知识点",
            [],
            chat_service=provider,
        )

        payload = json.loads(result)
        self.assertEqual(2, len(payload["questions"]))
        self.assertNotIn("题目数量要求：5", provider.user_prompt)
        self.assertIs(payload["questions"][0]["answer"]["correct"], True)
        self.assertIs(payload["questions"][1]["answer"]["correct"], False)

    def test_rejects_non_boolean_answer(self):
        provider = FakeTrueFalseProvider(json.dumps({
            "questions": [
                {
                    "statement": "列表是可变序列。",
                    "answer": "正确",
                    "explanation": "列表可以修改。",
                }
            ]
        }, ensure_ascii=False))

        with self.assertRaises(HTTPException):
            textbook_question_true_false_agent.generate_questions("列表知识点", [], chat_service=provider)


if __name__ == "__main__":
    unittest.main()
