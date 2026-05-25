from app.rag.core import PlaceholderRagStrategy, RAG_STRATEGY_SPECS

spec = RAG_STRATEGY_SPECS["multi_agent_rag"]
strategy = PlaceholderRagStrategy("multi_agent_rag", spec["category"], spec["purpose"])
