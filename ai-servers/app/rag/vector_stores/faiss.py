import json
import math
from pathlib import Path
from typing import Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.embeddings import build_embedding_provider
from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class FaissVectorStore(ScaffoldedVectorStore):
    name = "faiss"
    status = "disabled"
    dependency = "faiss-cpu"
    dependency_import = "faiss"
    required_config = []
    optional_config = []
    description = "Local FAISS adapter is disabled until callers pass explicit provider config."

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path)
        self.index_dir = root_dir / ".index" / "faiss"
        self.docstore_path = self.index_dir / "documents.jsonl"
        self.faiss_path = self.index_dir / "index.faiss"
        self.dimension = 384

    def load_documents(self) -> List[RagDocument]:
        if not self.docstore_path.exists():
            return []
        documents: List[RagDocument] = []
        for line in self.docstore_path.read_text(encoding="utf-8", errors="ignore").splitlines():
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except Exception:
                continue
            documents.append(RagDocument(
                id=str(row.get("id", "")),
                content=str(row.get("content", "")),
                source=str(row.get("source", "")),
                score=row.get("score"),
                metadata=row.get("metadata") if isinstance(row.get("metadata"), dict) else {},
            ))
        return documents

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        raise RuntimeError("FAISS vector store 已禁用：ai-server 不读取环境变量，请由 Java/调用方显式传入配置后再启用。")
        self._ensure_ready()
        import faiss
        import numpy as np

        materialized = list(documents)
        existing = [doc for doc in self.load_documents() if doc.source not in {item.source for item in materialized}]
        all_documents = existing + materialized
        self.index_dir.mkdir(parents=True, exist_ok=True)
        self.docstore_path.write_text(
            "\n".join(json.dumps(self._to_row(document), ensure_ascii=False) for document in all_documents) + ("\n" if all_documents else ""),
            encoding="utf-8",
        )
        vectors = np.array([self._dense_vector(document.content) for document in all_documents], dtype="float32")
        index = faiss.IndexFlatIP(self.dimension)
        if len(vectors):
            index.add(vectors)
        faiss.write_index(index, str(self.faiss_path))
        return len(materialized)

    def signature(self) -> str:
        if not self.docstore_path.exists():
            return f"{self.name}:missing"
        stat = self.docstore_path.stat()
        return f"{self.name}:{self.docstore_path}:{stat.st_mtime_ns}:{stat.st_size}"

    def health(self):
        data = super().health()
        data.update({
            "indexDir": str(self.index_dir),
            "docstorePath": str(self.docstore_path),
            "faissPath": str(self.faiss_path),
            "documentCount": len(self.load_documents()),
        })
        return data

    def _dense_vector(self, text: str) -> List[float]:
        provider = build_embedding_provider()
        sparse = provider.embed_text(text)
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
            raise RuntimeError(f"FAISS vector store is not configured: {health}")

    def _to_row(self, document: RagDocument) -> dict:
        return {
            "id": document.id,
            "content": document.content,
            "source": document.source,
            "score": document.score,
            "metadata": document.metadata,
        }
