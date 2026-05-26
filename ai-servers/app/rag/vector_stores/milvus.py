import math
import os
from pathlib import Path
from typing import Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.embeddings import build_embedding_provider
from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class MilvusVectorStore(ScaffoldedVectorStore):
    name = "milvus"
    status = "implemented_optional"
    dependency = "pymilvus"
    dependency_import = "pymilvus"
    required_env = ["RAG_MILVUS_URI", "RAG_MILVUS_COLLECTION"]
    optional_env = ["RAG_MILVUS_TOKEN", "RAG_MILVUS_DB_NAME", "RAG_MILVUS_VECTOR_FIELD", "RAG_MILVUS_DIMENSION"]
    description = "Milvus adapter scaffold for remote vector search and scalable RAG indexing."

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path)
        self.collection_name = os.getenv("RAG_MILVUS_COLLECTION", "")
        self.dimension = int(os.getenv("RAG_MILVUS_DIMENSION", "384"))

    def load_documents(self) -> List[RagDocument]:
        self._ensure_ready()
        from pymilvus import MilvusClient

        client = self._client(MilvusClient)
        rows = client.query(collection_name=self.collection_name, filter="", output_fields=["id", "content", "source", "metadata"], limit=int(os.getenv("RAG_MILVUS_LOAD_SIZE", "1000")))
        return [
            RagDocument(
                id=str(row.get("id", "")),
                content=str(row.get("content", "")),
                source=str(row.get("source", "")),
                metadata=row.get("metadata") if isinstance(row.get("metadata"), dict) else {},
            )
            for row in rows
        ]

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        self._ensure_ready()
        from pymilvus import DataType, MilvusClient

        client = self._client(MilvusClient)
        if not client.has_collection(self.collection_name):
            schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=True)
            schema.add_field("id", DataType.VARCHAR, is_primary=True, max_length=512)
            schema.add_field("vector", DataType.FLOAT_VECTOR, dim=self.dimension)
            schema.add_field("content", DataType.VARCHAR, max_length=65535)
            schema.add_field("source", DataType.VARCHAR, max_length=2048)
            schema.add_field("metadata", DataType.JSON)
            client.create_collection(collection_name=self.collection_name, schema=schema)
        rows = []
        for document in documents:
            rows.append({
                "id": document.id,
                "vector": self._dense_vector(document.content),
                "content": document.content,
                "source": document.source,
                "metadata": document.metadata,
            })
        if rows:
            client.upsert(collection_name=self.collection_name, data=rows)
        return len(rows)

    def signature(self) -> str:
        return f"{self.name}:{self.collection_name}"

    def _client(self, cls):
        kwargs = {"uri": os.getenv("RAG_MILVUS_URI")}
        token = os.getenv("RAG_MILVUS_TOKEN")
        db_name = os.getenv("RAG_MILVUS_DB_NAME")
        if token:
            kwargs["token"] = token
        if db_name:
            kwargs["db_name"] = db_name
        return cls(**kwargs)

    def _dense_vector(self, text: str) -> List[float]:
        sparse = build_embedding_provider().embed_text(text)
        dense = [0.0] * self.dimension
        for key, value in sparse.items():
            dense[hash(key) % self.dimension] += float(value)
        norm = math.sqrt(sum(value * value for value in dense))
        if norm > 0:
            dense = [value / norm for value in dense]
        return dense

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"Milvus vector store is not configured: {health}")
