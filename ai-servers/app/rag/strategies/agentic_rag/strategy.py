from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.evaluators import RetrievalGrader
from app.rag.query_transformers.multi_query import MultiQueryTransformer
from app.rag.rerankers import LexicalReranker
from app.rag.retrievers import HybridRetriever, VectorRetriever

spec = RAG_STRATEGY_SPECS["agentic_rag"]


class AgenticRagStrategy(BaseRagStrategy):
    name = "agentic_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.hybrid_retriever = HybridRetriever()
        self.vector_retriever = VectorRetriever()
        self.transformer = MultiQueryTransformer()
        self.grader = RetrievalGrader()
        self.reranker = LexicalReranker()

    def run(self, query: RagQuery) -> RagResult:
        search_text = query.keyword or query.text
        steps = ["plan", "retrieve", "grade", "repair_if_needed", "rerank"]
        documents = self.hybrid_retriever.search(search_text)
        grade = self.grader.grade(search_text, documents)
        if not grade.sufficient:
            expanded = []
            for transformed_query in self.transformer.transform(search_text):
                expanded.extend(self.vector_retriever.search(transformed_query))
            documents = self.reranker.rerank(search_text, self._deduplicate(documents + expanded))[:5]
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(stage="agent_plan", detail={"steps": steps}),
                RagTraceStep(stage="agent_execute", detail={"documentCount": len(documents), "initialGrade": grade.score}),
            ],
            metadata={"implemented": True, "agentSteps": steps},
        )

    def _deduplicate(self, documents):
        by_id = {}
        for document in documents:
            existing = by_id.get(document.id)
            if existing is None or (document.score or 0.0) > (existing.score or 0.0):
                by_id[document.id] = document
        return list(by_id.values())


strategy = AgenticRagStrategy()
