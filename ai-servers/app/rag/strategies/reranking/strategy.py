from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["reranking"]
strategy = PlaceholderRagStrategy("reranking", spec["category"], spec["purpose"])
