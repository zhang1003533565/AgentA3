from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["crag"]
strategy = PlaceholderRagStrategy("crag", spec["category"], spec["purpose"])
