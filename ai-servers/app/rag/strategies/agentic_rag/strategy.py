from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["agentic_rag"]
strategy = PlaceholderRagStrategy("agentic_rag", spec["category"], spec["purpose"])
