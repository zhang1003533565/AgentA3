from typing import Any, Dict, List

from app.services.langchain_chat_service import get_chat_service


class TextbookQuestionBankAgent:
    name = "textbook_question_bank_agent"

    def generate_questions(self, topic: str, evidence: List[Dict[str, Any]], count: int = 5, chat_service=None) -> str:
        service = chat_service or get_chat_service()
        prompt = topic if count == 5 else f"{topic}\n\n题目数量要求：{count}"
        return service.generate_specialist_answer(self.name, prompt, evidence)


textbook_question_bank_agent = TextbookQuestionBankAgent()
