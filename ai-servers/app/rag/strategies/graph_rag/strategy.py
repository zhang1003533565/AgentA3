from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.retrievers import GraphRetriever, HybridRetriever

spec = RAG_STRATEGY_SPECS["graph_rag"]


class GraphRagStrategy(BaseRagStrategy):
    name = "graph_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.graph_retriever = GraphRetriever()
        self.fallback_retriever = HybridRetriever()

    def run(self, query: RagQuery) -> RagResult:
        search_text = query.keyword or query.text
        documents = self.graph_retriever.search_paths(search_text)
        fallback_used = False
        if not documents:
            fallback_used = True
            documents = self.fallback_retriever.search(search_text)
        return RagResult(
            strategy=self.name,
            documents=documents,
            trace=[
                RagTraceStep(
                    stage="graph_retrieve",
                    detail={"retriever": "graph_paths", "documentCount": len(documents), "fallbackUsed": fallback_used},
                )
            ],
            metadata={"implemented": True, "graphEnabled": True, "fallbackUsed": fallback_used},
        )


strategy = GraphRagStrategy()
