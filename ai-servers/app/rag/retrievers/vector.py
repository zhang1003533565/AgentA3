import math
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from app.rag.chunking.semantic import SemanticChunker
from app.rag.core.types import RagDocument
from app.rag.defaults import KNOWLEDGE_BASE_DIR
from app.rag.embeddings import EmbeddingVector, build_embedding_provider
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.indexing.local_chunk_index import LocalChunkIndex
from app.rag.vector_stores import DEFAULT_VECTOR_STORE_BACKEND, build_vector_store, is_local_vector_store_backend


class VectorRetriever:
    def __init__(
        self,
        root_dir: Optional[str] = None,
        chunk_size: Optional[int] = None,
        overlap: Optional[int] = None,
        backend: Optional[str] = None,
    ) -> None:
        self.root_dir = self._resolve_root_dir(root_dir or KNOWLEDGE_BASE_DIR)
        self.chunker = SemanticChunker(
            chunk_size=chunk_size or 800,
            overlap=overlap or 120,
        )
        self.loader = DocumentLoader()
        self.embedding_provider = build_embedding_provider()
        self.vector_store_backend = (backend or DEFAULT_VECTOR_STORE_BACKEND).strip().lower()
        self.local_chunk_index = LocalChunkIndex(self.root_dir, backend=self.vector_store_backend)
        self.vector_store = build_vector_store(root_dir=self.root_dir, backend=self.vector_store_backend)
        self._index_signature = ""
        self._index: List[Tuple[RagDocument, EmbeddingVector]] = []

    def search(self, query: str, top_k: int = 5) -> List[RagDocument]:
        if not is_local_vector_store_backend(self.vector_store_backend):
            return self.vector_store.search(query, top_k=top_k)
        self._ensure_index()
        query_vector = self._vectorize(query)
        if not query_vector:
            return []

        scored: List[RagDocument] = []
        for document, document_vector in self._index:
            score = self._cosine(query_vector, document_vector)
            if score <= 0:
                continue
            scored.append(RagDocument(
                id=document.id,
                content=document.content,
                source=document.source,
                score=score,
                metadata=dict(document.metadata),
            ))
        scored.sort(key=lambda item: item.score or 0, reverse=True)
        return scored[:top_k]

    def _ensure_index(self) -> None:
        signature = self._signature()
        if signature == self._index_signature:
            return

        self._index = []
        for document in self._load_documents():
            vector = self._vectorize(document.content)
            if vector:
                self._index.append((document, vector))
        self._index_signature = signature

    def _load_documents(self) -> List[RagDocument]:
        indexed_documents = self.local_chunk_index.load()
        if indexed_documents:
            return indexed_documents
        if not is_local_vector_store_backend(self.vector_store_backend):
            return []

        documents: List[RagDocument] = []
        for loaded in self.loader.load(str(self.root_dir)):
            chunks = self.chunker.split(loaded.content)
            for index, chunk in enumerate(chunks):
                documents.append(RagDocument(
                    id=f"{loaded.id}#{index}",
                    content=chunk,
                    source=loaded.source,
                    metadata={"chunkIndex": index},
                ))
        return documents

    def _resolve_root_dir(self, root_dir: str) -> Path:
        path = Path(root_dir)
        if path.is_absolute():
            return path
        ai_server_root = Path(__file__).resolve().parents[3]
        return ai_server_root / path

    def _signature(self) -> str:
        if not is_local_vector_store_backend(self.vector_store_backend):
            return self.vector_store.signature()
        if not self.root_dir.exists():
            return "missing"
        if self.local_chunk_index.load():
            return self.local_chunk_index.signature()
        parts: List[str] = []
        for path in sorted(self.root_dir.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in DocumentLoader.SUPPORTED_SUFFIXES:
                continue
            stat = path.stat()
            parts.append(f"{path}:{stat.st_mtime_ns}:{stat.st_size}")
        return "|".join(parts)

    def _vectorize(self, text: str) -> EmbeddingVector:
        return self.embedding_provider.embed_text(text)

    def _cosine(self, left: Dict[str, float], right: Dict[str, float]) -> float:
        common = set(left) & set(right)
        numerator = sum(left[token] * right[token] for token in common)
        if numerator == 0:
            return 0.0
        left_norm = math.sqrt(sum(value * value for value in left.values()))
        right_norm = math.sqrt(sum(value * value for value in right.values()))
        if left_norm == 0 or right_norm == 0:
            return 0.0
        return numerator / (left_norm * right_norm)
