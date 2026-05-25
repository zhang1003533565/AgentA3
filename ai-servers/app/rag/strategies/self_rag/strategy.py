from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["self_rag"]
strategy = PlaceholderRagStrategy("self_rag", spec["category"], spec["purpose"])
