from abc import ABC, abstractmethod

from app.rag.core.types import RagQuery, RagResult


class BaseRagStrategy(ABC):
    name: str
    category: str

    @abstractmethod
    def run(self, query: RagQuery) -> RagResult:
        raise NotImplementedError
