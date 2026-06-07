import base64
import hashlib
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

from app.rag.chunking.semantic import SemanticChunker
from app.rag.chunking.parent_child import ParentChildChunker
from app.rag.core import RagDocument, RagTraceStep
from app.rag.defaults import (
    CHILD_CHUNK_OVERLAP,
    CHILD_CHUNK_SIZE,
    CHUNK_OVERLAP,
    CHUNK_SIZE,
    KNOWLEDGE_BASE_DIR,
    PARENT_CHUNK_OVERLAP,
    PARENT_CHUNK_SIZE,
)
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.indexing.embedding_writer import EmbeddingWriter
from app.rag.vector_stores import DEFAULT_VECTOR_STORE_BACKEND


@dataclass
class IngestInputDocument:
    content: str = ""
    content_base64: Optional[str] = None
    source: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class IngestedDocument:
    source: str
    stored_path: str
    modality: str
    chunk_count: int
    size: int
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class IngestionResult:
    stored_count: int
    stored_files: List[str]
    indexed_chunk_count: int
    index_path: str
    documents: List[IngestedDocument]
    trace: List[RagTraceStep]


class RagIngestionPipeline:
    def __init__(self, root_dir: Optional[str] = None) -> None:
        self.root_dir = self._resolve_root_dir(root_dir or KNOWLEDGE_BASE_DIR)
        self.ingest_dir = self.root_dir / "api_ingest"
        self.index_path = self.root_dir / ".index" / "local_chunks.jsonl"
        self.parent_index_path = self.root_dir / ".index" / "parent_child_chunks.jsonl"
        self.loader = DocumentLoader()
        self.chunker = SemanticChunker(
            chunk_size=CHUNK_SIZE,
            overlap=CHUNK_OVERLAP,
        )
        self.parent_child_chunker = ParentChildChunker(
            parent_chunk_size=PARENT_CHUNK_SIZE,
            parent_overlap=PARENT_CHUNK_OVERLAP,
            child_chunk_size=CHILD_CHUNK_SIZE,
            child_overlap=CHILD_CHUNK_OVERLAP,
        )
        self.vector_store_backend = DEFAULT_VECTOR_STORE_BACKEND
        self.writer = EmbeddingWriter(index_path=str(self.index_path), backend=self.vector_store_backend)
        self.parent_child_writer = EmbeddingWriter(index_path=str(self.parent_index_path), backend=self.vector_store_backend)

    def run(self, documents: Iterable[IngestInputDocument]) -> IngestionResult:
        self.ingest_dir.mkdir(parents=True, exist_ok=True)
        inputs = list(documents)
        trace = [
            RagTraceStep(stage="receive", detail={"documentCount": len(inputs)}),
        ]

        stored_files: List[str] = []
        ingested_documents: List[IngestedDocument] = []
        chunk_documents: List[RagDocument] = []
        parent_child_documents: List[RagDocument] = []

        for item in inputs:
            payload = self._content_bytes(item)
            target = self.ingest_dir / self._build_filename(item.source, payload)
            target.write_bytes(payload)
            stored_files.append(str(target))

            loaded_items = list(self.loader.load(str(target)))
            parsed_text = loaded_items[0].content if loaded_items else item.content
            chunks = self.chunker.split(parsed_text)
            modality = self._infer_modality(target)
            for index, chunk in enumerate(chunks):
                chunk_documents.append(RagDocument(
                    id=f"{target.resolve()}#chunk-{index}",
                    content=chunk,
                    source=str(target),
                    metadata={
                        **item.metadata,
                        "chunkIndex": index,
                        "sourceName": item.source or target.name,
                        "modality": modality,
                    },
                ))
            parent_child_documents.extend(self._build_parent_child_documents(
                target=target,
                parsed_text=parsed_text,
                source_name=item.source or target.name,
                metadata=item.metadata,
            ))

            ingested_documents.append(IngestedDocument(
                source=item.source or target.name,
                stored_path=str(target),
                modality=modality,
                chunk_count=len(chunks),
                size=len(payload),
                metadata=item.metadata,
            ))

        trace.append(RagTraceStep(stage="store", detail={"storedCount": len(stored_files)}))
        trace.append(RagTraceStep(stage="chunk", detail={"chunkCount": len(chunk_documents)}))
        indexed_count = self.writer.write(chunk_documents)
        parent_child_count = self.parent_child_writer.write(parent_child_documents)
        trace.append(RagTraceStep(
            stage="index",
            detail={
                "indexedChunkCount": indexed_count,
                "indexPath": str(self.index_path),
                "vectorStoreBackend": self.vector_store_backend,
                "parentChildIndexedChunkCount": parent_child_count,
                "parentChildIndexPath": str(self.parent_index_path),
            },
        ))

        return IngestionResult(
            stored_count=len(stored_files),
            stored_files=stored_files,
            indexed_chunk_count=indexed_count,
            index_path=str(self.index_path),
            documents=ingested_documents,
            trace=trace,
        )

    def _resolve_root_dir(self, root_dir: str) -> Path:
        path = Path(root_dir)
        if path.is_absolute():
            return path
        ai_server_root = Path(__file__).resolve().parents[3]
        return ai_server_root / path

    def _build_filename(self, source: Optional[str], content: bytes) -> str:
        digest = hashlib.sha256(content).hexdigest()[:12]
        source_name = Path(source or "document.md").name
        suffix = Path(source_name).suffix.lower() or ".md"
        if suffix not in DocumentLoader.SUPPORTED_SUFFIXES:
            suffix = ".md"
        stem = Path(source_name).stem or "document"
        safe_stem = re.sub(r"[^a-zA-Z0-9_\-\u4e00-\u9fff]+", "-", stem).strip("-") or "document"
        return f"{safe_stem}-{digest}{suffix}"

    def _content_bytes(self, item: IngestInputDocument) -> bytes:
        if item.content_base64:
            return base64.b64decode(item.content_base64, validate=True)
        return item.content.encode("utf-8")

    def _infer_modality(self, path: Path) -> str:
        suffix = path.suffix.lower()
        if suffix in {".csv", ".tsv"}:
            return "table"
        if suffix == ".json":
            return "structured_json"
        if suffix in {".html", ".htm"}:
            return "html"
        if suffix in {".md", ".markdown"}:
            return "markdown"
        if suffix == ".pdf":
            return "pdf"
        if suffix in {".png", ".jpg", ".jpeg", ".webp", ".gif"}:
            return "image"
        return "text"

    def _build_parent_child_documents(
        self,
        target: Path,
        parsed_text: str,
        source_name: str,
        metadata: Dict[str, Any],
    ) -> List[RagDocument]:
        documents: List[RagDocument] = []
        for parent_index, parent_chunk in enumerate(self.parent_child_chunker.split(parsed_text)):
            parent_id = f"{target.resolve()}#parent-{parent_index}"
            parent_document = RagDocument(
                id=parent_id,
                content=parent_chunk.parent,
                source=str(target),
                metadata={
                    **metadata,
                    "chunkRole": "parent",
                    "parentId": parent_id,
                    "parentIndex": parent_index,
                    "sourceName": source_name,
                },
            )
            documents.append(parent_document)
            for child_index, child in enumerate(parent_chunk.children):
                documents.append(RagDocument(
                    id=f"{parent_id}#child-{child_index}",
                    content=child,
                    source=str(target),
                    metadata={
                        **metadata,
                        "chunkRole": "child",
                        "parentId": parent_id,
                        "parentIndex": parent_index,
                        "childIndex": child_index,
                        "sourceName": source_name,
                    },
                ))
        return documents
