from typing import List

from app.rag.core.types import RagDocument


class GraphRetriever:
    def search_paths(self, query: str, top_k: int = 5) -> List[RagDocument]:
        return []
