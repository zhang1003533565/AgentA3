from app.rag.graph_stores.base import BaseGraphStore
from app.rag.graph_stores.local import LocalGraphStore
from app.rag.graph_stores.neo4j import Neo4jGraphStore


def build_graph_store(backend: str = "") -> BaseGraphStore:
    selected = (backend or "local_graph").lower()
    if selected in {"local", "local_graph"}:
        return LocalGraphStore()
    if selected == "neo4j":
        return Neo4jGraphStore()
    raise ValueError(f"Unsupported RAG graph store backend: {selected}")
