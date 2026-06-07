import json
from pathlib import Path
from typing import Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class PgVectorStore(ScaffoldedVectorStore):
    name = "pgvector"
    status = "disabled"
    dependency = "psycopg[binary]"
    dependency_import = "psycopg"
    required_config = []
    optional_config = []
    description = "pgvector adapter is disabled until callers pass explicit provider config."

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path)
        self.table = "rag_documents"

    def load_documents(self) -> List[RagDocument]:
        return []

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        raise RuntimeError("pgvector store 已禁用：ai-server 不读取环境变量，请由 Java/调用方显式传入配置后再启用。")

    def signature(self) -> str:
        return f"{self.name}:{self.table}"

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"pgvector store is not configured: {health}")
