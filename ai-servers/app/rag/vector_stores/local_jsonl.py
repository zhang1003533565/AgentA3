import json
from pathlib import Path
from typing import Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.vector_stores.base import BaseVectorStore


class LocalJsonlVectorStore(BaseVectorStore):
    name = "local_jsonl"

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path or root_dir / ".index" / "local_chunks.jsonl")

    def load_documents(self) -> List[RagDocument]:
        if self.index_path is None or not self.index_path.exists():
            return []

        documents: List[RagDocument] = []
        for line_no, line in enumerate(self.index_path.read_text(encoding="utf-8", errors="ignore").splitlines()):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except Exception:
                continue
            document = self._to_document(row, line_no)
            if document is not None:
                documents.append(document)
        return documents

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        materialized = list(documents)
        if self.index_path is None:
            return len(materialized)

        self.index_path.parent.mkdir(parents=True, exist_ok=True)
        incoming_sources = {document.source for document in materialized}
        existing = []
        if self.index_path.exists():
            for line in self.index_path.read_text(encoding="utf-8", errors="ignore").splitlines():
                if not line.strip():
                    continue
                try:
                    row = json.loads(line)
                except Exception:
                    continue
                if row.get("source") not in incoming_sources:
                    existing.append(row)

        rows = existing + [self._to_row(document) for document in materialized]
        self.index_path.write_text(
            "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + ("\n" if rows else ""),
            encoding="utf-8",
        )
        return len(materialized)

    def signature(self) -> str:
        if self.index_path is None or not self.index_path.exists():
            return f"{self.name}:missing"
        stat = self.index_path.stat()
        return f"{self.name}:{self.index_path}:{stat.st_mtime_ns}:{stat.st_size}"

    def _to_document(self, row: dict, line_no: int) -> Optional[RagDocument]:
        content = str(row.get("content") or "")
        if not content:
            return None
        metadata = row.get("metadata") if isinstance(row.get("metadata"), dict) else {}
        source = str(row.get("source") or "")
        return RagDocument(
            id=str(row.get("id") or f"{source or self.index_path}#line-{line_no}"),
            content=content,
            source=source,
            score=row.get("score"),
            metadata={**metadata, "indexSource": self.name},
        )

    def _to_row(self, document: RagDocument) -> dict:
        metadata = dict(document.metadata)
        metadata.pop("indexSource", None)
        return {
            "id": document.id,
            "content": document.content,
            "source": document.source,
            "score": document.score,
            "metadata": metadata,
        }
