import json
import math
import os
from pathlib import Path
from typing import Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.embeddings import build_embedding_provider
from app.rag.vector_stores.base import BaseVectorStore


class MilvusVectorStore(BaseVectorStore):
    name = "milvus"

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path)
        self.uri = os.getenv("RAG_MILVUS_URI", "http://localhost:19530").strip()
        base_collection = os.getenv("RAG_MILVUS_COLLECTION", "smart_campus_knowledge").strip()
        parent_collection = os.getenv("RAG_MILVUS_PARENT_CHILD_COLLECTION", f"{base_collection}_parent_child").strip()
        self.collection_role = "parent_child" if self._is_parent_child_index(index_path) else "chunks"
        self.collection_name = parent_collection if self.collection_role == "parent_child" else base_collection
        self.dimension = int(os.getenv("RAG_MILVUS_DIMENSION", "384"))
        self.metric_type = os.getenv("RAG_MILVUS_METRIC_TYPE", "COSINE").strip().upper()
        self.embedding_provider = build_embedding_provider(os.getenv("RAG_EMBEDDING_PROVIDER"))

    def load_documents(self) -> List[RagDocument]:
        self._ensure_ready()
        from pymilvus import MilvusClient

        client = MilvusClient(uri=self.uri)
        if not client.has_collection(self.collection_name):
            return []
        rows = client.query(
            collection_name=self.collection_name,
            filter='id != ""',
            output_fields=["id", "content", "source", "metadata"],
            limit=10000,
        )
        return [self._row_to_document(row) for row in rows]

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        materialized = list(documents)
        if not materialized:
            return 0
        self._ensure_ready()
        from pymilvus import DataType, MilvusClient

        client = MilvusClient(uri=self.uri)
        if not client.has_collection(self.collection_name):
            schema = client.create_schema(auto_id=False, enable_dynamic_field=False)
            schema.add_field(field_name="id", datatype=DataType.VARCHAR, is_primary=True, max_length=512)
            schema.add_field(field_name="vector", datatype=DataType.FLOAT_VECTOR, dim=self.dimension)
            schema.add_field(field_name="content", datatype=DataType.VARCHAR, max_length=8192)
            schema.add_field(field_name="source", datatype=DataType.VARCHAR, max_length=1024)
            schema.add_field(field_name="metadata", datatype=DataType.JSON)
            index_params = client.prepare_index_params()
            index_params.add_index(
                field_name="vector",
                index_type="AUTOINDEX",
                metric_type=self.metric_type,
            )
            client.create_collection(
                collection_name=self.collection_name,
                schema=schema,
                index_params=index_params,
            )

        incoming_sources = sorted({document.source for document in materialized if document.source})
        for source in incoming_sources:
            client.delete(
                collection_name=self.collection_name,
                filter=f'source == "{self._escape_filter_string(source)}"',
            )

        rows = [self._document_to_row(document) for document in materialized]
        client.upsert(collection_name=self.collection_name, data=rows)
        client.flush(collection_name=self.collection_name)
        return len(rows)

    def search(self, query: str, top_k: int = 5) -> List[RagDocument]:
        if not query.strip():
            return []
        self._ensure_ready()
        from pymilvus import MilvusClient

        client = MilvusClient(uri=self.uri)
        if not client.has_collection(self.collection_name):
            return []
        rows = client.search(
            collection_name=self.collection_name,
            data=[self._dense_vector(query)],
            limit=max(1, int(top_k or 5)),
            output_fields=["id", "content", "source", "metadata"],
        )
        results: List[RagDocument] = []
        for hit in rows[0] if rows else []:
            entity = hit.get("entity") or {}
            results.append(RagDocument(
                id=str(entity.get("id") or hit.get("id") or ""),
                content=str(entity.get("content") or ""),
                source=str(entity.get("source") or ""),
                score=float(hit.get("distance") or 0),
                metadata={
                    **self._decode_metadata(entity.get("metadata")),
                    "indexSource": self.name,
                },
            ))
        return results

    def signature(self) -> str:
        return f"{self.name}:{self.uri}:{self.collection_name}:{self.dimension}:{self.metric_type}"

    def health(self):
        dependency_available = self._dependency_available()
        configured = bool(self.uri and self.collection_name and dependency_available)
        document_count = 0
        collection_exists = False
        error = ""
        if configured:
            try:
                from pymilvus import MilvusClient

                client = MilvusClient(uri=self.uri)
                collection_exists = client.has_collection(self.collection_name)
                if collection_exists:
                    stats = client.get_collection_stats(self.collection_name)
                    document_count = int(stats.get("row_count") or 0)
            except Exception as exc:
                configured = False
                error = str(exc)
        return {
            "backend": self.name,
            "status": "implemented",
            "configured": configured,
            "dependency": "pymilvus",
            "dependencyAvailable": dependency_available,
            "uri": self.uri,
            "collection": self.collection_name,
            "collectionRole": self.collection_role,
            "collectionExists": collection_exists,
            "dimension": self.dimension,
            "metricType": self.metric_type,
            "documentCount": document_count,
            "rootDir": str(self.root_dir),
            "indexPath": str(self.index_path) if self.index_path else "",
            "error": error,
        }

    def _dense_vector(self, text: str) -> List[float]:
        sparse = self.embedding_provider.embed_text(text)
        dense = [0.0] * self.dimension
        for key, value in sparse.items():
            dense[hash(key) % self.dimension] += float(value)
        norm = math.sqrt(sum(value * value for value in dense))
        if norm > 0:
            dense = [value / norm for value in dense]
        return dense

    def _ensure_ready(self) -> None:
        if not self._dependency_available():
            raise RuntimeError("Milvus vector store 需要安装 pymilvus：pip install pymilvus")
        if not self.uri or not self.collection_name:
            raise RuntimeError("Milvus vector store 缺少 RAG_MILVUS_URI 或 RAG_MILVUS_COLLECTION")

    def _document_to_row(self, document: RagDocument) -> dict:
        metadata = dict(document.metadata)
        metadata.pop("indexSource", None)
        return {
            "id": document.id,
            "vector": self._dense_vector(document.content),
            "content": document.content[:8192],
            "source": document.source[:1024],
            "metadata": metadata,
        }

    def _row_to_document(self, row: dict) -> RagDocument:
        return RagDocument(
            id=str(row.get("id") or ""),
            content=str(row.get("content") or ""),
            source=str(row.get("source") or ""),
            metadata={
                **self._decode_metadata(row.get("metadata")),
                "indexSource": self.name,
            },
        )

    def _decode_metadata(self, value) -> dict:
        if isinstance(value, dict):
            return value
        if isinstance(value, str) and value.strip():
            try:
                parsed = json.loads(value)
                return parsed if isinstance(parsed, dict) else {}
            except Exception:
                return {}
        return {}

    def _dependency_available(self) -> bool:
        try:
            import pymilvus  # noqa: F401
            return True
        except Exception:
            return False

    def _escape_filter_string(self, value: str) -> str:
        return value.replace("\\", "\\\\").replace('"', '\\"')

    def _is_parent_child_index(self, index_path: Optional[Path]) -> bool:
        return bool(index_path and index_path.name == "parent_child_chunks.jsonl")
