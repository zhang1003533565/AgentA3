import hashlib
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

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
from app.model_providers.runtime_config import build_llm_runtime_config, reset_active_llm_config, set_active_llm_config
from app.multi_agents.catalog import AGENT_ORDER, get_agent_catalog, get_agent_detail, get_agent_profile, normalize_agent_name
from app.multi_agents.leader_agent.agent import leader_agent
from app.multi_agents.runner import run_specialist_agent
from app.multi_agents.textbook_knowledge_agent.agent import textbook_knowledge_agent
from app.rag.core import RAG_STRATEGY_SPECS, RagQuery, RagTraceStep
from app.rag.core.types import RagDocument
from app.rag.embeddings import build_embedding_provider
from app.rag.engine import rag_engine
from app.rag.evaluators import RagEvaluationInput, RagEvaluator
from app.rag.document_conversion import PdfConversionError, convert_pdf
from app.rag.graph_stores import build_graph_store
from app.rag.indexing.document_loader import DocumentLoader
from app.rag.pipelines import IngestInputDocument, RagIngestionPipeline
from app.rag.structured.text_to_sql import TextToSqlService
from app.rag.vector_stores import build_vector_store
from app.utils.logger import get_logger

router = APIRouter(prefix="/internal/rag", tags=["internal-rag"])
logger = get_logger("api.rag")


class PdfConvertRequest(BaseModel):
    fileName: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)
    targetFormat: str = Field(min_length=1, max_length=16)


def _llm_header_audit_fields(
    provider: Optional[str],
    base_url: Optional[str],
    api_key: Optional[str],
    model: Optional[str],
) -> Dict[str, Any]:
    key = (api_key or "").strip()
    return {
        "provider": (provider or "").strip() or "-",
        "base_url": (base_url or "").strip() or "-",
        "model": (model or "").strip() or "-",
        "api_key_len": len(key),
        "api_key_suffix": key[-4:] if len(key) >= 4 else (key or "-"),
        "api_key_sha256_8": hashlib.sha256(key.encode("utf-8")).hexdigest()[:8] if key else "-",
    }


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
                "label": RAG_STRATEGY_SPECS[name].get("label", name),
                "category": RAG_STRATEGY_SPECS[name]["category"],
                "categoryLabel": RAG_STRATEGY_SPECS[name].get("categoryLabel", RAG_STRATEGY_SPECS[name]["category"]),
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
        "label": spec.get("label", strategy_name),
        "category": spec["category"],
        "categoryLabel": spec.get("categoryLabel", spec["category"]),
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
            "defaultStrategy": "leader_orchestration",
            "ragStrategyRule": "只有 needRetrieval=true 的专业智能体才使用 ragStrategy",
            "answerSynthesizer": "llm_required_from_java_system_config",
            "noLocalFallback": True,
        },
        "indexing": {
            "supportedSuffixes": sorted(DocumentLoader.SUPPORTED_SUFFIXES),
            "defaultChunker": "semantic_boundary",
            "indexStore": "local_jsonl",
            "uploadEncoding": "text_or_base64",
        },
        "documentConversion": {
            "supportedInputs": ["pdf"],
            "supportedOutputs": ["md", "docx"],
            "ocr": False,
            "imageHandling": {
                "docx": "由 pdf2docx 尽量保留图片和基础排版",
                "md": "输出 zip，Markdown 使用 assets 相对路径引用图片",
            },
            "noLocalFallback": True,
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
        "agents": AGENT_ORDER,
        "agentInvocation": {
            "chatParameter": "agentName",
            "ragQueryParameter": "agentName",
            "automaticRouting": "不传 agentName 或传 leader_agent",
        },
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
            {
                "name": "deepseek",
                "runtime": "app.model_providers.deepseek.provider",
                "status": "implemented",
                "configSource": "Java system_config: ai.service.text.provider / ai.service.text.base-url / ai.service.text.api-key / ai.service.text.model",
            },
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
            {"name": "X-AI-Provider", "configured": "由 Java 请求头传入", "source": "ai.service.text.provider"},
            {"name": "X-AI-Base-Url", "configured": "由 Java 请求头传入", "source": "ai.service.text.base-url"},
            {"name": "X-AI-Api-Key", "configured": "由 Java 请求头传入", "source": "ai.service.text.api-key"},
            {"name": "X-AI-Model", "configured": "由 Java 请求头传入", "source": "ai.service.text.model"},
        ],
        "apis": [
            "GET /internal/rag/strategies",
            "GET /internal/rag/capabilities",
            "GET /internal/rag/framework",
            "GET /internal/rag/agents",
            "POST /internal/rag/query",
            "POST /internal/rag/documents",
            "POST /internal/rag/pdf/convert",
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
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
) -> RagQueryResponse:
    _require_authorization(authorization)
    audit = _llm_header_audit_fields(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    )
    logger.info(
        "rag query request received agent=%s rag_strategy=%s input_len=%s provider=%s base_url=%s model=%s api_key_len=%s api_key_suffix=%s api_key_sha256_8=%s",
        request.agentName or "-",
        request.ragStrategy or "-",
        len(request.input or ""),
        audit["provider"],
        audit["base_url"],
        audit["model"],
        audit["api_key_len"],
        audit["api_key_suffix"],
        audit["api_key_sha256_8"],
    )
    token = set_active_llm_config(build_llm_runtime_config(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    ))
    try:
        return _run_rag_query_core(request, authorization or "")
    finally:
        reset_active_llm_config(token)


def _run_rag_query_core(request: RagQueryRequest, authorization: str) -> RagQueryResponse:
    requested_agent = normalize_agent_name(request.agentName)
    if request.agentName and not requested_agent:
        raise HTTPException(status_code=400, detail="智能体不存在")

    active_agent = requested_agent or "leader_agent"
    if active_agent == "leader_agent":
        return _run_leader_orchestration(request, authorization)

    agent_profile = get_agent_profile(active_agent)
    if agent_profile and not agent_profile.get("needRetrieval", True):
        return _run_direct_agent(request, agent_profile)

    requested_strategy = request.ragStrategy or (
        agent_profile["defaultRagStrategy"] if agent_profile else "naive_rag"
    )
    return _run_rag_then_agent(request, active_agent, requested_strategy)


def _run_leader_orchestration(request: RagQueryRequest, authorization: str) -> RagQueryResponse:
    plan = leader_agent.plan(request.input, request.ragStrategy or "")
    if plan.action == "direct_answer":
        return _run_leader_direct_answer(plan)
    if plan.action == "call_tool":
        if plan.tool_name == "text_to_sql":
            return _run_text_to_sql_tool(request, plan)
        if plan.tool_name == "java_schedule_api":
            return _run_schedule_tool(request, authorization, plan)

    agent_profile = get_agent_profile(plan.target_agent)
    if not agent_profile:
        raise HTTPException(status_code=502, detail=f"Leader 路由到了不存在的目标智能体：{plan.target_agent}")
    if not agent_profile.get("needRetrieval", True):
        return _run_direct_agent(request, agent_profile, leader_plan=plan)
    requested_strategy = plan.rag_strategy or agent_profile["defaultRagStrategy"]
    return _run_rag_then_agent(request, plan.target_agent, requested_strategy, leader_plan=plan)


def _run_rag_then_agent(
    request: RagQueryRequest,
    active_agent: str,
    requested_strategy: str,
    leader_plan=None,
) -> RagQueryResponse:
    try:
        rag_engine.get_strategy(requested_strategy)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    result = rag_engine.run(RagQuery(
        text=request.input,
        keyword=request.keyword or "",
        intent=request.intent,
        metadata=request.metadata,
    ), strategy_name=requested_strategy)
    documents = [
        {
            "id": document.id,
            "content": document.content,
            "source": document.source,
            "score": document.score,
            "metadata": document.metadata,
        }
        for document in result.documents
    ]
    answer = run_specialist_agent(active_agent, request.input, documents)
    answer_type = _answer_type_for_agent(active_agent)
    trace = []
    if leader_plan:
        trace.append(RagTraceStep(stage="leader_route", detail=_leader_plan_detail(leader_plan)))
    trace.extend(result.trace)
    metadata = dict(result.metadata)
    trace.append(RagTraceStep(
        stage="agent_answer",
        detail={
            "agentName": active_agent,
            "answerLength": len(answer or ""),
            "executionMode": "leader_routed_rag" if leader_plan else "rag_then_agent",
        },
    ))
    metadata.update({
        "needRetrieval": True,
        "retrievalSkipped": False,
        "strategyLabel": _strategy_label(result.strategy),
        "answerType": answer_type,
    })
    if leader_plan:
        metadata.update({
            "agentName": "leader_agent",
            "targetAgent": active_agent,
            "executedAgent": active_agent,
            "intent": leader_plan.intent,
            "leaderAction": leader_plan.action,
            "routeReason": leader_plan.route_reason,
            "plannedRagStrategy": leader_plan.rag_strategy,
            "executionMode": "leader_routed_rag",
            "executionModeLabel": "Leader 路由后执行 RAG + 专业智能体",
        })
    else:
        metadata["agentName"] = active_agent
        metadata["executionMode"] = "rag_then_agent"
        metadata["executionModeLabel"] = "RAG 检索后交给专业智能体"
    return RagQueryResponse(
        strategy=result.strategy,
        answer=answer,
        answerType=answer_type,
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
            for step in trace
        ],
        metadata=metadata,
    )


def _run_leader_direct_answer(plan) -> RagQueryResponse:
    answer = (plan.answer or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail="Leader LLM 选择直接回答，但 answer 为空，已禁止本地兜底回答")
    metadata = {
        "agentName": "leader_agent",
        "intent": plan.intent,
        "targetAgent": plan.target_agent,
        "executedAgent": "leader_agent",
        "needRetrieval": plan.need_retrieval,
        "leaderAction": plan.action,
        "leaderActionLabel": _leader_action_label(plan.action),
        "routeReason": plan.route_reason,
        "executionMode": "leader_direct_answer",
        "executionModeLabel": "Leader 直接回答",
        "retrievalSkipped": True,
        "strategyLabel": "直接回答（不使用 RAG）",
        "answerType": "text",
    }
    return RagQueryResponse(
        strategy="leader_direct_answer",
        answer=answer,
        answerType="text",
        documents=[],
        trace=[
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(plan)),
            RagTraceResponse(stage="direct_answer", detail={"answerLength": len(answer or "")}),
        ],
        metadata=metadata,
    )


def _run_direct_agent(
    request: RagQueryRequest,
    agent_profile: Dict[str, Any],
    leader_plan=None,
) -> RagQueryResponse:
    agent_name = agent_profile["name"]
    answer = run_specialist_agent(agent_name, request.input, [])
    answer_type = _answer_type_for_agent(agent_name)
    metadata = {
        "agentName": "leader_agent" if leader_plan else agent_name,
        "targetAgent": agent_name,
        "executedAgent": agent_name,
        "intent": agent_profile["intent"],
        "needRetrieval": False,
        "retrievalSkipped": True,
        "strategyLabel": "直接处理（不使用 RAG）",
        "executionMode": "leader_routed_direct_agent" if leader_plan else "direct_agent",
        "executionModeLabel": "Leader 分发给非检索智能体" if leader_plan else "专业智能体直接处理",
        "answerType": answer_type,
    }
    if leader_plan:
        metadata.update({
            "leaderAction": leader_plan.action,
            "leaderActionLabel": _leader_action_label(leader_plan.action),
            "routeReason": leader_plan.route_reason,
        })
    trace = []
    if leader_plan:
        trace.append(RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)))
    trace.append(RagTraceResponse(
        stage="direct_agent",
        detail={**metadata, "answerLength": len(answer or "")},
    ))
    return RagQueryResponse(
        strategy="direct_agent",
        answer=answer,
        answerType=answer_type,
        documents=[],
        trace=trace,
        metadata=metadata,
    )


def _run_text_to_sql_tool(request: RagQueryRequest, leader_plan) -> RagQueryResponse:
    result = rag_engine.run(RagQuery(
        text=request.input,
        keyword=request.keyword or "",
        intent=leader_plan.intent,
        metadata=request.metadata,
    ), strategy_name="text_to_sql")
    metadata = dict(result.metadata)
    answer = result.answer or _format_text_to_sql_answer(metadata)
    if not answer:
        raise HTTPException(
            status_code=502,
            detail=metadata.get("error") or "Text-to-SQL 未生成可执行 SQL 或可展示结果，已禁止本地兜底回答",
        )
    metadata.update({
        "agentName": "leader_agent",
        "targetAgent": "text_to_sql",
        "executedAgent": "text_to_sql",
        "intent": leader_plan.intent,
        "needRetrieval": False,
        "retrievalSkipped": True,
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "toolName": leader_plan.tool_name,
        "routeReason": leader_plan.route_reason,
        "strategyLabel": _strategy_label(result.strategy),
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用 Text-to-SQL 接口",
        "answerType": "tool_result",
    })
    trace = [
        RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
        *[RagTraceResponse(stage=step.stage, detail=step.detail) for step in result.trace],
        RagTraceResponse(stage="tool_call", detail={"toolName": "text_to_sql", "strategy": result.strategy}),
    ]
    return RagQueryResponse(
        strategy=result.strategy,
        answer=answer,
        answerType="tool_result",
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
        trace=trace,
        metadata=metadata,
    )


def _format_text_to_sql_answer(metadata: Dict[str, Any]) -> str:
    sql = str(metadata.get("sql") or "").strip()
    if not sql:
        return ""
    rows = metadata.get("rows")
    if not isinstance(rows, list):
        rows = []
    if not rows:
        return f"已生成只读 SQL：{sql}\n当前查询结果为空。"
    previews = []
    for row in rows[:8]:
        if isinstance(row, dict):
            previews.append("，".join(f"{key}={value}" for key, value in row.items()))
        else:
            previews.append(str(row))
    return f"已生成只读 SQL：{sql}\n查询到 {len(rows)} 条结果：\n" + "\n".join(
        f"{index}. {text}" for index, text in enumerate(previews, start=1)
    )


def _run_schedule_tool(request: RagQueryRequest, authorization: str, leader_plan) -> RagQueryResponse:
    results, retrieval_meta = textbook_knowledge_agent.retrieve_with_meta(
        authorization=authorization,
        intent="schedule",
        keyword="课表查询",
        input_text=request.input,
        rag_strategy="",
    )
    documents = [_tool_result_to_document(item, index) for index, item in enumerate(results, start=1)]
    if results:
        lines = ["## 课表接口查询结果"]
        for item in results[:8]:
            lines.append(
                f"- {item.get('weekdayText') or ''} {item.get('classSessions') or ''}："
                f"{item.get('name') or item.get('courseName') or '课程'}"
                f"（{item.get('location') or '地点待补充'}，{item.get('teacherName') or '教师待补充'}）"
            )
        answer = "\n".join(lines)
    else:
        answer = "Leader 已识别为课表查询，并调用 Java 后端课表接口，但当前没有返回可展示的课程数据。请确认 Java 服务和登录态是否正常。"
    metadata = {
        "agentName": "leader_agent",
        "targetAgent": "java_schedule_api",
        "executedAgent": "java_schedule_api",
        "intent": leader_plan.intent,
        "needRetrieval": False,
        "retrievalSkipped": True,
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "toolName": leader_plan.tool_name,
        "routeReason": leader_plan.route_reason,
        "strategyLabel": "Java 课表接口",
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用 Java 后端接口",
        "answerType": "tool_result",
        **retrieval_meta,
    }
    return RagQueryResponse(
        strategy="java_schedule_api",
        answer=answer,
        answerType="tool_result",
        documents=documents,
        trace=[
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
            RagTraceResponse(stage="tool_call", detail={"toolName": leader_plan.tool_name, **retrieval_meta}),
        ],
        metadata=metadata,
    )


def _tool_result_to_document(item: Dict[str, Any], index: int) -> RagDocumentResponse:
    content_parts = [
        str(item.get("name") or item.get("courseName") or item.get("content") or "").strip(),
        str(item.get("location") or "").strip(),
        str(item.get("teacherName") or "").strip(),
        str(item.get("classSessions") or "").strip(),
    ]
    content = " ".join(part for part in content_parts if part) or str(item)
    return RagDocumentResponse(
        id=str(item.get("id") or f"tool:{index}"),
        content=content,
        source=str(item.get("type") or "java_backend"),
        score=1.0,
        metadata=item,
    )


def _leader_plan_detail(plan) -> Dict[str, Any]:
    return {
        **plan.to_dict(),
        "leaderActionLabel": _leader_action_label(plan.action),
        "strategyLabel": _strategy_label(plan.rag_strategy) if plan.rag_strategy else "不使用 RAG",
    }


def _leader_action_label(action: str) -> str:
    labels = {
        "direct_answer": "直接回答",
        "delegate_agent": "调用专业智能体",
        "call_tool": "调用接口/工具",
    }
    return labels.get(action or "", action or "未知动作")


def _strategy_label(strategy_name: str) -> str:
    custom_labels = {
        "leader_direct_answer": "Leader 直接回答",
        "direct_agent": "直接处理（不使用 RAG）",
        "java_schedule_api": "Java 课表接口",
    }
    if strategy_name in custom_labels:
        return custom_labels[strategy_name]
    spec = RAG_STRATEGY_SPECS.get(strategy_name)
    if not spec:
        return strategy_name or "不使用 RAG"
    return spec.get("label", strategy_name)


def _answer_type_for_agent(agent_name: str) -> str:
    mapping = {
        "leader_agent": "text",
        "mind_map_agent": "mermaid_mindmap",
        "textbook_knowledge_agent": "markdown",
        "ppt_agent": "ppt_outline",
        "image_agent": "image_prompt",
    }
    if (agent_name or "").startswith("textbook_question_"):
        return "question_bank"
    return mapping.get(agent_name or "", "text")


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


@router.post("/pdf/convert")
def convert_pdf_document(
    request: PdfConvertRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    filename = request.fileName or "document.pdf"
    if not filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="仅支持上传 PDF 文件")
    try:
        import base64
        pdf_bytes = base64.b64decode(request.contentBase64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="PDF Base64 内容无效") from exc
    logger.info(
        "pdf convert request filename=%s target_format=%s size=%s",
        filename,
        request.targetFormat,
        len(pdf_bytes),
    )
    try:
        result = convert_pdf(pdf_bytes, filename, request.targetFormat)
        logger.info(
            "pdf convert success filename=%s output=%s content_length=%s images=%s",
            filename,
            result.get("fileName"),
            result.get("contentLength"),
            result.get("imageCount"),
        )
        return result
    except PdfConversionError as exc:
        logger.warning("pdf convert failed filename=%s reason=%s", filename, exc)
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc


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
