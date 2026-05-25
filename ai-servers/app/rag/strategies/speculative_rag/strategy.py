from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["speculative_rag"]
strategy = PlaceholderRagStrategy("speculative_rag", spec["category"], spec["purpose"])
