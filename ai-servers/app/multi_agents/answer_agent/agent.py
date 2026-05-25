from typing import Any, Dict, List

from app.services.langchain_chat_service import get_chat_service


class AnswerAgent:
    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
    ) -> str:
        return get_chat_service().answer(
            prompt=prompt,
            input_text=input_text,
            history=history,
            search_keyword=search_keyword,
            search_results=search_results,
        )


answer_agent = AnswerAgent()
