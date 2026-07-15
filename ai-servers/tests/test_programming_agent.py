import json
import unittest

from fastapi import HTTPException

from app.multi_agents.textbook_question_programming_agent.agent import textbook_question_programming_agent


class FakeProgrammingProvider:
    def __init__(self, answer):
        self.answer = answer
        self.user_prompt = ""

    def complete(self, system_prompt, user_prompt):
        self.user_prompt = user_prompt
        return self.answer


class ProgrammingQuestionAgentTest(unittest.TestCase):
    def test_accepts_strict_programming_json(self):
        provider = FakeProgrammingProvider(json.dumps({
            "questions": [
                {
                    "id": "P1",
                    "type": "programming",
                    "stem": "实现顺序栈的入栈操作",
                    "score": 10,
                    "knowledgePoints": ["顺序栈", "入栈"],
                    "difficulty": "easy",
                    "tags": ["Python", "栈"],
                    "body": {
                        "title": "实现顺序栈的入栈操作",
                        "description": "根据输入的栈容量和元素序列，模拟顺序栈入栈。",
                        "language": "Python",
                        "inputFormat": "第一行包含栈容量，第二行包含待入栈整数序列。",
                        "outputFormat": "输出最终栈内元素。",
                        "constraints": ["容量为正整数"],
                        "examples": [{
                            "input": "3\n1 2",
                            "output": "1 2",
                            "explanation": "两个元素依次入栈。",
                        }],
                    },
                    "answer": {
                        "solutionOutline": ["读取容量和元素", "依次入栈", "输出栈内元素"],
                        "referenceSolution": "capacity=int(input())\nitems=list(map(int,input().split()))\nprint(*items[:capacity])",
                        "testCases": [{
                            "input": "3\n1 2",
                            "expectedOutput": "1 2",
                            "hidden": False,
                        }],
                    },
                    "analysis": "按容量依次入栈并输出。",
                    "scoring": {
                        "mode": "program",
                        "rubrics": [{"criterion": "正确完成入栈", "score": 10}],
                    },
                    "sourceBasis": ["输入要求生成顺序栈入栈题"],
                }
            ],
            "missingInfo": [],
        }, ensure_ascii=False))

        result = textbook_question_programming_agent.generate_questions(
            "请根据顺序栈入栈生成 Python 编程题",
            [],
            chat_service=provider,
        )

        payload = json.loads(result)
        self.assertEqual(1, len(payload["questions"]))
        self.assertEqual([], payload["missingInfo"])
        self.assertEqual("Python", payload["questions"][0]["body"]["language"])

    def test_allows_missing_info_without_inventing_question(self):
        provider = FakeProgrammingProvider(json.dumps({
            "questions": [],
            "missingInfo": ["缺少可用于生成编程题的知识点或教材证据"],
        }, ensure_ascii=False))

        result = textbook_question_programming_agent.generate_questions(
            "生成编程题",
            [],
            chat_service=provider,
        )

        payload = json.loads(result)
        self.assertEqual([], payload["questions"])
        self.assertTrue(payload["missingInfo"])

    def test_rejects_unspecified_language_with_reference_solution(self):
        provider = FakeProgrammingProvider(json.dumps({
            "questions": [
                {
                    "id": "P1",
                    "title": "测试题",
                    "knowledgePoints": ["数组"],
                    "difficulty": "easy",
                    "language": "未指定",
                    "description": "测试题描述",
                    "inputFormat": "未明确",
                    "outputFormat": "未明确",
                    "constraints": ["未明确"],
                    "examples": [],
                    "testCases": [],
                    "solutionOutline": ["读取输入"],
                    "referenceSolution": "print('hello')",
                    "sourceBasis": ["用户要求"],
                }
            ],
            "missingInfo": [],
        }, ensure_ascii=False))

        with self.assertRaises(HTTPException):
            textbook_question_programming_agent.generate_questions("数组", [], chat_service=provider)

    def test_rejects_markdown_wrapped_json(self):
        provider = FakeProgrammingProvider("""```json
{"questions": [], "missingInfo": ["缺少知识点"]}
```""")

        with self.assertRaises(HTTPException):
            textbook_question_programming_agent.generate_questions("生成编程题", [], chat_service=provider)


if __name__ == "__main__":
    unittest.main()
