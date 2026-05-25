from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["hyde"]
strategy = PlaceholderRagStrategy("hyde", spec["category"], spec["purpose"])
