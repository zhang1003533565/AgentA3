from typing import List

from app.rag.core.types import RagDocument


class BaseReranker:
    def rerank(self, query: str, documents: List[RagDocument]) -> List[RagDocument]:
        return documents
