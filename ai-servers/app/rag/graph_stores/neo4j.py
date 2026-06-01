import importlib.util
from typing import Any, Dict, List

from app.rag.core.types import RagDocument
from app.rag.graph_stores.base import BaseGraphStore


class Neo4jGraphStore(BaseGraphStore):
    name = "neo4j"

    def search_paths(self, query: str, documents: List[RagDocument], top_k: int = 5) -> List[RagDocument]:
        raise RuntimeError("Neo4j graph store 已禁用：ai-server 不读取环境变量，请由 Java/调用方显式传入配置后再启用。")

    def health(self) -> Dict[str, Any]:
        dependency_available = importlib.util.find_spec("neo4j") is not None
        return {
            "backend": self.name,
            "status": "disabled",
            "dependency": "neo4j",
            "dependencyAvailable": dependency_available,
            "configured": False,
            "missingEnv": [],
            "optionalEnv": [],
        }

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"Neo4j graph store is not configured: {health}")
