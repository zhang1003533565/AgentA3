from typing import List

from app.rag.core.types import RagDocument


class RetrievalGrader:
    def is_relevant(self, query: str, documents: List[RagDocument]) -> bool:
        return bool(query and documents)
