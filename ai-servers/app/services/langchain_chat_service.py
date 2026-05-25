from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.model_providers.deepseek import DeepSeekProvider


class LangChainChatService:
    """
    Compatibility facade used by existing nodes/tests.
    Runtime provider implementations now live under app/model_providers.
    """

    def __init__(self, provider: ChatModelProvider) -> None:
        self.provider = provider

    def extract_search_keyword(self, input_text: str) -> str:
        return self.provider.extract_search_keyword(input_text)

    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
    ) -> str:
        return self.provider.answer(
            prompt=prompt,
            input_text=input_text,
            history=history,
            search_keyword=search_keyword,
            search_results=search_results,
        )


chat_service: Optional[LangChainChatService] = None


def get_chat_service() -> LangChainChatService:
    global chat_service
    if chat_service is not None:
        return chat_service
    try:
        chat_service = LangChainChatService(provider=DeepSeekProvider())
        return chat_service
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
