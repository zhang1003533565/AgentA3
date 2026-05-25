class AdaptiveRagRouter:
    def route(self, query: str) -> str:
        return "naive_rag" if query else "naive_rag"
