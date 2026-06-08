from typing import Any, Dict, List

from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagDocument, RagQuery, RagResult, RagTraceStep
from app.rag.rerankers import LexicalReranker
from app.rag.retrievers import HybridRetriever

spec = RAG_STRATEGY_SPECS["metadata_filter_rag"]


class MetadataFilterRagStrategy(BaseRagStrategy):
    name = "metadata_filter_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = HybridRetriever()
        self.reranker = LexicalReranker()

    def run(self, query: RagQuery) -> RagResult:
        raw_documents = self.retriever.search(query.keyword or query.text, top_k=20)
        filters = self._filters(query.metadata)
        filtered_documents = [document for document in raw_documents if self._matches(document, filters)]
        documents = self.reranker.rerank(query.text, filtered_documents or raw_documents)[:5]
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(stage="retrieve", detail={"retriever": "hybrid_search", "documentCount": len(raw_documents)}),
                RagTraceStep(stage="metadata_filter", detail={"filters": filters, "documentCount": len(filtered_documents)}),
                RagTraceStep(stage="rerank", detail={"reranker": "lexical", "documentCount": len(documents)}),
            ],
            metadata={"implemented": True, "filters": filters},
        )

    def _filters(self, metadata: Dict[str, Any]) -> Dict[str, List[str]]:
        metadata = metadata or {}
        return {
            "knowledgeBaseIds": self._list(metadata.get("knowledgeBaseIds") or metadata.get("knowledgeBaseId")),
            "sources": self._list(metadata.get("sources") or metadata.get("source")),
            "tags": self._list(metadata.get("tags")),
            "scenes": self._list(metadata.get("scenes") or metadata.get("scene")),
        }

    def _matches(self, document: RagDocument, filters: Dict[str, List[str]]) -> bool:
        metadata = document.metadata or {}
        if filters["knowledgeBaseIds"] and str(metadata.get("knowledgeBaseId") or "default") not in filters["knowledgeBaseIds"]:
            return False
        source_name = str(metadata.get("sourceName") or document.source or "")
        if filters["sources"] and source_name not in filters["sources"]:
            return False
        if filters["scenes"] and str(metadata.get("scene") or "") not in filters["scenes"]:
            return False
        if filters["tags"]:
            document_tags = metadata.get("tags") if isinstance(metadata.get("tags"), list) else []
            if not set(filters["tags"]) & {str(tag) for tag in document_tags}:
                return False
        return True

    def _list(self, value: Any) -> List[str]:
        if value is None:
            return []
        if isinstance(value, str):
            values = value.split(",")
        elif isinstance(value, list):
            values = value
        else:
            values = [value]
        return [str(item).strip() for item in values if str(item).strip()]


strategy = MetadataFilterRagStrategy()
