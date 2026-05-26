from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.generation.context_builder import ContextBuilder
from app.rag.generation.speculative import SpeculativeGenerator
from app.rag.retrievers import HybridRetriever

spec = RAG_STRATEGY_SPECS["speculative_rag"]


class SpeculativeRagStrategy(BaseRagStrategy):
    name = "speculative_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.generator = SpeculativeGenerator()
        self.retriever = HybridRetriever()
        self.context_builder = ContextBuilder()

    def run(self, query: RagQuery) -> RagResult:
        search_text = query.keyword or query.text
        draft = self.generator.draft(search_text)
        documents = self.retriever.search(search_text)
        evidence = self.context_builder.build(documents)
        revised = self.generator.revise(draft, evidence)
        return RagResult(
            strategy=self.name,
            answer=revised,
            documents=documents,
            trace=[
                RagTraceStep(stage="draft", detail={"draftLength": len(draft)}),
                RagTraceStep(stage="verify", detail={"documentCount": len(documents)}),
                RagTraceStep(stage="revise", detail={"answerLength": len(revised)}),
            ],
            metadata={"implemented": True, "speculativeEnabled": True},
        )


strategy = SpeculativeRagStrategy()
