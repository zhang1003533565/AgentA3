from datetime import datetime, timezone

from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagDocument, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import HybridRetriever

spec = RAG_STRATEGY_SPECS["time_weighted_rag"]


class TimeWeightedRagStrategy(BaseRagStrategy):
    name = "time_weighted_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = HybridRetriever()

    def run(self, query: RagQuery) -> RagResult:
        raw_documents = self.retriever.search(query.keyword or query.text, top_k=12)
        documents = [self._with_time_score(document) for document in raw_documents]
        documents.sort(key=lambda item: item.score or 0, reverse=True)
        return RagResult(
            strategy=self.name,
            documents=documents[:5],
            trace=[
                RagTraceStep(stage="retrieve", detail={"retriever": "hybrid_search", "documentCount": len(raw_documents)}),
                RagTraceStep(stage="time_weight", detail={"rule": "base_score_plus_recency_boost", "documentCount": len(documents)}),
            ],
            metadata={"implemented": True, "timeWeighted": True},
        )

    def _with_time_score(self, document: RagDocument) -> RagDocument:
        recency_boost = self._recency_boost(document.metadata or {})
        score = float(document.score or 0) + recency_boost
        return RagDocument(
            id=document.id,
            content=document.content,
            source=document.source,
            score=score,
            metadata={**document.metadata, "recencyBoost": recency_boost, "timeWeightedScore": score},
        )

    def _recency_boost(self, metadata: dict) -> float:
        value = metadata.get("updatedAt") or metadata.get("createdAt") or metadata.get("timestamp")
        if not value:
            return 0.0
        try:
            if isinstance(value, (int, float)):
                dt = datetime.fromtimestamp(float(value), tz=timezone.utc)
            else:
                dt = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
            days = max(0, (datetime.now(timezone.utc) - dt).days)
            return max(0.0, 0.08 * (1 - min(days, 365) / 365))
        except Exception:
            return 0.0


strategy = TimeWeightedRagStrategy()
