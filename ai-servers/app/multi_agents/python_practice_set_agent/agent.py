import json
from typing import Any, Dict, List, Set

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


REQUIRED_QUESTION_TYPES = frozenset(
    {"single_choice", "multiple_choice", "true_false", "fill_blank", "code_output"}
)


class PythonPracticeSetAgent:
    name = "python_practice_set_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        answer = complete_agent_or_raise(
            self.name,
            input_text,
            evidence or [],
            model_provider=chat_service,
        )
        question_types = _question_types(answer)
        missing = sorted(REQUIRED_QUESTION_TYPES - question_types)
        if missing:
            raise HTTPException(
                status_code=502,
                detail=f"python_practice_set_agent 缺少必需题型：{', '.join(missing)}",
            )
        return answer


def _question_types(answer: str) -> Set[str]:
    text = (answer or "").strip()
    if text.startswith("```"):
        lines = text.splitlines()
        lines = lines[1:] if lines and lines[0].startswith("```") else lines
        lines = lines[:-1] if lines and lines[-1].strip() == "```" else lines
        text = "\n".join(lines).strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail="python_practice_set_agent 必须返回合法 JSON",
        ) from exc
    questions = payload.get("questions") if isinstance(payload, dict) else None
    if not isinstance(questions, list):
        raise HTTPException(
            status_code=502,
            detail="python_practice_set_agent 必须返回 questions 列表",
        )
    return {
        str(question.get("type") or question.get("questionType") or "").strip().lower().replace("-", "_")
        for question in questions
        if isinstance(question, dict)
    }


python_practice_set_agent = PythonPracticeSetAgent()

__all__ = ["REQUIRED_QUESTION_TYPES", "python_practice_set_agent"]
