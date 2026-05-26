from importlib import import_module

from app.rag.core import BaseRagStrategy, PlaceholderRagStrategy, RAG_STRATEGY_SPECS


def build_strategies() -> dict[str, BaseRagStrategy]:
    strategies: dict[str, BaseRagStrategy] = {}
    for name, spec in RAG_STRATEGY_SPECS.items():
        try:
            module = import_module(f"app.rag.strategies.{name}.strategy")
            strategy = getattr(module, "strategy")
        except Exception:
            strategy = PlaceholderRagStrategy(name, spec["category"], spec["purpose"])
        strategies[name] = strategy
    return strategies


def build_placeholder_strategies() -> dict[str, PlaceholderRagStrategy]:
    return {
        name: PlaceholderRagStrategy(name, spec["category"], spec["purpose"])
        for name, spec in RAG_STRATEGY_SPECS.items()
    }


__all__ = ["build_placeholder_strategies", "build_strategies"]
