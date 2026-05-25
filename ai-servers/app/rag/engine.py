from app.rag.principles.naive_rag import naive_rag_strategy
from app.rag.strategies import build_placeholder_strategies


class RagEngine:
    def __init__(self) -> None:
        self._strategies = build_placeholder_strategies()
        self._strategies[naive_rag_strategy.name] = naive_rag_strategy

    def get_strategy(self, strategy_name: str | None = None):
        if strategy_name and strategy_name in self._strategies:
            return self._strategies[strategy_name]
        return naive_rag_strategy

    def list_strategies(self) -> list[str]:
        return sorted(self._strategies.keys())


rag_engine = RagEngine()
