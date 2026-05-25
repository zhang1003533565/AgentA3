from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["multimodal_rag"]
strategy = PlaceholderRagStrategy("multimodal_rag", spec["category"], spec["purpose"])
