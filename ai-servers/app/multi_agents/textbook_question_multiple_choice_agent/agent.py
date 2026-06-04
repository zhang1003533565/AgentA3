import json
import re
from dataclasses import dataclass
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


@dataclass(frozen=True)
class QuestionTypeAgent:
    name: str

    def generate_questions(self, topic: str, evidence: List[Dict[str, Any]], count: int = 5, chat_service=None) -> str:
        prompt = topic if count == 5 else f"{topic}\n\n题目数量要求：{count}"
        answer = complete_agent_or_raise(self.name, prompt, evidence, model_provider=chat_service)
        return _validate_json_answer(self.name, answer)


textbook_question_single_choice_agent = QuestionTypeAgent("textbook_question_single_choice_agent")
textbook_question_fill_blank_agent = QuestionTypeAgent("textbook_question_fill_blank_agent")
textbook_question_true_false_agent = QuestionTypeAgent("textbook_question_true_false_agent")


textbook_question_multiple_choice_agent = QuestionTypeAgent("textbook_question_multiple_choice_agent")

def _validate_json_answer(agent_name: str, text: str) -> str:
    answer = (text or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail=f"{agent_name} LLM 返回内容为空")

    json_match = re.search(r"```(?:json)?\s*([\s\S]*?)```", answer, flags=re.IGNORECASE)
    if json_match:
        answer = json_match.group(1).strip()

    try:
        parsed = json.loads(answer)
        return json.dumps(parsed, ensure_ascii=False, indent=2)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"{agent_name} 返回的内容不是合法的 JSON 格式：{str(exc)}。请确保返回严格的 JSON 格式，不要包含 Markdown 标记或其他额外文本。",
        )



__all__ = ["textbook_question_multiple_choice_agent"]
