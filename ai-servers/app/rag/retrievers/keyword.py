import os
import re
from collections import Counter
from pathlib import Path
from typing import List

from app.rag.chunking.semantic import SemanticChunker
from app.rag.core.types import RagDocument
from app.rag.indexing.document_loader import DocumentLoader


class KeywordRetriever:
    def __init__(self, root_dir: str | None = None, chunk_size: int | None = None, overlap: int | None = None) -> None:
        self.root_dir = self._resolve_root_dir(root_dir or os.getenv("RAG_KNOWLEDGE_BASE_DIR", "knowledge_base/raw"))
        self.chunker = SemanticChunker(
            chunk_size=chunk_size or int(os.getenv("RAG_CHUNK_SIZE", "800")),
            overlap=overlap or int(os.getenv("RAG_CHUNK_OVERLAP", "120")),
        )
        self.loader = DocumentLoader()
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

        self._index = []
        for loaded in self.loader.load(str(self.root_dir)):
            chunks = self.chunker.split(loaded.content)
            for index, chunk in enumerate(chunks):
                self._index.append(RagDocument(
                    id=f"{loaded.id}#{index}",
                    content=chunk,
                    source=loaded.source,
                    metadata={"chunkIndex": index},
                ))
        self._index_signature = signature

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
        if not self.root_dir.exists():
            return "missing"
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
