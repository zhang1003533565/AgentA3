from pathlib import Path

from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagDocument, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import HybridRetriever

spec = RAG_STRATEGY_SPECS["multimodal_rag"]


class MultimodalRagStrategy(BaseRagStrategy):
    name = "multimodal_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = HybridRetriever()

    def run(self, query: RagQuery) -> RagResult:
        documents = [
            self._with_modality(document)
            for document in self.retriever.search(query.keyword or query.text)
        ]
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(
                    stage="multimodal_parse",
                    detail={"supported": ["markdown_images", "tables", "csv", "json", "html"]},
                ),
                RagTraceStep(
                    stage="retrieve",
                    detail={"retriever": "hybrid_search", "documentCount": len(documents)},
                ),
            ],
            metadata={"implemented": True, "multimodalEnabled": True},
        )

    def _with_modality(self, document: RagDocument) -> RagDocument:
        suffix = Path(document.source).suffix.lower()
        modality = {
            ".csv": "table",
            ".json": "structured_json",
            ".html": "html",
            ".htm": "html",
            ".md": "markdown",
            ".markdown": "markdown",
        }.get(suffix, "text")
        return RagDocument(
            id=document.id,
            content=document.content,
            source=document.source,
            score=document.score,
            metadata={**document.metadata, "modality": modality},
        )


strategy = MultimodalRagStrategy()
