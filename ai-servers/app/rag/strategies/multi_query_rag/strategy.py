from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.query_transformers.multi_query import MultiQueryTransformer
from app.rag.retrievers import VectorRetriever

spec = RAG_STRATEGY_SPECS["multi_query_rag"]


class MultiQueryRagStrategy(BaseRagStrategy):
    name = "multi_query_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.transformer = MultiQueryTransformer()
        self.retriever = VectorRetriever()

    def run(self, query: RagQuery) -> RagResult:
        transformed_queries = self.transformer.transform(query.keyword or query.text)
        documents = []
        seen: set[str] = set()
        for transformed_query in transformed_queries:
            for document in self.retriever.search(transformed_query):
                if document.id in seen:
                    continue
                seen.add(document.id)
                documents.append(document)

        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(
                    stage="query_transform",
                    detail={"transformer": "multi_query", "queries": transformed_queries},
                ),
                RagTraceStep(
                    stage="retrieve",
                    detail={"retriever": "vector", "documentCount": len(documents)},
                ),
            ],
            metadata={"implemented": True},
        )


strategy = MultiQueryRagStrategy()
