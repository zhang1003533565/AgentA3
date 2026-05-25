from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["semantic_chunking"]
strategy = PlaceholderRagStrategy("semantic_chunking", spec["category"], spec["purpose"])
