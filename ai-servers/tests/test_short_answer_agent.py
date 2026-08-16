import json
import unittest

from fastapi import HTTPException

from app.multi_agents.textbook_question_short_answer_agent.agent import textbook_question_short_answer_agent


class FakeShortAnswerProvider:
    def __init__(self, answer):
        self.answer = answer
        self.user_prompt = ""

    def complete(self, system_prompt, user_prompt):
        self.user_prompt = user_prompt
        return self.answer


class ShortAnswerQuestionAgentTest(unittest.TestCase):
    def test_accepts_strict_short_answer_json(self):
        provider = FakeShortAnswerProvider(json.dumps({
            "questions": [
                {
                    "id": "SA1",
                    "type": "short_answer",
                    "stem": "简述栈和队列的主要区别。",
                    "score": 10,
                    "knowledgePoints": ["栈", "队列", "后进先出", "先进先出"],
                    "tags": ["数据结构"],
                    "difficulty": "easy",
                    "body": {"answerLengthHint": "80-150字"},
                    "answer": {
                        "referenceAnswer": "栈在栈顶操作并遵循后进先出；队列在队尾插入、队头删除并遵循先进先出。",
                        "answerPoints": ["说明栈的操作端和后进先出特点", "说明队列的操作端和先进先出特点"],
                    },
                    "analysis": "重点比较操作位置和数据进出顺序。",
                    "scoring": {"mode": "rubric", "rubrics": [
                        {"criterion": "说明栈的操作端和后进先出特点", "score": 5},
                        {"criterion": "说明队列的操作端和先进先出特点", "score": 5},
                    ]},
                    "sourceBasis": ["输入提供了栈和队列的定义、操作限制和特点"],
                }
            ],
            "missingInfo": [],
        }, ensure_ascii=False))

        result = textbook_question_short_answer_agent.generate_questions(
            "数据结构：栈与队列",
            [],
            chat_service=provider,
        )

        payload = json.loads(result)
        self.assertEqual(1, len(payload["questions"]))
        self.assertEqual([], payload["missingInfo"])
        self.assertEqual(10, payload["questions"][0]["score"])

    def test_allows_missing_info_without_inventing_question(self):
        provider = FakeShortAnswerProvider(json.dumps({
            "questions": [],
            "missingInfo": ["缺少可用于生成简答题的知识点或教材证据"],
        }, ensure_ascii=False))

        result = textbook_question_short_answer_agent.generate_questions(
            "简答题",
            [],
            chat_service=provider,
        )

        payload = json.loads(result)
        self.assertEqual([], payload["questions"])
        self.assertTrue(payload["missingInfo"])

    def test_rejects_markdown_wrapped_json(self):
        provider = FakeShortAnswerProvider("""```json
{"questions": [], "missingInfo": ["缺少知识点"]}
```""")

        with self.assertRaises(HTTPException):
            textbook_question_short_answer_agent.generate_questions("简答题", [], chat_service=provider)

    def test_rejects_total_score_mismatch(self):
        provider = FakeShortAnswerProvider(json.dumps({
            "questions": [
                {
                    "id": "SA1",
                    "type": "short_answer",
                    "stem": "简述栈的特点。",
                    "score": 10,
                    "knowledgePoints": ["栈"],
                    "tags": ["数据结构"],
                    "difficulty": "easy",
                    "body": {"answerLengthHint": "50字以内"},
                    "answer": {"referenceAnswer": "栈遵循后进先出原则。", "answerPoints": ["说明后进先出特点"]},
                    "analysis": "栈的核心特点是后进先出。",
                    "scoring": {"mode": "rubric", "rubrics": [{"criterion": "说明后进先出特点", "score": 5}]},
                    "sourceBasis": ["输入提供了栈的特点"],
                }
            ],
            "missingInfo": [],
        }, ensure_ascii=False))

        with self.assertRaises(HTTPException):
            textbook_question_short_answer_agent.generate_questions("栈", [], chat_service=provider)


if __name__ == "__main__":
    unittest.main()
