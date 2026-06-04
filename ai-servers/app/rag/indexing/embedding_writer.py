from pathlib import Path
from typing import Iterable, Optional

from app.rag.core.types import RagDocument
from app.rag.vector_stores import build_vector_store


class EmbeddingWriter:
    def __init__(self, index_path: Optional[str] = None, backend: Optional[str] = None) -> None:
        self.index_path = Path(index_path) if index_path else None
        self.backend = backend

    def write(self, documents: Iterable[RagDocument]) -> int:
        materialized = list(documents)
        if self.index_path is not None:
            vector_store = build_vector_store(
                root_dir=self.index_path.parents[1],
                backend=self.backend,
                index_path=self.index_path,
            )
            return vector_store.upsert_documents(materialized)
        return len(materialized)
