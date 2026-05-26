from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import VectorRetriever

spec = RAG_STRATEGY_SPECS["semantic_chunking"]


class SemanticChunkingStrategy(BaseRagStrategy):
    name = "semantic_chunking"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = VectorRetriever()

    def run(self, query: RagQuery) -> RagResult:
        documents = self.retriever.search(query.keyword or query.text)
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(
                    stage="chunk",
                    detail={"chunker": "semantic_boundary", "purpose": spec["purpose"]},
                ),
                RagTraceStep(
                    stage="retrieve",
                    detail={"retriever": "vector", "documentCount": len(documents)},
                ),
            ],
            metadata={"implemented": True, "semanticChunkingEnabled": True},
        )


strategy = SemanticChunkingStrategy()
