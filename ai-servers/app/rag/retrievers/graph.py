import os
from pathlib import Path
from typing import List, Optional

from app.rag.chunking.semantic import SemanticChunker
from app.rag.core.types import RagDocument
from app.rag.graph_stores import build_graph_store
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.indexing.local_chunk_index import LocalChunkIndex
from app.rag.vector_stores import DEFAULT_VECTOR_STORE_BACKEND, is_local_vector_store_backend


class GraphRetriever:
    def __init__(self, root_dir: Optional[str] = None) -> None:
        self.root_dir = self._resolve_root_dir(root_dir or "knowledge_base/raw")
        self.loader = DocumentLoader()
        self.chunker = SemanticChunker(chunk_size=900, overlap=120)
        self.local_chunk_index = LocalChunkIndex(self.root_dir)
        self.vector_store_backend = os.getenv("RAG_VECTOR_STORE_BACKEND", DEFAULT_VECTOR_STORE_BACKEND).strip().lower()
        self.graph_store = build_graph_store()
        self._index_signature = ""
        self._documents: List[RagDocument] = []

    def search_paths(self, query: str, top_k: int = 5) -> List[RagDocument]:
        self._ensure_index()
        return self.graph_store.search_paths(query, self._documents, top_k=top_k)

    def _ensure_index(self) -> None:
        signature = self._signature()
        if signature == self._index_signature:
            return

        self._documents = []
        for loaded in self._load_documents():
            for index, chunk in enumerate(self._chunks_for_document(loaded)):
                self._documents.append(RagDocument(
                    id=f"{loaded.id}#graph-source-{index}",
                    content=chunk,
                    source=loaded.source,
                    metadata={**loaded.metadata, "chunkIndex": index},
                ))
        self._index_signature = signature

    def _load_documents(self) -> List[RagDocument]:
        indexed_documents = self.local_chunk_index.load()
        if indexed_documents:
            return indexed_documents
        if not is_local_vector_store_backend(self.vector_store_backend):
            return []

        documents: List[RagDocument] = []
        for loaded in self.loader.load(str(self.root_dir)):
            documents.append(RagDocument(
                id=loaded.id,
                content=loaded.content,
                source=loaded.source,
                metadata={},
            ))
        return documents

    def _chunks_for_document(self, document: RagDocument) -> List[str]:
        if document.metadata.get("indexSource"):
            return [document.content]
        return self.chunker.split(document.content)

    def _resolve_root_dir(self, root_dir: str) -> Path:
        path = Path(root_dir)
        if path.is_absolute():
            return path
        ai_server_root = Path(__file__).resolve().parents[3]
        return ai_server_root / path

    def _signature(self) -> str:
        if not is_local_vector_store_backend(self.vector_store_backend):
            return self.local_chunk_index.signature()
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
