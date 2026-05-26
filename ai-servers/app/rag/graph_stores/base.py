from abc import ABC, abstractmethod
from typing import Any, Dict, List

from app.rag.core.types import RagDocument


class BaseGraphStore(ABC):
    name = "base"

    @abstractmethod
    def search_paths(self, query: str, documents: List[RagDocument], top_k: int = 5) -> List[RagDocument]:
        raise NotImplementedError

    def health(self) -> Dict[str, Any]:
        return {"backend": self.name, "status": "implemented", "configured": True}
