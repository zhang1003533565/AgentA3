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
from app.rag.knowledge_base import (
    DEFAULT_KNOWLEDGE_BASE_ID,
    DEFAULT_KNOWLEDGE_BASE_NAME,
    KnowledgeBaseDocument,
    KnowledgeBaseSegment,
    KnowledgeBaseStore,
)
from app.rag.knowledge_base.models import utc_now_iso
from app.rag.vector_stores import DEFAULT_VECTOR_STORE_BACKEND


class RagIngestionError(Exception):
    def __init__(self, message: str, status_code: int = 400) -> None:
        super().__init__(message)
        self.status_code = status_code


@dataclass
class IngestInputDocument:
    content: str = ""
    content_base64: Optional[str] = None
    source: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class IngestedDocument:
    id: str
    knowledge_base_id: str
    knowledge_base_name: str
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
        self.store = KnowledgeBaseStore(self.root_dir)
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
        metadata_records: List[tuple[KnowledgeBaseDocument, List[KnowledgeBaseSegment]]] = []

        for item in inputs:
            knowledge_base_id = self._knowledge_base_id(item.metadata)
            knowledge_base_name = self._knowledge_base_name(item.metadata)
            self.store.upsert_knowledge_base(
                knowledge_base_id=knowledge_base_id,
                name=knowledge_base_name,
                description=str(item.metadata.get("knowledgeBaseDescription") or item.metadata.get("knowledge_base_description") or ""),
            )
            payload = self._content_bytes(item)
            target = self.ingest_dir / self._safe_path_part(knowledge_base_id) / self._build_filename(item.source, payload)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
            stored_files.append(str(target))

            loaded_items = list(self.loader.load(str(target)))
            parsed_text = loaded_items[0].content if loaded_items else item.content
            chunker = self._semantic_chunker_for_metadata(item.metadata)
            chunks = chunker.split(parsed_text)
            modality = self._infer_modality(target)
            document_id = self._document_id(knowledge_base_id, item.source or target.name, payload)
            now = utc_now_iso()
            segment_records: List[KnowledgeBaseSegment] = []
            for index, chunk in enumerate(chunks):
                segment_id = f"{document_id}:segment:{index}"
                metadata = {
                    **item.metadata,
                    "chunkIndex": index,
                    "sourceName": item.source or target.name,
                    "modality": modality,
                    "knowledgeBaseId": knowledge_base_id,
                    "knowledgeBaseName": knowledge_base_name,
                    "documentId": document_id,
                    "segmentId": segment_id,
                    "indexStructure": "text_model",
                }
                rag_document = RagDocument(
                    id=segment_id,
                    content=chunk,
                    source=str(target),
                    metadata=metadata,
                )
                chunk_documents.append(rag_document)
                segment_records.append(KnowledgeBaseSegment(
                    id=segment_id,
                    document_id=document_id,
                    knowledge_base_id=knowledge_base_id,
                    position=index,
                    content=chunk,
                    source=str(target),
                    word_count=len(chunk),
                    token_count=len(chunk),
                    created_at=now,
                    updated_at=now,
                    metadata=metadata,
                ))
            parent_child_documents.extend(self._build_parent_child_documents(
                target=target,
                parsed_text=parsed_text,
                source_name=item.source or target.name,
                document_id=document_id,
                knowledge_base_id=knowledge_base_id,
                knowledge_base_name=knowledge_base_name,
                metadata=item.metadata,
            ))

            document_record = KnowledgeBaseDocument(
                id=document_id,
                knowledge_base_id=knowledge_base_id,
                name=item.source or target.name,
                source=item.source or target.name,
                stored_path=str(target),
                modality=modality,
                segment_count=len(segment_records),
                size=len(payload),
                word_count=len(parsed_text),
                token_count=len(parsed_text),
                created_at=now,
                updated_at=now,
                metadata={
                    **item.metadata,
                    "knowledgeBaseId": knowledge_base_id,
                    "knowledgeBaseName": knowledge_base_name,
                    "vectorStoreBackend": self.vector_store_backend,
                },
            )
            metadata_records.append((document_record, segment_records))

            ingested_documents.append(IngestedDocument(
                id=document_id,
                knowledge_base_id=knowledge_base_id,
                knowledge_base_name=knowledge_base_name,
                source=item.source or target.name,
                stored_path=str(target),
                modality=modality,
                chunk_count=len(chunks),
                size=len(payload),
                metadata=document_record.metadata,
            ))

        trace.append(RagTraceStep(stage="store", detail={"storedCount": len(stored_files)}))
        trace.append(RagTraceStep(stage="chunk", detail={"chunkCount": len(chunk_documents)}))
        indexed_count = self.writer.write(chunk_documents)
        parent_child_count = self.parent_child_writer.write(parent_child_documents)
        for document_record, segment_records in metadata_records:
            document_record.metadata = {
                **document_record.metadata,
                "indexedChunkCount": document_record.segment_count,
                "parentChildIndexed": True,
                "parentChildIndexedChunkCount": parent_child_count,
            }
            self.store.replace_document(document_record, segment_records)
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
        source_name = Path(source or "document.txt").name
        suffix = Path(source_name).suffix.lower() or ".txt"
        if suffix not in DocumentLoader.SUPPORTED_SUFFIXES:
            supported = ", ".join(sorted(DocumentLoader.SUPPORTED_SUFFIXES))
            raise RagIngestionError(f"知识库暂时只支持上传 {supported} 文件")
        stem = Path(source_name).stem or "document"
        safe_stem = re.sub(r"[^a-zA-Z0-9_\-\u4e00-\u9fff]+", "-", stem).strip("-") or "document"
        return f"{safe_stem}-{digest}{suffix}"

    def _content_bytes(self, item: IngestInputDocument) -> bytes:
        if item.content_base64:
            return base64.b64decode(item.content_base64, validate=True)
        return item.content.encode("utf-8")

    def _infer_modality(self, path: Path) -> str:
        suffix = path.suffix.lower()
        if suffix == ".docx":
            return "docx"
        if suffix in {".md", ".markdown"}:
            return "markdown"
        if suffix == ".pdf":
            return "pdf"
        if suffix in {".png", ".jpg", ".jpeg", ".webp", ".gif"}:
            return "image"
        if suffix in {".csv", ".tsv"}:
            return "table"
        if suffix == ".json":
            return "structured_json"
        if suffix in {".html", ".htm"}:
            return "html"
        return "text"

    def _semantic_chunker_for_metadata(self, metadata: Dict[str, Any]) -> SemanticChunker:
        chunk_size = self._bounded_int(metadata.get("chunkSize"), CHUNK_SIZE, 200, 3000)
        overlap = self._bounded_int(metadata.get("chunkOverlap"), CHUNK_OVERLAP, 0, min(800, chunk_size - 1))
        return SemanticChunker(chunk_size=chunk_size, overlap=overlap)

    def _parent_child_chunker_for_metadata(self, metadata: Dict[str, Any]) -> ParentChildChunker:
        parent_chunk_size = self._bounded_int(metadata.get("parentChunkSize"), PARENT_CHUNK_SIZE, 600, 5000)
        parent_overlap = self._bounded_int(metadata.get("parentChunkOverlap"), PARENT_CHUNK_OVERLAP, 0, min(1200, parent_chunk_size - 1))
        child_chunk_size = self._bounded_int(metadata.get("childChunkSize"), CHILD_CHUNK_SIZE, 120, 1600)
        child_overlap = self._bounded_int(metadata.get("childChunkOverlap"), CHILD_CHUNK_OVERLAP, 0, min(500, child_chunk_size - 1))
        return ParentChildChunker(
            parent_chunk_size=parent_chunk_size,
            parent_overlap=parent_overlap,
            child_chunk_size=child_chunk_size,
            child_overlap=child_overlap,
        )

    def _bounded_int(self, value: Any, default: int, minimum: int, maximum: int) -> int:
        try:
            number = int(value)
        except (TypeError, ValueError):
            number = default
        return max(minimum, min(maximum, number))

    def _build_parent_child_documents(
        self,
        target: Path,
        parsed_text: str,
        source_name: str,
        document_id: str,
        knowledge_base_id: str,
        knowledge_base_name: str,
        metadata: Dict[str, Any],
    ) -> List[RagDocument]:
        documents: List[RagDocument] = []
        chunker = self._parent_child_chunker_for_metadata(metadata)
        for parent_index, parent_chunk in enumerate(chunker.split(parsed_text)):
            parent_id = f"{document_id}:parent:{parent_index}"
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
                    "documentId": document_id,
                    "knowledgeBaseId": knowledge_base_id,
                    "knowledgeBaseName": knowledge_base_name,
                    "indexStructure": "hierarchical_model",
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
                        "documentId": document_id,
                        "knowledgeBaseId": knowledge_base_id,
                        "knowledgeBaseName": knowledge_base_name,
                        "indexStructure": "hierarchical_model",
                    },
                ))
        return documents

    def _knowledge_base_id(self, metadata: Dict[str, Any]) -> str:
        value = metadata.get("knowledgeBaseId") or metadata.get("knowledge_base_id")
        return str(value or DEFAULT_KNOWLEDGE_BASE_ID).strip() or DEFAULT_KNOWLEDGE_BASE_ID

    def _knowledge_base_name(self, metadata: Dict[str, Any]) -> str:
        value = metadata.get("knowledgeBaseName") or metadata.get("knowledge_base_name")
        return str(value or DEFAULT_KNOWLEDGE_BASE_NAME).strip() or DEFAULT_KNOWLEDGE_BASE_NAME

    def _safe_path_part(self, value: str) -> str:
        return re.sub(r"[^a-zA-Z0-9_\-\u4e00-\u9fff]+", "-", value).strip("-") or DEFAULT_KNOWLEDGE_BASE_ID

    def _document_id(self, knowledge_base_id: str, source: str, content: bytes) -> str:
        source_name = Path(source or "document.txt").name
        digest = hashlib.sha256(content).hexdigest()[:16]
        seed = f"{knowledge_base_id}:{source_name}:{digest}"
        return hashlib.sha256(seed.encode("utf-8")).hexdigest()[:24]
