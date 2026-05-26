import importlib.util
import os
from typing import Any, Dict, List

from app.rag.core.types import RagDocument
from app.rag.graph_stores.base import BaseGraphStore


class Neo4jGraphStore(BaseGraphStore):
    name = "neo4j"

    def search_paths(self, query: str, documents: List[RagDocument], top_k: int = 5) -> List[RagDocument]:
        self._ensure_ready()
        from neo4j import GraphDatabase

        uri = os.getenv("RAG_NEO4J_URI")
        user = os.getenv("RAG_NEO4J_USERNAME")
        password = os.getenv("RAG_NEO4J_PASSWORD")
        database = os.getenv("RAG_NEO4J_DATABASE") or None
        cypher = os.getenv(
            "RAG_NEO4J_SEARCH_CYPHER",
            "MATCH p=(n)-[*1..2]-(m) WHERE toLower(n.name) CONTAINS toLower($query) "
            "RETURN p LIMIT $limit",
        )
        driver = GraphDatabase.driver(uri, auth=(user, password))
        try:
            with driver.session(database=database) as session:
                rows = session.run(cypher, query=query, limit=top_k)
                results: List[RagDocument] = []
                for index, row in enumerate(rows):
                    content = str(row.data())
                    results.append(RagDocument(
                        id=f"neo4j:path:{index}",
                        content=content,
                        source="neo4j",
                        score=1.0 / (index + 1),
                        metadata={"graphBackend": self.name, "cypher": cypher},
                    ))
                return results
        finally:
            driver.close()

    def health(self) -> Dict[str, Any]:
        missing = [key for key in ("RAG_NEO4J_URI", "RAG_NEO4J_USERNAME", "RAG_NEO4J_PASSWORD") if not os.getenv(key)]
        dependency_available = importlib.util.find_spec("neo4j") is not None
        return {
            "backend": self.name,
            "status": "implemented_optional",
            "dependency": "neo4j",
            "dependencyAvailable": dependency_available,
            "configured": not missing and dependency_available,
            "missingEnv": missing,
            "optionalEnv": ["RAG_NEO4J_DATABASE", "RAG_NEO4J_SEARCH_CYPHER"],
        }

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"Neo4j graph store is not configured: {health}")
