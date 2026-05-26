from importlib import import_module

from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagQuery, RagResult, RagTraceStep
from app.rag.routers.adaptive_router import AdaptiveRagRouter

spec = RAG_STRATEGY_SPECS["adaptive_rag"]


class AdaptiveRagStrategy(BaseRagStrategy):
    name = "adaptive_rag"
    category = spec["category"]

    def __init__(self) -> None:
        self.router = AdaptiveRagRouter()

    def run(self, query: RagQuery) -> RagResult:
        routed_strategy = self.router.route(query.text)
        delegate = self._load_delegate(routed_strategy)
        result = delegate.run(query)
        return RagResult(
            strategy=self.name,
            answer=result.answer,
            documents=result.documents,
            trace=[
                RagTraceStep(stage="route", detail={"routedStrategy": routed_strategy}),
                *result.trace,
            ],
            metadata={"implemented": True, "routedStrategy": routed_strategy, "delegateMetadata": result.metadata},
        )

    def _load_delegate(self, strategy_name: str) -> BaseRagStrategy:
        if strategy_name == self.name:
            strategy_name = "naive_rag"
        try:
            module = import_module(f"app.rag.strategies.{strategy_name}.strategy")
            return getattr(module, "strategy")
        except Exception as exc:
            raise RuntimeError(f"adaptive_rag 路由到 {strategy_name}，但策略加载失败：{exc}") from exc


strategy = AdaptiveRagStrategy()
