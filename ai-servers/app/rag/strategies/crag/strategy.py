from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.evaluators import RetrievalGrader
from app.rag.query_transformers.multi_query import MultiQueryTransformer
from app.rag.rerankers import LexicalReranker
from app.rag.retrievers import HybridRetriever, VectorRetriever

spec = RAG_STRATEGY_SPECS["crag"]


class CragStrategy(BaseRagStrategy):
    name = "crag"
    category = spec["category"]

    def __init__(self) -> None:
        self.hybrid_retriever = HybridRetriever()
        self.vector_retriever = VectorRetriever()
        self.transformer = MultiQueryTransformer()
        self.grader = RetrievalGrader()
        self.reranker = LexicalReranker()

    def run(self, query: RagQuery) -> RagResult:
        search_text = query.keyword or query.text
        documents = self.hybrid_retriever.search(search_text)
        grade = self.grader.grade(search_text, documents)
        trace = [
            RagTraceStep(
                stage="retrieve",
                detail={"retriever": "hybrid_search", "documentCount": len(documents)},
            ),
            RagTraceStep(
                stage="grade",
                detail={"score": grade.score, "sufficient": grade.sufficient, "reason": grade.reason},
            ),
        ]

        if not grade.sufficient:
            repaired_documents = []
            for transformed_query in self.transformer.transform(search_text):
                repaired_documents.extend(self.vector_retriever.search(transformed_query))
            documents = self.reranker.rerank(search_text, self._deduplicate(documents + repaired_documents))
            trace.append(RagTraceStep(
                stage="correct",
                detail={"action": "multi_query_repair+rerank", "documentCount": len(documents)},
            ))

        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=trace,
            metadata={"implemented": True},
        )

    def _deduplicate(self, documents):
        by_id = {}
        for document in documents:
            existing = by_id.get(document.id)
            if existing is None or (document.score or 0.0) > (existing.score or 0.0):
                by_id[document.id] = document
        return list(by_id.values())


strategy = CragStrategy()
