from typing import Iterable

from app.rag.core.types import RagDocument


class EmbeddingWriter:
    def write(self, documents: Iterable[RagDocument]) -> int:
        return sum(1 for _ in documents)
