from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult
from app.rag.strategies import build_strategies
from typing import Optional


class RagEngine:
    def __init__(self) -> None:
        self._strategies = build_strategies()

    def get_strategy(self, strategy_name: Optional[str] = None) -> BaseRagStrategy:
        requested = strategy_name or "naive_rag"
        if requested not in self._strategies:
            raise ValueError(f"未知 RAG 策略：{requested}")
        return self._strategies[requested]

    def run(self, query: RagQuery, strategy_name: Optional[str] = None) -> RagResult:
        strategy = self.get_strategy(strategy_name or "naive_rag")
        result = strategy.run(query)
        trace = list(result.trace)
        metadata = dict(result.metadata)

        return RagResult(
            strategy=result.strategy,
            answer=result.answer,
            documents=result.documents,
            trace=trace,
            metadata=metadata,
        )

    def list_strategies(self) -> list[str]:
        return sorted(self._strategies.keys())

    def describe_strategies(self) -> dict[str, dict[str, str]]:
        return {
            name: {
                "label": RAG_STRATEGY_SPECS[name].get("label", name),
                "category": RAG_STRATEGY_SPECS[name]["category"],
                "categoryLabel": RAG_STRATEGY_SPECS[name].get("categoryLabel", RAG_STRATEGY_SPECS[name]["category"]),
                "purpose": RAG_STRATEGY_SPECS[name]["purpose"],
                "runtime": f"app.rag.strategies.{name}.strategy",
            }
            for name in self.list_strategies()
        }


rag_engine = RagEngine()
