from abc import ABC, abstractmethod
from typing import Any, Dict, List


class ChatModelProvider(ABC):
    @abstractmethod
    def extract_search_keyword(self, input_text: str) -> str:
        raise NotImplementedError

    def complete(self, system_prompt: str, user_prompt: str) -> str:
        raise NotImplementedError

    @abstractmethod
    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
    ) -> str:
        raise NotImplementedError
