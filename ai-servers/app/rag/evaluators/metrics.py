import re
from dataclasses import dataclass, field
from typing import Any, Dict, List

from app.rag.core.types import RagDocument


@dataclass
class RagEvaluationInput:
    query: str
    documents: List[RagDocument]
    answer: str = ""
    expected_sources: List[str] = field(default_factory=list)
    expected_answer_terms: List[str] = field(default_factory=list)


@dataclass
class RagEvaluationResult:
    metrics: Dict[str, float]
    passed: bool
    detail: Dict[str, Any] = field(default_factory=dict)


class RagEvaluator:
    def evaluate(self, data: RagEvaluationInput) -> RagEvaluationResult:
        metrics = {
            "hitRate": self.hit_rate(data.documents, data.expected_sources),
            "mrr": self.mean_reciprocal_rank(data.documents, data.expected_sources),
            "contextRelevance": self.context_relevance(data.query, data.documents),
            "faithfulness": self.faithfulness(data.answer, data.documents),
            "answerTermCoverage": self.answer_term_coverage(data.answer, data.expected_answer_terms),
        }
        passed = (
            metrics["contextRelevance"] >= 0.2
            and metrics["faithfulness"] >= 0.2
            and (not data.expected_sources or metrics["hitRate"] > 0)
        )
        return RagEvaluationResult(
            metrics={key: round(value, 4) for key, value in metrics.items()},
            passed=passed,
            detail={
                "documentCount": len(data.documents),
                "expectedSources": data.expected_sources,
                "expectedAnswerTerms": data.expected_answer_terms,
            },
        )

    def hit_rate(self, documents: List[RagDocument], expected_sources: List[str]) -> float:
        if not expected_sources:
            return 1.0 if documents else 0.0
        expected = {self._normalize(source) for source in expected_sources if source}
        for document in documents:
            source = self._normalize(document.source)
            document_id = self._normalize(document.id)
            if any(item and (item in source or item in document_id) for item in expected):
                return 1.0
        return 0.0

    def mean_reciprocal_rank(self, documents: List[RagDocument], expected_sources: List[str]) -> float:
        if not expected_sources:
            return 1.0 if documents else 0.0
        expected = {self._normalize(source) for source in expected_sources if source}
        for rank, document in enumerate(documents, start=1):
            source = self._normalize(document.source)
            document_id = self._normalize(document.id)
            if any(item and (item in source or item in document_id) for item in expected):
                return 1.0 / rank
        return 0.0

    def context_relevance(self, query: str, documents: List[RagDocument]) -> float:
        terms = self._terms(query)
        if not terms or not documents:
            return 0.0
        scores = []
        for document in documents:
            normalized = self._normalize(document.content)
            matched = sum(1 for term in terms if term in normalized)
            scores.append(matched / max(len(terms), 1))
        return max(scores) if scores else 0.0

    def faithfulness(self, answer: str, documents: List[RagDocument]) -> float:
        answer_terms = self._terms(answer)
        if not answer_terms:
            return 1.0 if not answer else 0.0
        context = self._normalize("\n".join(document.content for document in documents))
        if not context:
            return 0.0
        matched = sum(1 for term in answer_terms if term in context)
        return matched / max(len(answer_terms), 1)

    def answer_term_coverage(self, answer: str, expected_terms: List[str]) -> float:
        if not expected_terms:
            return 1.0 if answer else 0.0
        normalized = self._normalize(answer)
        matched = sum(1 for term in expected_terms if self._normalize(term) in normalized)
        return matched / max(len(expected_terms), 1)

    def _terms(self, text: str) -> List[str]:
        normalized = self._normalize(text)
        words = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]{2,}", normalized)
        chinese_chars = re.findall(r"[\u4e00-\u9fff]", normalized)
        bigrams = [a + b for a, b in zip(chinese_chars, chinese_chars[1:])]
        return words + bigrams

    def _normalize(self, text: str) -> str:
        return re.sub(r"\s+", "", (text or "").lower())
