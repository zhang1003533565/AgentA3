import os
from typing import Any, Dict, List

from app.rag.core.types import RagDocument
from app.rag.query_transformers.hyde import HydeTransformer
from app.rag.query_transformers.multi_query import MultiQueryTransformer
from app.rag.rerankers import LexicalReranker
from app.rag.retrievers import HybridRetriever, VectorRetriever, java_backend_retriever


class RetrieverAgent:
    def __init__(self) -> None:
        self.vector_retriever = VectorRetriever()
        self.hybrid_retriever = HybridRetriever()
        self.multi_query_transformer = MultiQueryTransformer()
        self.hyde_transformer = HydeTransformer()
        self.reranker = LexicalReranker()
        self.vector_top_k = int(os.getenv("RAG_VECTOR_TOP_K", "5"))

    def retrieve(self, authorization: str, intent: str, keyword: str, input_text: str) -> List[Dict[str, Any]]:
        results, _ = self.retrieve_with_meta(authorization, intent, keyword, input_text)
        return results

    def retrieve_with_meta(
        self,
        authorization: str,
        intent: str,
        keyword: str,
        input_text: str,
        rag_strategy: str = "naive_rag",
    ) -> tuple[List[Dict[str, Any]], Dict[str, Any]]:
        if not keyword:
            return [], {"javaBackendCount": 0, "documentCount": 0}
        if intent == "schedule":
            results = java_backend_retriever.search_schedule(authorization, input_text)
            return results, {"javaBackendCount": len(results), "documentCount": 0}

        java_results = java_backend_retriever.search_keyword(authorization, keyword)
        document_results = self._search_documents(keyword=keyword, input_text=input_text, rag_strategy=rag_strategy)
        results = java_results + document_results
        return results, {
            "javaBackendCount": len(java_results),
            "documentCount": len(document_results),
            "documentTopK": self.vector_top_k,
            "documentRetriever": self._document_retriever_name(rag_strategy),
            "rerankingEnabled": rag_strategy == "reranking",
            **self._strategy_meta(rag_strategy, query=f"{keyword} {input_text}".strip()),
        }

    def _search_documents(self, keyword: str, input_text: str, rag_strategy: str) -> List[Dict[str, Any]]:
        query = f"{keyword} {input_text}".strip()
        if rag_strategy == "multi_query_rag":
            documents = self._multi_query_search(query)
        elif rag_strategy == "hyde":
            documents = self._hyde_search(query)
        elif rag_strategy in {"hybrid_search", "reranking"}:
            documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        else:
            documents = self.vector_retriever.search(query, top_k=self.vector_top_k)

        if rag_strategy == "reranking":
            documents = self.reranker.rerank(query, documents)[:self.vector_top_k]

        return [
            {
                "type": "knowledge_document",
                "id": document.id,
                "name": document.source,
                "source": document.source,
                "content": document.content,
                "score": document.score,
                "metadata": document.metadata,
            }
            for document in documents
        ]

    def _multi_query_search(self, query: str) -> List[RagDocument]:
        queries = self.multi_query_transformer.transform(query)
        by_id: dict[str, RagDocument] = {}
        scores: dict[str, float] = {}
        rank_constant = 60
        for query_index, transformed_query in enumerate(queries):
            documents = self.vector_retriever.search(transformed_query, top_k=self.vector_top_k)
            for rank, document in enumerate(documents, start=1):
                existing = by_id.get(document.id)
                if existing is None:
                    by_id[document.id] = document
                    existing = document
                scores[document.id] = scores.get(document.id, 0.0) + 1.0 / (rank_constant + rank)
                matches = existing.metadata.setdefault("multiQueryMatches", [])
                matches.append({"queryIndex": query_index, "query": transformed_query, "rank": rank})

        merged: List[RagDocument] = []
        for document_id, document in by_id.items():
            score = scores.get(document_id, 0.0)
            merged.append(RagDocument(
                id=document.id,
                content=document.content,
                source=document.source,
                score=score,
                metadata={**document.metadata, "multiQueryScore": score},
            ))
        merged.sort(key=lambda item: item.score or 0, reverse=True)
        return merged[:self.vector_top_k]

    def _hyde_search(self, query: str) -> List[RagDocument]:
        hypothetical_document = self.hyde_transformer.transform(query)
        documents = self.vector_retriever.search(hypothetical_document, top_k=self.vector_top_k)
        for document in documents:
            document.metadata["hydeQuery"] = hypothetical_document
        return documents

    def _strategy_meta(self, rag_strategy: str, query: str) -> Dict[str, Any]:
        if rag_strategy == "multi_query_rag":
            queries = self.multi_query_transformer.transform(query)
            return {"transformedQueries": queries, "queryTransform": "multi_query"}
        if rag_strategy == "hyde":
            hypothetical_document = self.hyde_transformer.transform(query)
            return {
                "queryTransform": "hyde",
                "hypotheticalDocumentLength": len(hypothetical_document),
            }
        return {}

    def _document_retriever_name(self, rag_strategy: str) -> str:
        if rag_strategy == "multi_query_rag":
            return "vector+multi_query"
        if rag_strategy == "hyde":
            return "vector+hyde"
        if rag_strategy == "reranking":
            return "hybrid_search+lexical_reranker"
        if rag_strategy == "hybrid_search":
            return "hybrid_search"
        return "vector"


retriever_agent = RetrieverAgent()
