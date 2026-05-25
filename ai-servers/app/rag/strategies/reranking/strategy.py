from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.rerankers import LexicalReranker
from app.rag.retrievers import HybridRetriever

spec = RAG_STRATEGY_SPECS["reranking"]


class RerankingStrategy(BaseRagStrategy):
    name = "reranking"
    category = spec["category"]

    def __init__(self) -> None:
        self.retriever = HybridRetriever()
        self.reranker = LexicalReranker()

    def run(self, query: RagQuery) -> RagResult:
        raw_documents = self.retriever.search(query.keyword or query.text)
        documents = self.reranker.rerank(query.text, raw_documents)
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(
                    stage="retrieve",
                    detail={"retriever": "hybrid_search", "documentCount": len(raw_documents)},
                ),
                RagTraceStep(
                    stage="rerank",
                    detail={"reranker": "lexical", "documentCount": len(documents)},
                ),
            ],
            metadata={"implemented": True},
        )


strategy = RerankingStrategy()
