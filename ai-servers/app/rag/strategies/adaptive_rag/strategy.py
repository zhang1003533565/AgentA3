from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["adaptive_rag"]
strategy = PlaceholderRagStrategy("adaptive_rag", spec["category"], spec["purpose"])
