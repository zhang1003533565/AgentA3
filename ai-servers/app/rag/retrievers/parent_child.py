import math
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from app.rag.chunking.parent_child import ParentChildChunker
from app.rag.core.types import RagDocument
from app.rag.embeddings import EmbeddingVector, build_embedding_provider
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.indexing.local_chunk_index import LocalChunkIndex


class ParentChildRetriever:
    def __init__(self, root_dir: Optional[str] = None) -> None:
        self.root_dir = self._resolve_root_dir(root_dir or "knowledge_base/raw")
        self.loader = DocumentLoader()
        self.chunker = ParentChildChunker(
            parent_chunk_size=int(__import__("os").getenv("RAG_PARENT_CHUNK_SIZE", "1600")),
            parent_overlap=int(__import__("os").getenv("RAG_PARENT_CHUNK_OVERLAP", "160")),
            child_chunk_size=int(__import__("os").getenv("RAG_CHILD_CHUNK_SIZE", "420")),
            child_overlap=int(__import__("os").getenv("RAG_CHILD_CHUNK_OVERLAP", "80")),
        )
        self.local_parent_child_index = LocalChunkIndex(self.root_dir, self.root_dir / ".index" / "parent_child_chunks.jsonl")
        self.embedding_provider = build_embedding_provider()
        self._index_signature = ""
        self._children: List[Tuple[RagDocument, EmbeddingVector]] = []
        self._parents: Dict[str, RagDocument] = {}

    def search(self, query: str, top_k: int = 5) -> List[RagDocument]:
        self._ensure_index()
        query_vector = self._vectorize(query)
        if not query_vector:
            return []

        parent_hits: Dict[str, RagDocument] = {}
        parent_scores: Dict[str, float] = {}
        child_hits: Dict[str, RagDocument] = {}

        for child, child_vector in self._children:
            score = self._cosine(query_vector, child_vector)
            if score <= 0:
                continue
            parent_id = str(child.metadata.get("parentId", ""))
            if score > parent_scores.get(parent_id, 0.0):
                parent_scores[parent_id] = score
                child_hits[parent_id] = child

        for parent_id, score in parent_scores.items():
            parent = self._parents.get(parent_id)
            child = child_hits.get(parent_id)
            if not parent or not child:
                continue
            parent_hits[parent_id] = RagDocument(
                id=parent.id,
                content=parent.content,
                source=parent.source,
                score=score,
                metadata={
                    **parent.metadata,
                    "parentChildScore": score,
                    "matchedChildId": child.id,
                    "matchedChildContent": child.content,
                },
            )

        results = list(parent_hits.values())
        results.sort(key=lambda item: item.score or 0, reverse=True)
        return results[:top_k]

    def _ensure_index(self) -> None:
        signature = self._signature()
        if signature == self._index_signature:
            return

        self._children = []
        self._parents = {}
        indexed_documents = self.local_parent_child_index.load()
        if indexed_documents:
            for document in indexed_documents:
                role = document.metadata.get("chunkRole")
                parent_id = str(document.metadata.get("parentId") or document.id)
                if role == "parent":
                    self._parents[parent_id] = document
                elif role == "child":
                    vector = self._vectorize(document.content)
                    if vector:
                        self._children.append((document, vector))
            self._index_signature = signature
            return
        for loaded in self.loader.load(str(self.root_dir)):
            parent_chunks = self.chunker.split(loaded.content)
            for parent_index, parent_chunk in enumerate(parent_chunks):
                parent_id = f"{loaded.id}#parent-{parent_index}"
                parent_document = RagDocument(
                    id=parent_id,
                    content=parent_chunk.parent,
                    source=loaded.source,
                    metadata={"parentIndex": parent_index},
                )
                self._parents[parent_id] = parent_document
                for child_index, child in enumerate(parent_chunk.children):
                    child_document = RagDocument(
                        id=f"{parent_id}#child-{child_index}",
                        content=child,
                        source=loaded.source,
                        metadata={
                            "parentId": parent_id,
                            "parentIndex": parent_index,
                            "childIndex": child_index,
                        },
                    )
                    vector = self._vectorize(child)
                    if vector:
                        self._children.append((child_document, vector))
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
        if self.local_parent_child_index.load():
            return self.local_parent_child_index.signature()
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
