import os
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Header, HTTPException

from app.models.schemas import (
    RagDocumentIngestRequest,
    RagDocumentIngestResponse,
    RagDocumentResponse,
    RagEvaluateRequest,
    RagEvaluateResponse,
    RagQueryRequest,
    RagQueryResponse,
    RagTraceResponse,
)
from app.rag.core import RAG_STRATEGY_SPECS, RagQuery
from app.rag.core.types import RagDocument
from app.rag.embeddings import build_embedding_provider
from app.rag.engine import rag_engine
from app.rag.evaluators import RagEvaluationInput, RagEvaluator
from app.rag.graph_stores import build_graph_store
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.pipelines import IngestInputDocument, RagIngestionPipeline
from app.rag.structured.text_to_sql import TextToSqlService
from app.rag.vector_stores import build_vector_store
from app.utils.logger import get_logger

router = APIRouter(prefix="/internal/rag", tags=["internal-rag"])
logger = get_logger("api.rag")


@router.get("/strategies")
def list_rag_strategies(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return {
        "total": len(rag_engine.list_strategies()),
        "strategies": [
            {
                "name": name,
                "category": RAG_STRATEGY_SPECS[name]["category"],
                "purpose": RAG_STRATEGY_SPECS[name]["purpose"],
                "status": "implemented",
                "runtime": f"app.rag.strategies.{name}.strategy",
            }
            for name in rag_engine.list_strategies()
        ]
    }


@router.get("/strategies/{strategy_name}")
def get_rag_strategy(
    strategy_name: str,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    if strategy_name not in RAG_STRATEGY_SPECS:
        raise HTTPException(status_code=404, detail="RAG 策略不存在")
    spec = RAG_STRATEGY_SPECS[strategy_name]
    return {
        "name": strategy_name,
        "category": spec["category"],
        "purpose": spec["purpose"],
        "status": "implemented",
        "runtime": f"app.rag.strategies.{strategy_name}.strategy",
        "docs": f"app/rag/strategies/{strategy_name}/README.md",
    }


@router.get("/capabilities")
def get_rag_capabilities(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return {
        "query": {
            "strategies": rag_engine.describe_strategies(),
            "defaultStrategy": "naive_rag",
            "answerSynthesizer": "local_context_synthesizer",
        },
        "indexing": {
            "supportedSuffixes": sorted(DocumentLoader.SUPPORTED_SUFFIXES),
            "defaultChunker": "semantic_boundary",
            "indexStore": "local_jsonl",
        },
        "retrieval": {
            "retrievers": ["keyword", "vector", "hybrid", "parent_child", "graph", "java_backend"],
            "rerankers": ["lexical"],
        },
        "evaluation": {
            "metrics": ["hitRate", "mrr", "contextRelevance", "faithfulness", "answerTermCoverage"],
        },
        "structuredKnowledge": {
            "textToSql": True,
            "graphRag": True,
        },
        "agents": [
            "orchestrator_agent",
            "planner_agent",
            "retriever_agent",
            "answer_agent",
            "critic_agent",
            "memory_agent",
            "sql_agent",
            "graph_agent",
            "tool_agent",
        ],
    }


@router.post("/query", response_model=RagQueryResponse)
def run_rag_query(
    request: RagQueryRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> RagQueryResponse:
    _require_authorization(authorization)
    strategy = rag_engine.get_strategy(request.ragStrategy)
    if request.ragStrategy and strategy.name != request.ragStrategy:
        logger.info("rag strategy fallback requested=%s active=%s", request.ragStrategy, strategy.name)

    result = rag_engine.run(RagQuery(
        text=request.input,
        keyword=request.keyword or "",
        intent=request.intent,
        metadata=request.metadata,
    ), strategy_name=request.ragStrategy)
    return RagQueryResponse(
        strategy=result.strategy,
        answer=result.answer,
        documents=[
            RagDocumentResponse(
                id=document.id,
                content=document.content,
                source=document.source,
                score=document.score,
                metadata=document.metadata,
            )
            for document in result.documents
        ],
        trace=[
            RagTraceResponse(stage=step.stage, detail=step.detail)
            for step in result.trace
        ],
        metadata=result.metadata,
    )


@router.post("/documents", response_model=RagDocumentIngestResponse)
def ingest_rag_documents(
    request: RagDocumentIngestRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> RagDocumentIngestResponse:
    _require_authorization(authorization)
    pipeline = RagIngestionPipeline(root_dir=str(_knowledge_base_root()))
    result = pipeline.run([
        IngestInputDocument(content=item.content, source=item.source, metadata=item.metadata)
        for item in request.documents
    ])

    logger.info(
        "rag documents ingested count=%s chunks=%s",
        result.stored_count,
        result.indexed_chunk_count,
    )
    return RagDocumentIngestResponse(
        storedCount=result.stored_count,
        storedFiles=result.stored_files,
        indexedChunkCount=result.indexed_chunk_count,
        indexPath=result.index_path,
        documents=[
            {
                "source": document.source,
                "storedPath": document.stored_path,
                "modality": document.modality,
                "chunkCount": document.chunk_count,
                "size": document.size,
                "metadata": document.metadata,
            }
            for document in result.documents
        ],
        trace=[
            RagTraceResponse(stage=step.stage, detail=step.detail)
            for step in result.trace
        ],
    )


@router.get("/documents")
def list_rag_documents(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    root = _knowledge_base_root()
    documents = []
    if root.exists():
        for path in sorted(root.rglob("*")):
            if path.is_file() and path.suffix.lower() in DocumentLoader.SUPPORTED_SUFFIXES:
                stat = path.stat()
                documents.append({
                    "source": str(path),
                    "size": stat.st_size,
                    "updatedAt": int(stat.st_mtime),
                })
    return {"documents": documents}


@router.get("/vector-store/health")
def vector_store_health(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    vector_store = build_vector_store(_knowledge_base_root())
    return vector_store.health()


@router.get("/embedding/health")
def embedding_health(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    embedding_provider = build_embedding_provider()
    return embedding_provider.health()


@router.post("/evaluate", response_model=RagEvaluateResponse)
def evaluate_rag(
    request: RagEvaluateRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> RagEvaluateResponse:
    _require_authorization(authorization)
    result = RagEvaluator().evaluate(RagEvaluationInput(
        query=request.query,
        answer=request.answer,
        documents=[
            RagDocument(
                id=document.id,
                content=document.content,
                source=document.source,
                score=document.score,
                metadata=document.metadata,
            )
            for document in request.documents
        ],
        expected_sources=request.expectedSources,
        expected_answer_terms=request.expectedAnswerTerms,
    ))
    return RagEvaluateResponse(metrics=result.metrics, passed=result.passed, detail=result.detail)


@router.get("/graph-store/health")
def graph_store_health(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return build_graph_store().health()


@router.get("/text-to-sql/schema")
def text_to_sql_schema(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return {"schema": TextToSqlService().introspect_sqlite_schema()}


@router.post("/text-to-sql/execute")
def text_to_sql_execute(
    request: RagQueryRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    service = TextToSqlService()
    schema = service.introspect_sqlite_schema()
    result = service.plan(request.input, schema=schema)
    return {
        "sql": result.sql,
        "rows": result.rows,
        "rowCount": len(result.rows),
        "readonly": bool(result.sql),
        "error": result.error,
    }


def _require_authorization(authorization: Optional[str]) -> None:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或Token无效")


def _knowledge_base_root() -> Path:
    root_dir = os.getenv("RAG_KNOWLEDGE_BASE_DIR", "knowledge_base/raw")
    path = Path(root_dir)
    if path.is_absolute():
        return path
    ai_server_root = Path(__file__).resolve().parents[3]
    return ai_server_root / path
