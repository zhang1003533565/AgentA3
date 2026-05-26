from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

from app.rag.core.types import RagDocument


class BaseVectorStore(ABC):
    name = "base"

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        self.root_dir = root_dir
        self.index_path = index_path

    @abstractmethod
    def load_documents(self) -> List[RagDocument]:
        raise NotImplementedError

    @abstractmethod
    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        raise NotImplementedError

    @abstractmethod
    def signature(self) -> str:
        raise NotImplementedError

    def health(self) -> Dict[str, Any]:
        return {
            "backend": self.name,
            "rootDir": str(self.root_dir),
            "indexPath": str(self.index_path) if self.index_path else "",
            "documentCount": len(self.load_documents()),
        }
