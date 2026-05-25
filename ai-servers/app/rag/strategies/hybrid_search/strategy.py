from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["hybrid_search"]
strategy = PlaceholderRagStrategy("hybrid_search", spec["category"], spec["purpose"])
