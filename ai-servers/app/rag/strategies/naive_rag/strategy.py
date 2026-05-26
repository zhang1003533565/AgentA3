from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import VectorRetriever

spec = RAG_STRATEGY_SPECS["naive_rag"]


class NaiveRagStrategy(BaseRagStrategy):
    name = "naive_rag"
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
                    stage="retrieve",
                    detail={"retriever": "vector", "documentCount": len(documents)},
                )
            ],
            metadata={"implemented": True},
        )


strategy = NaiveRagStrategy()
