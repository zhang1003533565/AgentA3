from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class PgVectorStore(ScaffoldedVectorStore):
    name = "pgvector"
    dependency = "psycopg[binary]"
    required_env = ["RAG_PGVECTOR_DSN", "RAG_PGVECTOR_TABLE"]
    optional_env = ["RAG_PGVECTOR_VECTOR_COLUMN", "RAG_PGVECTOR_SCHEMA"]
    description = "pgvector adapter scaffold for PostgreSQL-backed vector retrieval."
