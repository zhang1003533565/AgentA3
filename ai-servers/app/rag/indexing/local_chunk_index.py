from pathlib import Path
from typing import List, Optional
import os

from app.rag.core.types import RagDocument
from app.rag.vector_stores import DEFAULT_VECTOR_STORE_BACKEND, build_vector_store


class LocalChunkIndex:
    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        self.root_dir = root_dir
        self.index_path = index_path or root_dir / ".index" / "local_chunks.jsonl"
        self.backend = os.getenv("RAG_VECTOR_STORE_BACKEND", DEFAULT_VECTOR_STORE_BACKEND)
        self.vector_store = build_vector_store(root_dir=root_dir, backend=self.backend, index_path=self.index_path)

    def load(self) -> List[RagDocument]:
        return self.vector_store.load_documents()

    def signature(self) -> str:
        return self.vector_store.signature()
