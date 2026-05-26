from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS
from app.rag.strategies import build_strategies
from typing import Optional


class RagEngine:
    def __init__(self) -> None:
        self._strategies = build_strategies()

    def get_strategy(self, strategy_name: Optional[str] = None) -> BaseRagStrategy:
        if strategy_name and strategy_name in self._strategies:
            return self._strategies[strategy_name]
        return self._strategies["naive_rag"]

    def list_strategies(self) -> list[str]:
        return sorted(self._strategies.keys())

    def describe_strategies(self) -> dict[str, dict[str, str]]:
        return {
            name: {
                "category": RAG_STRATEGY_SPECS[name]["category"],
                "purpose": RAG_STRATEGY_SPECS[name]["purpose"],
            }
            for name in self.list_strategies()
        }


rag_engine = RagEngine()
