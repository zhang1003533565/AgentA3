from typing import Any, Dict, List


class TextbookQuestionBankAgent:
    name = "textbook_question_bank_agent"

    def generate_questions(self, topic: str, evidence: List[Dict[str, Any]], count: int = 5) -> str:
        points = self._points(topic, evidence)
        lines = ["## 教材题库"]
        for index, point in enumerate(points[:count], start=1):
            lines.append(f"{index}. 【简答题】请说明：{point}")
            lines.append(f"   参考答案要点：围绕“{point}”给出定义、关键特征和应用场景。")
        return "\n".join(lines)

    def _points(self, topic: str, evidence: List[Dict[str, Any]]) -> List[str]:
        points = []
        for item in evidence or []:
            content = str(item.get("content") or item.get("name") or "").strip()
            if content:
                points.append(content.replace("\n", " ")[:60])
        return points or [topic or "本章节核心知识点"]


textbook_question_bank_agent = TextbookQuestionBankAgent()
