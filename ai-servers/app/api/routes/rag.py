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
from app.multi_agents.catalog import get_agent_catalog, get_agent_detail
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
            "uploadEncoding": "text_or_base64",
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
            "leader_agent",
            "mind_map_agent",
            "md_knowledge_agent",
            "textbook_knowledge_agent",
            "textbook_question_bank_agent",
            "ppt_agent",
            "image_agent",
        ],
    }


@router.get("/framework")
def get_rag_framework(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return {
        "sourceDocument": "https://www.cnblogs.com/yupi/p/19914426",
        "coverage": [
            {"name": name, **spec, "status": "implemented"}
            for name, spec in RAG_STRATEGY_SPECS.items()
        ],
        "runtimeFolders": {
            "modelProviders": "app/model_providers",
            "ragCore": "app/rag/core",
            "strategies": "app/rag/strategies",
            "multiAgents": "app/multi_agents",
            "langgraphWorkflow": "app/langgraph",
            "indexing": "app/rag/indexing",
            "retrievers": "app/rag/retrievers",
            "vectorStores": "app/rag/vector_stores",
            "graphStores": "app/rag/graph_stores",
            "evaluators": "app/rag/evaluators",
        },
        "modelProviders": [
            {"name": "deepseek", "runtime": "app.model_providers.deepseek.provider", "status": "implemented"},
        ],
        "embeddingProviders": [
            {"name": "local_lexical", "status": "implemented", "requiredEnv": []},
            {"name": "openai", "status": "implemented_optional", "requiredEnv": ["OPENAI_API_KEY"]},
            {"name": "dashscope", "status": "implemented_optional", "requiredEnv": ["DASHSCOPE_API_KEY"]},
            {"name": "bge", "status": "implemented_optional", "requiredEnv": ["RAG_BGE_MODEL_NAME"]},
            {"name": "sentence_transformers", "status": "implemented_optional", "requiredEnv": ["RAG_SENTENCE_TRANSFORMERS_MODEL"]},
        ],
        "vectorStores": [
            {"name": "local_jsonl", "status": "implemented", "requiredEnv": []},
            {"name": "faiss", "status": "implemented_optional", "requiredEnv": ["RAG_FAISS_INDEX_DIR"]},
            {"name": "milvus", "status": "implemented_optional", "requiredEnv": ["RAG_MILVUS_URI", "RAG_MILVUS_COLLECTION"]},
            {"name": "elasticsearch", "status": "implemented_optional", "requiredEnv": ["RAG_ELASTICSEARCH_URL", "RAG_ELASTICSEARCH_INDEX"]},
            {"name": "pgvector", "status": "implemented_optional", "requiredEnv": ["RAG_PGVECTOR_DSN", "RAG_PGVECTOR_TABLE"]},
        ],
        "graphStores": [
            {"name": "local_graph", "status": "implemented", "requiredEnv": []},
            {"name": "neo4j", "status": "implemented_optional", "requiredEnv": ["RAG_NEO4J_URI", "RAG_NEO4J_USERNAME", "RAG_NEO4J_PASSWORD"]},
        ],
        "indexing": {
            "supportedSuffixes": sorted(DocumentLoader.SUPPORTED_SUFFIXES),
            "defaultChunker": "semantic_boundary",
            "parentChildChunker": "parent_child",
            "uploadEncoding": "text_or_base64",
            "localIndexFile": str(_knowledge_base_root() / ".index" / "local_chunks.jsonl"),
        },
        "runtimeEnv": [
            {"name": "RAG_KNOWLEDGE_BASE_DIR", "configured": bool(os.getenv("RAG_KNOWLEDGE_BASE_DIR")), "default": "knowledge_base/raw"},
            {"name": "RAG_EMBEDDING_PROVIDER", "configured": bool(os.getenv("RAG_EMBEDDING_PROVIDER")), "default": "local_lexical"},
            {"name": "RAG_VECTOR_STORE_BACKEND", "configured": bool(os.getenv("RAG_VECTOR_STORE_BACKEND")), "default": "local_jsonl"},
            {"name": "RAG_GRAPH_STORE_BACKEND", "configured": bool(os.getenv("RAG_GRAPH_STORE_BACKEND")), "default": "local_graph"},
            {"name": "RAG_SQLITE_DB_PATH", "configured": bool(os.getenv("RAG_SQLITE_DB_PATH")), "default": ""},
            {"name": "JAVA_BACKEND_BASE_URL", "configured": bool(os.getenv("JAVA_BACKEND_BASE_URL")), "default": "http://localhost:8080"},
        ],
        "apis": [
            "GET /internal/rag/strategies",
            "GET /internal/rag/capabilities",
            "GET /internal/rag/framework",
            "GET /internal/rag/agents",
            "POST /internal/rag/query",
            "POST /internal/rag/documents",
            "POST /internal/rag/evaluate",
            "GET /internal/rag/text-to-sql/schema",
            "POST /internal/rag/text-to-sql/execute",
        ],
    }


@router.get("/agents")
def list_rag_agents(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return get_agent_catalog()


@router.get("/agents/{agent_name}")
def get_rag_agent(
    agent_name: str,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    agent = get_agent_detail(agent_name)
    if agent is None:
        raise HTTPException(status_code=404, detail="智能体不存在")
    return agent


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
        IngestInputDocument(
            content=item.content,
            content_base64=item.contentBase64,
            source=item.source,
            metadata=item.metadata,
        )
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
