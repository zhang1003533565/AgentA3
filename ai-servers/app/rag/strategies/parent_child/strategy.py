from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import ParentChildRetriever

spec = RAG_STRATEGY_SPECS["parent_child"]


class ParentChildStrategy(BaseRagStrategy):
    name = "parent_child"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = ParentChildRetriever()

    def run(self, query: RagQuery) -> RagResult:
        documents = self.retriever.search(query.keyword or query.text)
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(
                    stage="retrieve",
                    detail={"retriever": "parent_child", "documentCount": len(documents)},
                )
            ],
            metadata={"implemented": True},
        )


strategy = ParentChildStrategy()
