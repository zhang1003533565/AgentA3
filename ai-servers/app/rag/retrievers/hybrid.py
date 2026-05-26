from typing import Dict, List, Optional

from app.rag.core.types import RagDocument
from app.rag.retrievers.keyword import KeywordRetriever
from app.rag.retrievers.vector import VectorRetriever


class HybridRetriever:
    def __init__(self, root_dir: Optional[str] = None) -> None:
        self.keyword_retriever = KeywordRetriever(root_dir=root_dir)
        self.vector_retriever = VectorRetriever(root_dir=root_dir)

    def search(self, query: str, top_k: int = 5) -> List[RagDocument]:
        keyword_results = self.keyword_retriever.search(query, top_k=top_k)
        vector_results = self.vector_retriever.search(query, top_k=top_k)
        return self._fuse(keyword_results, vector_results)[:top_k]

    def _fuse(self, keyword_results: List[RagDocument], vector_results: List[RagDocument]) -> List[RagDocument]:
        by_id: Dict[str, RagDocument] = {}
        scores: Dict[str, float] = {}
        rank_constant = 60

        for rank, document in enumerate(keyword_results, start=1):
            by_id[document.id] = document
            scores[document.id] = scores.get(document.id, 0.0) + 1.0 / (rank_constant + rank)
            document.metadata["keywordScore"] = document.score

        for rank, document in enumerate(vector_results, start=1):
            existing = by_id.get(document.id)
            if existing is None:
                by_id[document.id] = document
                existing = document
            scores[document.id] = scores.get(document.id, 0.0) + 1.0 / (rank_constant + rank)
            existing.metadata["vectorScore"] = document.score

        fused: List[RagDocument] = []
        for document_id, document in by_id.items():
            score = scores.get(document_id, 0.0)
            fused.append(RagDocument(
                id=document.id,
                content=document.content,
                source=document.source,
                score=score,
                metadata={**document.metadata, "hybridScore": score},
            ))
        fused.sort(key=lambda item: item.score or 0, reverse=True)
        return fused
