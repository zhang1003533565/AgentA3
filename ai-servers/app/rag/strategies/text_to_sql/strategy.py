from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["text_to_sql"]
strategy = PlaceholderRagStrategy("text_to_sql", spec["category"], spec["purpose"])
