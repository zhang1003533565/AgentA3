import json
import re
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


class TrueFalseQuestionAgent:
    name = "textbook_question_true_false_agent"

    def generate_questions(self, topic: str, evidence: List[Dict[str, Any]], count: int | None = None, chat_service=None) -> str:
        prompt = topic if not count else f"{topic}\n\n题目数量要求：{count}"
        answer = complete_agent_or_raise(self.name, prompt, evidence, model_provider=chat_service)
        return _validate_json_answer(answer)


textbook_question_true_false_agent = TrueFalseQuestionAgent()


def _validate_json_answer(text: str) -> str:
    answer = (text or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail="textbook_question_true_false_agent LLM 返回内容为空")

    json_match = re.search(r"```(?:json)?\s*([\s\S]*?)```", answer, flags=re.IGNORECASE)
    if json_match:
        answer = json_match.group(1).strip()

    try:
        parsed = json.loads(answer)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"textbook_question_true_false_agent 返回的内容不是合法的 JSON 格式：{str(exc)}。请确保返回严格 JSON，不要包含 Markdown 标记或其他额外文本。",
        )
    _validate_true_false_schema(parsed)
    return json.dumps(parsed, ensure_ascii=False, indent=2)


def _validate_true_false_schema(parsed: Any) -> None:
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail="判断题输出必须是 JSON 对象")
    questions = parsed.get("questions")
    if not isinstance(questions, list) or not questions:
        raise HTTPException(status_code=502, detail="判断题输出必须包含非空 questions 数组")
    for index, question in enumerate(questions, start=1):
        if not isinstance(question, dict):
            raise HTTPException(status_code=502, detail=f"第 {index} 道判断题必须是 JSON 对象")
        statement = question.get("statement")
        answer = question.get("answer")
        explanation = question.get("explanation")
        if not isinstance(statement, str) or not statement.strip():
            raise HTTPException(status_code=502, detail=f"第 {index} 道判断题缺少 statement 字符串")
        if not isinstance(answer, bool):
            raise HTTPException(status_code=502, detail=f"第 {index} 道判断题 answer 必须是布尔值 true 或 false")
        if not isinstance(explanation, str) or not explanation.strip():
            raise HTTPException(status_code=502, detail=f"第 {index} 道判断题缺少 explanation 字符串")


__all__ = ["textbook_question_true_false_agent"]
