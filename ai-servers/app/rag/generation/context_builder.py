from typing import List

from app.rag.core.types import RagDocument


class ContextBuilder:
    def build(self, documents: List[RagDocument]) -> str:
        return "\n\n".join(document.content for document in documents)
