from pathlib import Path
from typing import Optional

from app.rag.vector_stores.base import BaseVectorStore
from app.rag.vector_stores.elasticsearch import ElasticsearchVectorStore
from app.rag.vector_stores.faiss import FaissVectorStore
from app.rag.vector_stores.local_jsonl import LocalJsonlVectorStore
from app.rag.vector_stores.milvus import MilvusVectorStore
from app.rag.vector_stores.pgvector import PgVectorStore


def build_vector_store(
    root_dir: Path,
    backend: Optional[str] = None,
    index_path: Optional[Path] = None,
) -> BaseVectorStore:
    selected = (backend or "local_jsonl").lower()
    if selected in {"local", "jsonl", "local_jsonl"}:
        return LocalJsonlVectorStore(root_dir=root_dir, index_path=index_path)
    if selected == "faiss":
        return FaissVectorStore(root_dir=root_dir, index_path=index_path)
    if selected == "milvus":
        return MilvusVectorStore(root_dir=root_dir, index_path=index_path)
    if selected in {"elasticsearch", "elastic", "es"}:
        return ElasticsearchVectorStore(root_dir=root_dir, index_path=index_path)
    if selected in {"pgvector", "postgres", "postgresql"}:
        return PgVectorStore(root_dir=root_dir, index_path=index_path)
    raise ValueError(f"Unsupported RAG vector store backend: {selected}")
