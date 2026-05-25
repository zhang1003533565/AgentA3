from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import HybridRetriever

spec = RAG_STRATEGY_SPECS["hybrid_search"]


class HybridSearchStrategy(BaseRagStrategy):
    name = "hybrid_search"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = HybridRetriever()

    def run(self, query: RagQuery) -> RagResult:
        documents = self.retriever.search(query.keyword or query.text)
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(
                    stage="retrieve",
                    detail={"retriever": "hybrid_search", "documentCount": len(documents)},
                )
            ],
            metadata={"implemented": True},
        )


strategy = HybridSearchStrategy()
