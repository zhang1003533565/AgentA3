import importlib.util
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.vector_stores.base import BaseVectorStore


class ScaffoldedVectorStore(BaseVectorStore):
    name = "scaffolded"
    status = "scaffolded"
    dependency = ""
    dependency_import = ""
    required_config: List[str] = []
    optional_config: List[str] = []
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
        dependency_available = self._dependency_available()
        disabled = self.status == "disabled"
        return {
            "backend": self.name,
            "status": self.status,
            "description": self.description,
            "dependency": self.dependency,
            "dependencyAvailable": dependency_available,
            "configured": not disabled and not self.required_config and dependency_available,
            "requiredConfig": self.required_config,
            "optionalConfig": self.optional_config,
            "missingConfig": list(self.required_config),
            "rootDir": str(self.root_dir),
            "indexPath": str(self.index_path) if self.index_path else "",
            "documentCount": 0,
        }

    def _dependency_available(self) -> bool:
        if not self.dependency_import:
            return True
        return importlib.util.find_spec(self.dependency_import) is not None
