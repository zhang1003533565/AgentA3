import math
from pathlib import Path
from typing import Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.embeddings import build_embedding_provider
from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class MilvusVectorStore(ScaffoldedVectorStore):
    name = "milvus"
    status = "disabled"
    dependency = "pymilvus"
    dependency_import = "pymilvus"
    required_env = []
    optional_env = []
    description = "Milvus adapter is disabled until callers pass explicit provider config."

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path)
        self.collection_name = ""
        self.dimension = 384

    def load_documents(self) -> List[RagDocument]:
        return []

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        raise RuntimeError("Milvus vector store 已禁用：ai-server 不读取环境变量，请由 Java/调用方显式传入配置后再启用。")

    def signature(self) -> str:
        return f"{self.name}:{self.collection_name}"

    def _dense_vector(self, text: str) -> List[float]:
        sparse = build_embedding_provider().embed_text(text)
        dense = [0.0] * self.dimension
        for key, value in sparse.items():
            dense[hash(key) % self.dimension] += float(value)
        norm = math.sqrt(sum(value * value for value in dense))
        if norm > 0:
            dense = [value / norm for value in dense]
        return dense

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"Milvus vector store is not configured: {health}")
