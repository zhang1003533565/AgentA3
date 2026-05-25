from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["naive_rag"]
strategy = PlaceholderRagStrategy("naive_rag", spec["category"], spec["purpose"])
