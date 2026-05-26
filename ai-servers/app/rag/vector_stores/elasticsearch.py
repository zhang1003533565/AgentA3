import os
from pathlib import Path
from typing import Iterable, List, Optional

from app.rag.core.types import RagDocument
from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class ElasticsearchVectorStore(ScaffoldedVectorStore):
    name = "elasticsearch"
    status = "implemented_optional"
    dependency = "elasticsearch"
    dependency_import = "elasticsearch"
    required_env = ["RAG_ELASTICSEARCH_URL", "RAG_ELASTICSEARCH_INDEX"]
    optional_env = ["RAG_ELASTICSEARCH_API_KEY", "RAG_ELASTICSEARCH_USERNAME", "RAG_ELASTICSEARCH_PASSWORD"]
    description = "Elasticsearch adapter scaffold for hybrid lexical/vector retrieval."

    def __init__(self, root_dir: Path, index_path: Optional[Path] = None) -> None:
        super().__init__(root_dir=root_dir, index_path=index_path)
        self.index_name = os.getenv("RAG_ELASTICSEARCH_INDEX", "")

    def load_documents(self) -> List[RagDocument]:
        self._ensure_ready()
        client = self._client()
        response = client.search(index=self.index_name, query={"match_all": {}}, size=int(os.getenv("RAG_ELASTICSEARCH_LOAD_SIZE", "1000")))
        documents: List[RagDocument] = []
        for hit in response.get("hits", {}).get("hits", []):
            source = hit.get("_source", {})
            documents.append(RagDocument(
                id=str(source.get("id") or hit.get("_id")),
                content=str(source.get("content") or ""),
                source=str(source.get("source") or ""),
                score=hit.get("_score"),
                metadata=source.get("metadata") if isinstance(source.get("metadata"), dict) else {},
            ))
        return documents

    def upsert_documents(self, documents: Iterable[RagDocument]) -> int:
        self._ensure_ready()
        client = self._client()
        count = 0
        for document in documents:
            client.index(
                index=self.index_name,
                id=document.id,
                document={
                    "id": document.id,
                    "content": document.content,
                    "source": document.source,
                    "score": document.score,
                    "metadata": document.metadata,
                },
            )
            count += 1
        return count

    def signature(self) -> str:
        return f"{self.name}:{self.index_name}"

    def _client(self):
        from elasticsearch import Elasticsearch

        api_key = os.getenv("RAG_ELASTICSEARCH_API_KEY")
        username = os.getenv("RAG_ELASTICSEARCH_USERNAME")
        password = os.getenv("RAG_ELASTICSEARCH_PASSWORD")
        if api_key:
            return Elasticsearch(os.getenv("RAG_ELASTICSEARCH_URL"), api_key=api_key)
        if username and password:
            return Elasticsearch(os.getenv("RAG_ELASTICSEARCH_URL"), basic_auth=(username, password))
        return Elasticsearch(os.getenv("RAG_ELASTICSEARCH_URL"))

    def _ensure_ready(self) -> None:
        health = self.health()
        if not health["configured"]:
            raise RuntimeError(f"Elasticsearch vector store is not configured: {health}")
