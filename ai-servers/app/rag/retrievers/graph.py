import os
import re
from pathlib import Path
from typing import List, Optional, Set

from app.rag.chunking.semantic import SemanticChunker
from app.rag.core.types import RagDocument
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.indexing.local_chunk_index import LocalChunkIndex


class GraphRetriever:
    def __init__(self, root_dir: Optional[str] = None) -> None:
        self.root_dir = self._resolve_root_dir(root_dir or os.getenv("RAG_KNOWLEDGE_BASE_DIR", "knowledge_base/raw"))
        self.loader = DocumentLoader()
        self.chunker = SemanticChunker(chunk_size=900, overlap=120)
        self.local_chunk_index = LocalChunkIndex(self.root_dir)
        self._index_signature = ""
        self._paths: List[RagDocument] = []

    def search_paths(self, query: str, top_k: int = 5) -> List[RagDocument]:
        self._ensure_index()
        query_entities = set(self._extract_entities(query))
        if not query_entities:
            return []

        scored: List[RagDocument] = []
        for path_document in self._paths:
            entities = set(path_document.metadata.get("entities", []))
            score = len(query_entities & entities) / max(len(query_entities), 1)
            if score <= 0:
                continue
            scored.append(RagDocument(
                id=path_document.id,
                content=path_document.content,
                source=path_document.source,
                score=score,
                metadata={**path_document.metadata, "graphScore": score},
            ))
        scored.sort(key=lambda item: item.score or 0.0, reverse=True)
        return scored[:top_k]

    def _ensure_index(self) -> None:
        signature = self._signature()
        if signature == self._index_signature:
            return

        self._paths = []
        for loaded in self._load_documents():
            for index, chunk in enumerate(self._chunks_for_document(loaded)):
                entities = self._extract_entities(chunk)
                if len(entities) < 2:
                    continue
                path = " -> ".join(entities[:4])
                self._paths.append(RagDocument(
                    id=f"{loaded.id}#graph-{index}",
                    content=f"图谱证据路径：{path}\n证据片段：{chunk}",
                    source=loaded.source,
                    metadata={"entities": entities[:10], "path": path, "chunkIndex": index},
                ))
        self._index_signature = signature

    def _load_documents(self) -> List[RagDocument]:
        indexed_documents = self.local_chunk_index.load()
        if indexed_documents:
            return indexed_documents

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

    def _extract_entities(self, text: str) -> List[str]:
        normalized = text or ""
        chinese_terms = re.findall(r"[\u4e00-\u9fff]{2,12}", normalized)
        english_terms = re.findall(r"[a-zA-Z][a-zA-Z0-9_]{2,}", normalized)
        return self._unique(chinese_terms + english_terms)

    def _unique(self, values: List[str]) -> List[str]:
        seen: Set[str] = set()
        result: List[str] = []
        for value in values:
            normalized = value.strip()
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            result.append(normalized)
        return result

    def _resolve_root_dir(self, root_dir: str) -> Path:
        path = Path(root_dir)
        if path.is_absolute():
            return path
        ai_server_root = Path(__file__).resolve().parents[3]
        return ai_server_root / path

    def _signature(self) -> str:
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
