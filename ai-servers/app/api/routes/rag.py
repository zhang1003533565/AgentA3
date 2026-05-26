import os
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Header, HTTPException

from app.models.schemas import (
    RagDocumentIngestRequest,
    RagDocumentIngestResponse,
    RagDocumentResponse,
    RagQueryRequest,
    RagQueryResponse,
    RagTraceResponse,
)
from app.rag.core import RAG_STRATEGY_SPECS, RagQuery
from app.rag.embeddings import build_embedding_provider
from app.rag.engine import rag_engine
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.pipelines import IngestInputDocument, RagIngestionPipeline
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
        "strategies": [
            {
                "name": name,
                "category": RAG_STRATEGY_SPECS[name]["category"],
                "purpose": RAG_STRATEGY_SPECS[name]["purpose"],
                "status": "implemented",
            }
            for name in rag_engine.list_strategies()
        ]
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

    result = strategy.run(RagQuery(
        text=request.input,
        keyword=request.keyword or "",
        intent=request.intent,
        metadata=request.metadata,
    ))
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
