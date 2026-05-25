import re
from dataclasses import dataclass
from typing import List

from app.rag.core.types import RagDocument


@dataclass
class RetrievalGrade:
    score: float
    sufficient: bool
    reason: str


class RetrievalGrader:
    def grade(self, query: str, documents: List[RagDocument]) -> RetrievalGrade:
        if not query:
            return RetrievalGrade(score=0.0, sufficient=False, reason="empty_query")
        if not documents:
            return RetrievalGrade(score=0.0, sufficient=False, reason="no_documents")

        terms = self._terms(query)
        if not terms:
            return RetrievalGrade(score=0.0, sufficient=False, reason="no_query_terms")

        best_score = 0.0
        for document in documents:
            score = self._coverage_score(terms, document.content) + min(document.score or 0.0, 1.0)
            best_score = max(best_score, score)

        sufficient = best_score >= 0.35
        return RetrievalGrade(
            score=round(best_score, 4),
            sufficient=sufficient,
            reason="sufficient" if sufficient else "low_relevance",
        )

    def is_relevant(self, query: str, documents: List[RagDocument]) -> bool:
        return self.grade(query, documents).sufficient

    def _coverage_score(self, terms: List[str], text: str) -> float:
        normalized = re.sub(r"\s+", "", (text or "").lower())
        if not normalized:
            return 0.0
        matched = sum(1 for term in terms if term in normalized)
        return matched / max(len(terms), 1)

    def _terms(self, text: str) -> List[str]:
        normalized = re.sub(r"\s+", "", (text or "").lower())
        words = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]{2,}", normalized)
        chinese_chars = re.findall(r"[\u4e00-\u9fff]", normalized)
        bigrams = [a + b for a, b in zip(chinese_chars, chinese_chars[1:])]
        return words + bigrams
