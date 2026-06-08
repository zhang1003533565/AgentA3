from typing import Any, List

from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagDocument, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import HybridRetriever

spec = RAG_STRATEGY_SPECS["knowledge_base_router_rag"]


class KnowledgeBaseRouterRagStrategy(BaseRagStrategy):
    name = "knowledge_base_router_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = HybridRetriever()

    def run(self, query: RagQuery) -> RagResult:
        knowledge_base_ids = self._knowledge_base_ids(query.metadata)
        raw_documents = self.retriever.search(query.keyword or query.text, top_k=20)
        routed_documents = [
            document
            for document in raw_documents
            if not knowledge_base_ids or str(document.metadata.get("knowledgeBaseId") or "default") in knowledge_base_ids
        ]
        documents = routed_documents[:5]
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(stage="route", detail={"knowledgeBaseIds": knowledge_base_ids or ["*"]}),
                RagTraceStep(stage="retrieve", detail={"retriever": "hybrid_search", "rawCount": len(raw_documents), "routedCount": len(routed_documents)}),
            ],
            metadata={"implemented": True, "knowledgeBaseIds": knowledge_base_ids},
        )

    def _knowledge_base_ids(self, metadata: dict[str, Any]) -> List[str]:
        value = (metadata or {}).get("knowledgeBaseIds") or (metadata or {}).get("knowledgeBaseId")
        if value is None:
            return []
        if isinstance(value, str):
            values = value.split(",")
        elif isinstance(value, list):
            values = value
        else:
            values = [value]
        return [str(item).strip() for item in values if str(item).strip()]


strategy = KnowledgeBaseRouterRagStrategy()
