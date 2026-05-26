import os
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.vector_stores.base import BaseVectorStore


class ScaffoldedVectorStore(BaseVectorStore):
    name = "scaffolded"
    status = "scaffolded"
    dependency = ""
    required_env: List[str] = []
    optional_env: List[str] = []
    description = "Vector store adapter scaffold. Runtime integration is not implemented yet."

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path)

    def load_documents(self) -> List[RagDocument]:
        return []

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        count = sum(1 for _ in documents)
        raise RuntimeError(
            f"{self.name} vector store is scaffolded only; refused to upsert {count} documents. "
            "Use local_jsonl or finish this adapter before enabling it."
        )

    def signature(self) -> str:
        return f"{self.name}:{self.status}"

    def health(self) -> Dict[str, Any]:
        missing_env = [key for key in self.required_env if not os.getenv(key)]
        return {
            "backend": self.name,
            "status": self.status,
            "description": self.description,
            "dependency": self.dependency,
            "configured": not missing_env,
            "requiredEnv": self.required_env,
            "optionalEnv": self.optional_env,
            "missingEnv": missing_env,
            "rootDir": str(self.root_dir),
            "indexPath": str(self.index_path) if self.index_path else "",
            "documentCount": 0,
        }
