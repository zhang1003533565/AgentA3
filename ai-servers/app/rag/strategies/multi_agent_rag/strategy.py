from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.evaluators import RetrievalGrader
from app.rag.rerankers import LexicalReranker
from app.rag.retrievers import HybridRetriever, ParentChildRetriever

spec = RAG_STRATEGY_SPECS["multi_agent_rag"]


class MultiAgentRagStrategy(BaseRagStrategy):
    name = "multi_agent_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.hybrid_retriever = HybridRetriever()
        self.parent_child_retriever = ParentChildRetriever()
        self.grader = RetrievalGrader()
        self.reranker = LexicalReranker()

    def run(self, query: RagQuery) -> RagResult:
        search_text = query.keyword or query.text
        hybrid_documents = self.hybrid_retriever.search(search_text)
        parent_documents = self.parent_child_retriever.search(search_text)
        documents = self.reranker.rerank(search_text, self._deduplicate(hybrid_documents + parent_documents))[:5]
        grade = self.grader.grade(search_text, documents)
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(stage="planner_agent", detail={"intent": query.intent}),
                RagTraceStep(stage="retriever_agent", detail={"hybridCount": len(hybrid_documents), "parentChildCount": len(parent_documents)}),
                RagTraceStep(stage="critic_agent", detail={"evidenceSufficient": grade.sufficient, "score": grade.score}),
            ],
            metadata={
                "implemented": True,
                "agents": ["planner_agent", "retriever_agent", "answer_agent", "critic_agent"],
                "evidenceSufficient": grade.sufficient,
            },
        )

    def _deduplicate(self, documents):
        by_id = {}
        for document in documents:
            existing = by_id.get(document.id)
            if existing is None or (document.score or 0.0) > (existing.score or 0.0):
                by_id[document.id] = document
        return list(by_id.values())


strategy = MultiAgentRagStrategy()
