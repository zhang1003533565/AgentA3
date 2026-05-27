from dataclasses import dataclass
from typing import Any, Dict, List

from app.services.langchain_chat_service import get_chat_service


@dataclass(frozen=True)
class QuestionTypeAgent:
    name: str

    def generate_questions(self, topic: str, evidence: List[Dict[str, Any]], count: int = 5, chat_service=None) -> str:
        service = chat_service or get_chat_service()
        prompt = topic if count == 5 else f"{topic}\n\n题目数量要求：{count}"
        return service.generate_specialist_answer(self.name, prompt, evidence)


textbook_question_single_choice_agent = QuestionTypeAgent("textbook_question_single_choice_agent")
textbook_question_fill_blank_agent = QuestionTypeAgent("textbook_question_fill_blank_agent")
textbook_question_true_false_agent = QuestionTypeAgent("textbook_question_true_false_agent")
textbook_question_multiple_choice_agent = QuestionTypeAgent("textbook_question_multiple_choice_agent")
textbook_question_short_answer_agent = QuestionTypeAgent("textbook_question_short_answer_agent")
textbook_question_calculation_agent = QuestionTypeAgent("textbook_question_calculation_agent")
textbook_question_programming_agent = QuestionTypeAgent("textbook_question_programming_agent")

QUESTION_TYPE_AGENTS = {
    agent.name: agent
    for agent in (
        textbook_question_single_choice_agent,
        textbook_question_fill_blank_agent,
        textbook_question_true_false_agent,
        textbook_question_multiple_choice_agent,
        textbook_question_short_answer_agent,
        textbook_question_calculation_agent,
        textbook_question_programming_agent,
    )
}
