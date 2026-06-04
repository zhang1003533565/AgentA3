from abc import ABC, abstractmethod
from typing import Any, Dict, Iterator, List


class ChatModelProvider(ABC):
    @abstractmethod
    def extract_search_keyword(self, input_text: str) -> str:
        raise NotImplementedError

    def complete(self, system_prompt: str, user_prompt: str) -> str:
        raise NotImplementedError

    def stream_complete(self, system_prompt: str, user_prompt: str) -> Iterator[str]:
        yield self.complete(system_prompt, user_prompt)

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
