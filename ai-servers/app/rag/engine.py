from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.generation import AnswerSynthesizer
from app.rag.strategies import build_strategies
from typing import Optional


class RagEngine:
    def __init__(self) -> None:
        self._strategies = build_strategies()
        self._answer_synthesizer = AnswerSynthesizer()

    def get_strategy(self, strategy_name: Optional[str] = None) -> BaseRagStrategy:
        if strategy_name and strategy_name in self._strategies:
            return self._strategies[strategy_name]
        return self._strategies["naive_rag"]

    def run(self, query: RagQuery, strategy_name: Optional[str] = None) -> RagResult:
        requested_strategy = strategy_name or "naive_rag"
        strategy = self.get_strategy(requested_strategy)
        result = strategy.run(query)
        trace = list(result.trace)
        metadata = dict(result.metadata)

        if requested_strategy and strategy.name != requested_strategy:
            trace.insert(0, RagTraceStep(
                stage="strategy_fallback",
                detail={"requested": requested_strategy, "active": strategy.name},
            ))
            metadata["strategyRequested"] = requested_strategy
            metadata["strategyFallback"] = strategy.name

        answer = result.answer
        if not answer:
            answer = self._answer_synthesizer.synthesize(
                query=query.text,
                documents=result.documents,
                strategy=result.strategy,
                metadata=result.metadata,
            )
            metadata["answerSynthesizer"] = "local_context_synthesizer"

        return RagResult(
            strategy=result.strategy,
            answer=answer,
            documents=result.documents,
            trace=trace,
            metadata=metadata,
        )

    def list_strategies(self) -> list[str]:
        return sorted(self._strategies.keys())

    def describe_strategies(self) -> dict[str, dict[str, str]]:
        return {
            name: {
                "category": RAG_STRATEGY_SPECS[name]["category"],
                "purpose": RAG_STRATEGY_SPECS[name]["purpose"],
                "runtime": f"app.rag.strategies.{name}.strategy",
            }
            for name in self.list_strategies()
        }


rag_engine = RagEngine()
