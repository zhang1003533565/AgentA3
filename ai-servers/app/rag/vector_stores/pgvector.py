import json
import os
from pathlib import Path
from typing import Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class PgVectorStore(ScaffoldedVectorStore):
    name = "pgvector"
    status = "implemented_optional"
    dependency = "psycopg[binary]"
    dependency_import = "psycopg"
    required_env = ["RAG_PGVECTOR_DSN", "RAG_PGVECTOR_TABLE"]
    optional_env = ["RAG_PGVECTOR_VECTOR_COLUMN", "RAG_PGVECTOR_SCHEMA"]
    description = "pgvector adapter scaffold for PostgreSQL-backed vector retrieval."

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path)
        self.table = os.getenv("RAG_PGVECTOR_TABLE", "rag_documents")

    def load_documents(self) -> List[RagDocument]:
        self._ensure_ready()
        import psycopg

        with psycopg.connect(os.getenv("RAG_PGVECTOR_DSN")) as conn:
            with conn.cursor() as cursor:
                cursor.execute(f"SELECT id, content, source, metadata FROM {self.table} LIMIT %s", (int(os.getenv("RAG_PGVECTOR_LOAD_SIZE", "1000")),))
                rows = cursor.fetchall()
        return [
            RagDocument(
                id=str(row[0]),
                content=str(row[1]),
                source=str(row[2] or ""),
                metadata=row[3] if isinstance(row[3], dict) else {},
            )
            for row in rows
        ]

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        self._ensure_ready()
        import psycopg

        materialized = list(documents)
        with psycopg.connect(os.getenv("RAG_PGVECTOR_DSN")) as conn:
            with conn.cursor() as cursor:
                cursor.execute(
                    f"CREATE TABLE IF NOT EXISTS {self.table} "
                    "(id TEXT PRIMARY KEY, content TEXT NOT NULL, source TEXT, metadata JSONB)"
                )
                for document in materialized:
                    cursor.execute(
                        f"INSERT INTO {self.table} (id, content, source, metadata) VALUES (%s, %s, %s, %s::jsonb) "
                        "ON CONFLICT (id) DO UPDATE SET content=EXCLUDED.content, source=EXCLUDED.source, metadata=EXCLUDED.metadata",
                        (document.id, document.content, document.source, json.dumps(document.metadata, ensure_ascii=False)),
                    )
            conn.commit()
        return len(materialized)

    def signature(self) -> str:
        return f"{self.name}:{self.table}"

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"pgvector store is not configured: {health}")
