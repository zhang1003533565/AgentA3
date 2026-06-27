from dataclasses import dataclass
from typing import Any, Dict, List

from app.multi_agents.question_bank_schema import parse_and_validate_question_bank_answer
from app.multi_agents.runtime import complete_agent_or_raise


@dataclass(frozen=True)
class QuestionTypeAgent:
    name: str

    def generate_questions(self, topic: str, evidence: List[Dict[str, Any]], count: int = 5, chat_service=None) -> str:
        prompt = topic if count == 5 else f"{topic}\n\n题目数量要求：{count}"
        answer = complete_agent_or_raise(self.name, prompt, evidence, model_provider=chat_service)
        return parse_and_validate_question_bank_answer(self.name, answer)

