from app.rag.core.base import BaseRagStrategy
from app.rag.core.types import RagQuery, RagResult, RagTraceStep


class PlaceholderRagStrategy(BaseRagStrategy):
    def __init__(self, name: str, category: str, purpose: str) -> None:
        self.name = name
        self.category = category
        self.purpose = purpose

    def run(self, query: RagQuery) -> RagResult:
        return RagResult(
            strategy=self.name,
            trace=[
                RagTraceStep(
                    stage="placeholder",
                    detail={
                        "category": self.category,
                        "purpose": self.purpose,
                        "query": query.text,
                    },
                )
            ],
            metadata={"implemented": False},
        )
