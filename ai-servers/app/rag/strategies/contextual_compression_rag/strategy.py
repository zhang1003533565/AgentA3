import re
from typing import List

from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagDocument, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import HybridRetriever

spec = RAG_STRATEGY_SPECS["contextual_compression_rag"]


class ContextualCompressionRagStrategy(BaseRagStrategy):
    name = "contextual_compression_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = HybridRetriever()

    def run(self, query: RagQuery) -> RagResult:
        raw_documents = self.retriever.search(query.keyword or query.text, top_k=8)
        documents = [self._compress(query.text, document) for document in raw_documents]
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(stage="retrieve", detail={"retriever": "hybrid_search", "documentCount": len(raw_documents)}),
                RagTraceStep(stage="compress", detail={"compressor": "query_terms_sentence_filter", "documentCount": len(documents)}),
            ],
            metadata={"implemented": True, "compression": "sentence_filter"},
        )

    def _compress(self, query: str, document: RagDocument) -> RagDocument:
        sentences = [item.strip() for item in re.split(r"(?<=[。！？.!?])\s*|\n+", document.content or "") if item.strip()]
        terms = self._terms(query)
        selected = [sentence for sentence in sentences if any(term in sentence.lower() for term in terms)]
        content = "\n".join((selected or sentences[:2])[:4]) or document.content
        return RagDocument(
            id=document.id,
            content=content,
            source=document.source,
            score=document.score,
            metadata={**document.metadata, "compressed": True, "originalLength": len(document.content or ""), "compressedLength": len(content or "")},
        )

    def _terms(self, text: str) -> List[str]:
        normalized = (text or "").lower()
        terms = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]{2,}", normalized)
        chinese_chars = re.findall(r"[\u4e00-\u9fff]", normalized)
        return terms + [left + right for left, right in zip(chinese_chars, chinese_chars[1:])]


strategy = ContextualCompressionRagStrategy()
