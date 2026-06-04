from pathlib import Path
from typing import Any, Dict, List

from app.rag.core.types import RagDocument
from app.rag.evaluators import RetrievalGrader
from app.rag.generation.context_builder import ContextBuilder
from app.rag.generation.speculative import SpeculativeGenerator
from app.rag.query_transformers.hyde import HydeTransformer
from app.rag.query_transformers.multi_query import MultiQueryTransformer
from app.rag.rerankers import LexicalReranker
from app.rag.retrievers import GraphRetriever, HybridRetriever, ParentChildRetriever, VectorRetriever, java_backend_retriever
from app.rag.routers.adaptive_router import AdaptiveRagRouter
from app.rag.structured.text_to_sql import TextToSqlService
from app.multi_agents.runtime import complete_agent_or_raise


class TextbookKnowledgeAgent:
    def __init__(self) -> None:
        self.vector_retriever = VectorRetriever()
        self.hybrid_retriever = HybridRetriever()
        self.parent_child_retriever = ParentChildRetriever()
        self.graph_retriever = GraphRetriever()
        self.multi_query_transformer = MultiQueryTransformer()
        self.hyde_transformer = HydeTransformer()
        self.retrieval_grader = RetrievalGrader()
        self.reranker = LexicalReranker()
        self.adaptive_router = AdaptiveRagRouter()
        self.text_to_sql = TextToSqlService()
        self.speculative_generator = SpeculativeGenerator()
        self.context_builder = ContextBuilder()
        self.vector_top_k = 5
        self._last_crag_meta: Dict[str, Any] = {}
        self._last_self_rag_meta: Dict[str, Any] = {}
        self._last_agentic_meta: Dict[str, Any] = {}
        self._last_graph_meta: Dict[str, Any] = {}
        self._last_speculative_meta: Dict[str, Any] = {}

    def retrieve(self, authorization: str, intent: str, keyword: str, input_text: str) -> List[Dict[str, Any]]:
        results, _ = self.retrieve_with_meta(authorization, intent, keyword, input_text)
        return results

    def summarize_knowledge_points(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise("textbook_knowledge_agent", topic, evidence, model_provider=chat_service)

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

        query = f"{keyword} {input_text}".strip()
        effective_strategy = self._effective_strategy(rag_strategy, query)
        java_results = [] if effective_strategy == "text_to_sql" else java_backend_retriever.search_keyword(authorization, keyword)
        document_results = self._search_documents(keyword=keyword, input_text=input_text, rag_strategy=effective_strategy)
        results = java_results + document_results

        meta = {
            "javaBackendCount": len(java_results),
            "documentCount": len(document_results),
            "documentTopK": self.vector_top_k,
            "documentRetriever": self._document_retriever_name(effective_strategy),
            "parentChildEnabled": effective_strategy in {"parent_child", "multi_agent_rag"},
            "rerankingEnabled": effective_strategy in {"reranking", "crag", "self_rag", "agentic_rag", "multi_agent_rag"},
            **self._routing_meta(rag_strategy, effective_strategy),
            **self._crag_meta(effective_strategy, query, document_results),
            **self._strategy_meta(effective_strategy, query),
        }
        return results, meta

    def _effective_strategy(self, rag_strategy: str, query: str) -> str:
        if rag_strategy == "adaptive_rag":
            routed = self.adaptive_router.route(query)
            return routed if routed != "adaptive_rag" else "naive_rag"
        return rag_strategy or "naive_rag"

    def _search_documents(self, keyword: str, input_text: str, rag_strategy: str) -> List[Dict[str, Any]]:
        query = f"{keyword} {input_text}".strip()
        if rag_strategy == "text_to_sql":
            return self._text_to_sql_search(query)

        if rag_strategy == "parent_child":
            documents = self.parent_child_retriever.search(query, top_k=self.vector_top_k)
        elif rag_strategy == "crag":
            documents = self._crag_search(query)
        elif rag_strategy == "self_rag":
            documents = self._self_rag_search(query)
        elif rag_strategy == "multi_query_rag":
            documents = self._multi_query_search(query)
        elif rag_strategy == "hyde":
            documents = self._hyde_search(query)
        elif rag_strategy == "graph_rag":
            documents = self._graph_search(query)
        elif rag_strategy == "agentic_rag":
            documents = self._agentic_search(query)
        elif rag_strategy == "multi_agent_rag":
            documents = self._multi_agent_search(query)
        elif rag_strategy == "multimodal_rag":
            documents = self._multimodal_search(query)
        elif rag_strategy == "speculative_rag":
            documents = self._speculative_search(query)
        elif rag_strategy in {"hybrid_search", "reranking"}:
            documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        else:
            documents = self.vector_retriever.search(query, top_k=self.vector_top_k)

        if rag_strategy == "reranking":
            documents = self.reranker.rerank(query, documents)[:self.vector_top_k]

        return self._format_documents(documents)

    def _crag_search(self, query: str) -> List[RagDocument]:
        initial_documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        grade = self.retrieval_grader.grade(query, initial_documents)
        self._last_crag_meta = {
            "initialGrade": {
                "score": grade.score,
                "sufficient": grade.sufficient,
                "reason": grade.reason,
            },
            "correctiveAction": "none",
            "initialDocumentCount": len(initial_documents),
        }
        if grade.sufficient:
            return initial_documents

        repaired_documents = self._multi_query_search(query)
        combined = self._deduplicate_documents(initial_documents + repaired_documents)
        reranked = self.reranker.rerank(query, combined)[:self.vector_top_k]
        self._last_crag_meta["correctiveAction"] = "multi_query_repair+rerank"
        self._last_crag_meta["repairedDocumentCount"] = len(repaired_documents)
        self._last_crag_meta["finalDocumentCount"] = len(reranked)
        return reranked

    def _self_rag_search(self, query: str) -> List[RagDocument]:
        initial_documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        initial_grade = self.retrieval_grader.grade(query, initial_documents)
        documents = initial_documents
        action = "accept"
        if not initial_grade.sufficient:
            repaired_documents = self._multi_query_search(query)
            documents = self.reranker.rerank(query, self._deduplicate_documents(initial_documents + repaired_documents))[:self.vector_top_k]
            action = "self_repair"
        final_grade = self.retrieval_grader.grade(query, documents)
        self._last_self_rag_meta = {
            "selfReflection": {
                "needRetrieval": bool(query),
                "initialSufficient": initial_grade.sufficient,
                "finalSufficient": final_grade.sufficient,
                "action": action,
                "score": final_grade.score,
            }
        }
        return documents

    def _graph_search(self, query: str) -> List[RagDocument]:
        documents = self.graph_retriever.search_paths(query, top_k=self.vector_top_k)
        fallback_used = False
        if not documents:
            fallback_used = True
            documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        self._last_graph_meta = {"graphEnabled": True, "graphFallbackUsed": fallback_used}
        return documents

    def _text_to_sql_search(self, query: str) -> List[Dict[str, Any]]:
        plan = self.text_to_sql.plan(query)
        if not plan.sql:
            return []
        return [{
            "type": "sql_plan",
            "id": "text_to_sql:plan",
            "name": "只读 SQL 查询计划",
            "source": "text_to_sql",
            "content": f"SQL 查询计划：{plan.sql}",
            "score": 1.0,
            "metadata": {"sql": plan.sql, "readonly": True, "rows": plan.rows, "error": plan.error},
        }]

    def _agentic_search(self, query: str) -> List[RagDocument]:
        steps = ["plan", "retrieve", "grade", "repair_if_needed", "rerank"]
        documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        grade = self.retrieval_grader.grade(query, documents)
        if not grade.sufficient:
            documents = self.reranker.rerank(query, self._deduplicate_documents(documents + self._multi_query_search(query)))[:self.vector_top_k]
        self._last_agentic_meta = {"agentSteps": steps, "initialGrade": grade.score}
        return documents

    def _multi_agent_search(self, query: str) -> List[RagDocument]:
        hybrid_documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        parent_documents = self.parent_child_retriever.search(query, top_k=self.vector_top_k)
        documents = self.reranker.rerank(query, self._deduplicate_documents(hybrid_documents + parent_documents))[:self.vector_top_k]
        self._last_agentic_meta = {
            "agents": ["leader_agent", "textbook_knowledge_agent"],
            "hybridCount": len(hybrid_documents),
            "parentChildCount": len(parent_documents),
        }
        return documents

    def _multimodal_search(self, query: str) -> List[RagDocument]:
        documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        enriched: List[RagDocument] = []
        for document in documents:
            suffix = Path(document.source).suffix.lower()
            modality = {
                ".csv": "table",
                ".json": "structured_json",
                ".html": "html",
                ".htm": "html",
                ".md": "markdown",
                ".markdown": "markdown",
            }.get(suffix, "text")
            enriched.append(RagDocument(
                id=document.id,
                content=document.content,
                source=document.source,
                score=document.score,
                metadata={**document.metadata, "modality": modality},
            ))
        return enriched

    def _speculative_search(self, query: str) -> List[RagDocument]:
        draft = self.speculative_generator.draft(query)
        documents = self.hybrid_retriever.search(query, top_k=self.vector_top_k)
        evidence = self.context_builder.build(documents)
        revised = self.speculative_generator.revise(draft, evidence)
        self._last_speculative_meta = {
            "speculativeEnabled": True,
            "draftLength": len(draft),
            "revisedLength": len(revised),
        }
        return documents

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

    def _deduplicate_documents(self, documents: List[RagDocument]) -> List[RagDocument]:
        by_id: dict[str, RagDocument] = {}
        for document in documents:
            existing = by_id.get(document.id)
            if existing is None or (document.score or 0.0) > (existing.score or 0.0):
                by_id[document.id] = document
        return list(by_id.values())

    def _format_documents(self, documents: List[RagDocument]) -> List[Dict[str, Any]]:
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

    def _routing_meta(self, requested_strategy: str, effective_strategy: str) -> Dict[str, Any]:
        if requested_strategy == effective_strategy:
            return {}
        meta = {"requestedRagStrategy": requested_strategy, "effectiveRagStrategy": effective_strategy}
        if requested_strategy == "adaptive_rag":
            meta["adaptiveRoutedStrategy"] = effective_strategy
        return meta

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
        if rag_strategy == "semantic_chunking":
            return {"semanticChunkingEnabled": True}
        if rag_strategy == "self_rag":
            return self._last_self_rag_meta
        if rag_strategy == "graph_rag":
            return self._last_graph_meta
        if rag_strategy == "text_to_sql":
            plan = self.text_to_sql.plan(query)
            return {"textToSqlEnabled": True, "sql": plan.sql, "readonly": bool(plan.sql), "error": plan.error}
        if rag_strategy in {"agentic_rag", "multi_agent_rag"}:
            return self._last_agentic_meta
        if rag_strategy == "multimodal_rag":
            return {"multimodalEnabled": True, "supportedModalities": ["markdown", "table", "structured_json", "html", "text"]}
        if rag_strategy == "speculative_rag":
            return self._last_speculative_meta
        return {}

    def _crag_meta(self, rag_strategy: str, query: str, document_results: List[Dict[str, Any]]) -> Dict[str, Any]:
        if rag_strategy != "crag":
            return {}
        documents = [
            RagDocument(
                id=str(item.get("id", "")),
                content=str(item.get("content", "")),
                source=str(item.get("source", "")),
                score=item.get("score"),
                metadata=item.get("metadata") if isinstance(item.get("metadata"), dict) else {},
            )
            for item in document_results
            if item.get("type") == "knowledge_document"
        ]
        grade = self.retrieval_grader.grade(query, documents)
        return {
            "correctiveEnabled": True,
            **self._last_crag_meta,
            "retrievalGrade": {
                "score": grade.score,
                "sufficient": grade.sufficient,
                "reason": grade.reason,
            },
        }

    def _document_retriever_name(self, rag_strategy: str) -> str:
        if rag_strategy == "crag":
            return "hybrid_search+retrieval_grader+repair"
        if rag_strategy == "self_rag":
            return "hybrid_search+self_reflection+repair"
        if rag_strategy == "parent_child":
            return "parent_child"
        if rag_strategy == "multi_query_rag":
            return "vector+multi_query"
        if rag_strategy == "hyde":
            return "vector+hyde"
        if rag_strategy == "graph_rag":
            return "graph_paths+hybrid_fallback"
        if rag_strategy == "text_to_sql":
            return "text_to_sql_plan"
        if rag_strategy == "agentic_rag":
            return "agentic_planner+hybrid+repair"
        if rag_strategy == "multi_agent_rag":
            return "leader+textbook_knowledge_agents"
        if rag_strategy == "multimodal_rag":
            return "multimodal_parser+hybrid_search"
        if rag_strategy == "speculative_rag":
            return "speculative_draft+hybrid_verify"
        if rag_strategy == "reranking":
            return "hybrid_search+lexical_reranker"
        if rag_strategy == "hybrid_search":
            return "hybrid_search"
        if rag_strategy == "semantic_chunking":
            return "semantic_chunking+vector"
        return "vector"


textbook_knowledge_agent = TextbookKnowledgeAgent()
