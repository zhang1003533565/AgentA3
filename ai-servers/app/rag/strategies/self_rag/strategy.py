from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.evaluators import RetrievalGrader
from app.rag.query_transformers.multi_query import MultiQueryTransformer
from app.rag.rerankers import LexicalReranker
from app.rag.retrievers import HybridRetriever, VectorRetriever

spec = RAG_STRATEGY_SPECS["self_rag"]


class SelfRagStrategy(BaseRagStrategy):
    name = "self_rag"
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
        initial_grade = self.grader.grade(search_text, documents)
        trace = [
            RagTraceStep(stage="self_reflect", detail={"needRetrieval": bool(search_text)}),
            RagTraceStep(
                stage="grade_evidence",
                detail={"score": initial_grade.score, "sufficient": initial_grade.sufficient, "reason": initial_grade.reason},
            ),
        ]

        final_grade = initial_grade
        if not initial_grade.sufficient:
            repaired = []
            for transformed_query in self.transformer.transform(search_text):
                repaired.extend(self.vector_retriever.search(transformed_query))
            documents = self.reranker.rerank(search_text, self._deduplicate(documents + repaired))[:5]
            final_grade = self.grader.grade(search_text, documents)
            trace.append(RagTraceStep(
                stage="self_repair",
                detail={"action": "multi_query_repair+rerank", "score": final_grade.score},
            ))

        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=trace,
            metadata={
                "implemented": True,
                "selfReflection": {
                    "initialSufficient": initial_grade.sufficient,
                    "finalSufficient": final_grade.sufficient,
                },
            },
        )

    def _deduplicate(self, documents):
        by_id = {}
        for document in documents:
            existing = by_id.get(document.id)
            if existing is None or (document.score or 0.0) > (existing.score or 0.0):
                by_id[document.id] = document
        return list(by_id.values())


strategy = SelfRagStrategy()
