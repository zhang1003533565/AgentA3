from pathlib import Path
from typing import Optional

from app.rag.vector_stores.base import BaseVectorStore
from app.rag.vector_stores.elasticsearch import ElasticsearchVectorStore
from app.rag.vector_stores.faiss import FaissVectorStore
from app.rag.vector_stores.local_jsonl import LocalJsonlVectorStore
from app.rag.vector_stores.milvus import MilvusVectorStore
from app.rag.vector_stores.pgvector import PgVectorStore
from app.rag.defaults import VECTOR_STORE_BACKEND

DEFAULT_VECTOR_STORE_BACKEND = VECTOR_STORE_BACKEND
LOCAL_VECTOR_STORE_BACKENDS = {"", "local", "jsonl", "local_jsonl"}


def get_vector_store_backend(backend: Optional[str] = None) -> str:
    return (backend or DEFAULT_VECTOR_STORE_BACKEND).strip().lower()


def is_local_vector_store_backend(backend: Optional[str] = None) -> bool:
    return get_vector_store_backend(backend) in LOCAL_VECTOR_STORE_BACKENDS


def build_vector_store(
    root_dir: Path,
    backend: Optional[str] = None,
    index_path: Optional[Path] = None,
) -> BaseVectorStore:
    selected = get_vector_store_backend(backend)
    if selected in LOCAL_VECTOR_STORE_BACKENDS:
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
