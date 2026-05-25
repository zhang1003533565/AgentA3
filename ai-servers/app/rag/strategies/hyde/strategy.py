from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.query_transformers.hyde import HydeTransformer
from app.rag.retrievers import VectorRetriever

spec = RAG_STRATEGY_SPECS["hyde"]


class HydeRagStrategy(BaseRagStrategy):
    name = "hyde"
    category = spec["category"]

    def __init__(self) -> None:
        self.transformer = HydeTransformer()
        self.retriever = VectorRetriever()

    def run(self, query: RagQuery) -> RagResult:
        hypothetical_document = self.transformer.transform(query.keyword or query.text)
        documents = self.retriever.search(hypothetical_document)
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(
                    stage="query_transform",
                    detail={
                        "transformer": "hyde",
                        "hypotheticalDocumentLength": len(hypothetical_document),
                    },
                ),
                RagTraceStep(
                    stage="retrieve",
                    detail={"retriever": "vector", "documentCount": len(documents)},
                ),
            ],
            metadata={"implemented": True},
        )


strategy = HydeRagStrategy()
