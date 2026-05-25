import math
import os
import re
from collections import Counter
from pathlib import Path
from typing import Dict, List, Tuple

from app.rag.chunking.semantic import SemanticChunker
from app.rag.core.types import RagDocument
from app.rag.indexing.document_loader import DocumentLoader


class VectorRetriever:
    def __init__(self, root_dir: str | None = None, chunk_size: int | None = None, overlap: int | None = None) -> None:
        self.root_dir = self._resolve_root_dir(root_dir or os.getenv("RAG_KNOWLEDGE_BASE_DIR", "knowledge_base/raw"))
        self.chunker = SemanticChunker(
            chunk_size=chunk_size or int(os.getenv("RAG_CHUNK_SIZE", "800")),
            overlap=overlap or int(os.getenv("RAG_CHUNK_OVERLAP", "120")),
        )
        self.loader = DocumentLoader()
        self._index_signature = ""
        self._index: List[Tuple[RagDocument, Counter[str]]] = []

    def search(self, query: str, top_k: int = 5) -> List[RagDocument]:
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
        for loaded in self.loader.load(str(self.root_dir)):
            chunks = self.chunker.split(loaded.content)
            for index, chunk in enumerate(chunks):
                document = RagDocument(
                    id=f"{loaded.id}#{index}",
                    content=chunk,
                    source=loaded.source,
                    metadata={"chunkIndex": index},
                )
                vector = self._vectorize(chunk)
                if vector:
                    self._index.append((document, vector))
        self._index_signature = signature

    def _resolve_root_dir(self, root_dir: str) -> Path:
        path = Path(root_dir)
        if path.is_absolute():
            return path
        ai_server_root = Path(__file__).resolve().parents[3]
        return ai_server_root / path

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

    def _vectorize(self, text: str) -> Counter[str]:
        tokens = self._tokenize(text)
        return Counter(tokens)

    def _tokenize(self, text: str) -> List[str]:
        normalized = (text or "").lower()
        words = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]", normalized)
        chinese_chars = [token for token in words if re.fullmatch(r"[\u4e00-\u9fff]", token)]
        bigrams = [a + b for a, b in zip(chinese_chars, chinese_chars[1:])]
        return words + bigrams

    def _cosine(self, left: Dict[str, int], right: Dict[str, int]) -> float:
        common = set(left) & set(right)
        numerator = sum(left[token] * right[token] for token in common)
        if numerator == 0:
            return 0.0
        left_norm = math.sqrt(sum(value * value for value in left.values()))
        right_norm = math.sqrt(sum(value * value for value in right.values()))
        if left_norm == 0 or right_norm == 0:
            return 0.0
        return numerator / (left_norm * right_norm)
