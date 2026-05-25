from typing import List

from app.rag.core.types import RagDocument


class KeywordRetriever:
    def search(self, query: str, top_k: int = 5) -> List[RagDocument]:
        return []
