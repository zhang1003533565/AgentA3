from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS


def build_placeholder_strategies() -> dict[str, PlaceholderRagStrategy]:
    return {
        name: PlaceholderRagStrategy(name, spec["category"], spec["purpose"])
        for name, spec in RAG_STRATEGY_SPECS.items()
    }


__all__ = ["build_placeholder_strategies"]
