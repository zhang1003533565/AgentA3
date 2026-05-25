from typing import List

from app.rag.core.types import RagDocument
from app.rag.retrievers.keyword import KeywordRetriever
from app.rag.retrievers.vector import VectorRetriever


class HybridRetriever:
    def __init__(self) -> None:
        self.keyword_retriever = KeywordRetriever()
        self.vector_retriever = VectorRetriever()

    def search(self, query: str, top_k: int = 5) -> List[RagDocument]:
        keyword_results = self.keyword_retriever.search(query, top_k=top_k)
        vector_results = self.vector_retriever.search(query, top_k=top_k)
        return self._deduplicate(keyword_results + vector_results)[:top_k]

    def _deduplicate(self, documents: List[RagDocument]) -> List[RagDocument]:
        seen: set[str] = set()
        deduped: List[RagDocument] = []
        for document in documents:
            if document.id in seen:
                continue
            seen.add(document.id)
            deduped.append(document)
        return deduped
