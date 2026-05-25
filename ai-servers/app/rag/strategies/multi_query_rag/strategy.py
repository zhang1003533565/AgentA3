from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["multi_query_rag"]
strategy = PlaceholderRagStrategy("multi_query_rag", spec["category"], spec["purpose"])
