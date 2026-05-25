from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["parent_child"]
strategy = PlaceholderRagStrategy("parent_child", spec["category"], spec["purpose"])
