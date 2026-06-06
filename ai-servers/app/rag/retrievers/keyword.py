import os
import re
from collections import Counter
from pathlib import Path
from typing import List, Optional

from app.rag.chunking.semantic import SemanticChunker
from app.rag.core.types import RagDocument
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.indexing.local_chunk_index import LocalChunkIndex
from app.rag.vector_stores import DEFAULT_VECTOR_STORE_BACKEND, is_local_vector_store_backend


class KeywordRetriever:
    def __init__(self, root_dir: Optional[str] = None, chunk_size: Optional[int] = None, overlap: Optional[int] = None) -> None:
        self.root_dir = self._resolve_root_dir(root_dir or "knowledge_base/raw")
        self.chunker = SemanticChunker(
            chunk_size=chunk_size or 800,
            overlap=overlap or 120,
        )
        self.loader = DocumentLoader()
        self.local_chunk_index = LocalChunkIndex(self.root_dir)
        self.vector_store_backend = os.getenv("RAG_VECTOR_STORE_BACKEND", DEFAULT_VECTOR_STORE_BACKEND).strip().lower()
        self._index_signature = ""
        self._index: List[RagDocument] = []

    def search(self, query: str, top_k: int = 5) -> List[RagDocument]:
        self._ensure_index()
        terms = self._terms(query)
        if not terms:
            return []

        scored: List[RagDocument] = []
        for document in self._index:
            score = self._score(document.content, terms)
            if score <= 0:
                continue
            scored.append(RagDocument(
                id=document.id,
                content=document.content,
                source=document.source,
                score=score,
                metadata={**document.metadata, "keywordScore": score},
            ))
        scored.sort(key=lambda item: item.score or 0, reverse=True)
        return scored[:top_k]

    def _ensure_index(self) -> None:
        signature = self._signature()
        if signature == self._index_signature:
            return

        self._index = self._load_documents()
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

    def _score(self, text: str, terms: List[str]) -> float:
        normalized = self._normalize(text)
        term_counts = Counter(terms)
        score = 0.0
        for term, weight in term_counts.items():
            if not term:
                continue
            occurrences = normalized.count(term)
            if occurrences:
                score += occurrences * (1.0 + min(len(term), 8) / 8.0) * weight
        return score

    def _terms(self, text: str) -> List[str]:
        normalized = self._normalize(text)
        words = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]{2,}", normalized)
        chinese_chars = re.findall(r"[\u4e00-\u9fff]", normalized)
        bigrams = [a + b for a, b in zip(chinese_chars, chinese_chars[1:])]
        return words + bigrams

    def _normalize(self, text: str) -> str:
        return re.sub(r"\s+", "", (text or "").lower())

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

    def _resolve_root_dir(self, root_dir: str) -> Path:
        path = Path(root_dir)
        if path.is_absolute():
            return path
        ai_server_root = Path(__file__).resolve().parents[3]
        return ai_server_root / path
