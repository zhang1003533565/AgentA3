from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["graph_rag"]
strategy = PlaceholderRagStrategy("graph_rag", spec["category"], spec["purpose"])
