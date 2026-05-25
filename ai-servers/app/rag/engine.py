from app.rag.principles.naive_rag import naive_rag_strategy


class RagEngine:
    def __init__(self) -> None:
        self._strategies = {
            naive_rag_strategy.name: naive_rag_strategy,
        }

    def get_strategy(self, strategy_name: str | None = None):
        if strategy_name and strategy_name in self._strategies:
            return self._strategies[strategy_name]
        return naive_rag_strategy


rag_engine = RagEngine()
