import re
from typing import List

from app.rag.core.types import RagDocument


class BaseReranker:
    def rerank(self, query: str, documents: List[RagDocument]) -> List[RagDocument]:
        return documents


class LexicalReranker(BaseReranker):
    def rerank(self, query: str, documents: List[RagDocument]) -> List[RagDocument]:
        terms = self._terms(query)
        if not terms:
            return documents

        reranked: List[RagDocument] = []
        for document in documents:
            lexical_score = self._lexical_score(document.content, terms)
            original_score = document.score or 0.0
            final_score = original_score + lexical_score
            reranked.append(RagDocument(
                id=document.id,
                content=document.content,
                source=document.source,
                score=final_score,
                metadata={
                    **document.metadata,
                    "originalScore": original_score,
                    "rerankScore": lexical_score,
                    "finalScore": final_score,
                },
            ))
        reranked.sort(key=lambda item: item.score or 0, reverse=True)
        return reranked

    def _lexical_score(self, text: str, terms: List[str]) -> float:
        normalized = self._normalize(text)
        score = 0.0
        for term in terms:
            if term and term in normalized:
                score += 1.0 + min(len(term), 10) / 10.0
        return score / max(len(terms), 1)

    def _terms(self, text: str) -> List[str]:
        normalized = self._normalize(text)
        words = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]{2,}", normalized)
        chinese_chars = re.findall(r"[\u4e00-\u9fff]", normalized)
        bigrams = [a + b for a, b in zip(chinese_chars, chinese_chars[1:])]
        return words + bigrams

    def _normalize(self, text: str) -> str:
        return re.sub(r"\s+", "", (text or "").lower())
