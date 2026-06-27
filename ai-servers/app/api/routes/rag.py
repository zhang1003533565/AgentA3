import asyncio
import hashlib
import json
import re
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.models.schemas import (
    RagDocumentResponse,
    RagQueryRequest,
    RagQueryResponse,
    RagTraceResponse,
)
from app.model_providers.multimodal import append_image_references_to_text, collect_request_image_references
from app.model_providers.runtime_config import build_llm_runtime_config, reset_active_llm_config, set_active_llm_config
from app.multi_agents.catalog import AGENT_ORDER, get_agent_catalog, get_agent_detail, get_agent_profile, normalize_agent_name, update_agent_example_input
from app.multi_agents.leader_agent.agent import leader_agent
from app.multi_agents.question_bank_schema import review_question_bank_payload
from app.multi_agents.runner import run_specialist_agent
from app.rag.document_conversion import PdfConversionError, PptConversionError, convert_pdf, convert_ppt_to_docx
from app.rag.structured.text_to_sql import TextToSqlService
from app.services.data_store import data_store
from app.utils.logger import get_logger
from app.utils.sse import build_sse, chunk_answer

router = APIRouter(prefix="/internal/rag", tags=["internal-rag"])
logger = get_logger("api.rag")


class PdfConvertRequest(BaseModel):
    fileName: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)
    targetFormat: str = Field(min_length=1, max_length=16)


class PptConvertRequest(BaseModel):
    fileName: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)


class AgentExampleInputUpdateRequest(BaseModel):
    input: str = Field(min_length=1, max_length=12000)


class QuestionBankReviewRequest(BaseModel):
    payload: Dict[str, Any]
    expectedType: Optional[str] = Field(default=None, max_length=64)


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


@router.get("/capabilities")
def get_rag_capabilities(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return {
        "query": {
            "defaultMode": "leader_orchestration",
            "localKnowledgeBase": False,
            "localRagStrategies": False,
            "knowledgeBaseBoundary": "第三方知识库由 Java 后端对接；AI Server 不维护本地知识库、向量库或检索策略。",
            "answerSynthesizer": "llm_required_from_java_system_config",
            "noLocalFallback": True,
        },
        "documentConversion": {
            "supportedInputs": ["pdf", "pptx"],
            "supportedOutputs": ["docx"],
            "ocr": False,
            "imageHandling": {
                "docx": "原生 PDF 文字会重建为 Word 文字，图片单独保留；不支持扫描件 OCR",
                "pptxDocx": "PPTX 转 DOCX 会按幻灯片顺序重排内容，保留文本、表格和图片",
            },
            "noLocalFallback": True,
        },
        "structuredKnowledge": {
            "textToSql": True,
        },
        "agents": AGENT_ORDER,
        "agentInvocation": {
            "chatParameter": "agentName",
            "ragQueryParameter": "agentName",
            "automaticRouting": "不传 agentName 或传 leader_agent",
            "ragStrategyAccepted": False,
        },
    }


@router.get("/framework")
def get_rag_framework(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return {
        "knowledgeBaseBoundary": "第三方知识库能力由 Java 后端连接并对外代理；AI Server 只负责模型、多智能体、文件转换和工具编排。",
        "coverage": [],
        "runtimeFolders": {
            "modelProviders": "app/model_providers",
            "multiAgents": "app/multi_agents",
            "langgraphWorkflow": "app/langgraph",
            "documentConversion": "app/rag/document_conversion",
            "textToSql": "app/rag/structured",
        },
        "modelProviders": [
            {
                "name": "deepseek",
                "runtime": "app.model_providers.deepseek.provider",
                "status": "implemented",
                "configSource": "Java system_config: ai.service.text.provider / ai.service.text.base-url / ai.service.text.api-key / ai.service.text.model",
            },
            {
                "name": "xiaomi",
                "runtime": "app.model_providers.xiaomi.provider",
                "status": "implemented",
                "defaultBaseUrl": "https://api.xiaomimimo.com/v1",
                "exampleModel": "mimo-v2.5-pro",
                "configSource": "Java system_config: ai.service.text.provider / ai.service.text.base-url / ai.service.text.api-key / ai.service.text.model",
            },
            {
                "name": "qwen",
                "runtime": "app.model_providers.qwen.provider",
                "status": "implemented",
                "defaultBaseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "exampleModel": "qwen-vl-plus",
                "supportedModalities": ["text", "image_url"],
                "configSource": "Java system_config: ai.service.text.provider / ai.service.text.base-url / ai.service.text.api-key / ai.service.text.model",
            },
            {
                "name": "xfyun",
                "runtime": "app.model_providers.xfyun.provider",
                "status": "implemented",
                "defaultBaseUrl": "https://spark-api-open.xf-yun.com/v1",
                "exampleModel": "4.0Ultra",
                "supportedModalities": ["text"],
                "plannedModalities": ["vision", "image", "video"],
                "configSource": "Java system_config: ai.service.text.provider / ai.service.text.base-url / ai.service.text.api-key / ai.service.text.model",
            },
            {
                "name": "volcengine",
                "runtime": "app.model_providers.volcengine.provider",
                "status": "implemented",
                "defaultBaseUrl": "https://ark.cn-beijing.volces.com/api/v3",
                "exampleModel": "deepseek-v3",
                "supportedModalities": ["text"],
                "configSource": "Java system_config: ai.service.text.provider / ai.service.text.base-url / ai.service.text.api-key / ai.service.text.model",
            },
        ],
        "runtimeEnv": [
            {"name": "X-AI-Provider", "configured": "由 Java 请求头传入", "source": "ai.service.text.provider"},
            {"name": "X-AI-Base-Url", "configured": "由 Java 请求头传入", "source": "ai.service.text.base-url"},
            {"name": "X-AI-Api-Key", "configured": "由 Java 请求头传入", "source": "ai.service.text.api-key"},
            {"name": "X-AI-Model", "configured": "由 Java 请求头传入", "source": "ai.service.text.model"},
        ],
        "apis": [
            "GET /internal/rag/capabilities",
            "GET /internal/rag/framework",
            "GET /internal/rag/agents",
            "POST /internal/rag/query",
            "POST /internal/rag/pdf/convert",
            "POST /internal/rag/ppt/convert",
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


@router.put("/agents/{agent_name}/example-input")
def save_rag_agent_example_input(
    agent_name: str,
    request: AgentExampleInputUpdateRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return update_agent_example_input(agent_name, request.input)


@router.post("/question-bank/review")
def review_question_bank(
    request: QuestionBankReviewRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return review_question_bank_payload(request.payload, expected_type=request.expectedType)


@router.post("/question-bank/validate")
def validate_question_bank(
    request: QuestionBankReviewRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    review = review_question_bank_payload(request.payload, expected_type=request.expectedType)
    if not review["valid"]:
        raise HTTPException(status_code=400, detail=review)
    return review


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


@router.post("/query/stream")
async def run_rag_query_stream(
    request: RagQueryRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
):
    _require_authorization(authorization)
    llm_config = build_llm_runtime_config(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    )

    async def event_stream():
        yield build_sse("status", {"stage": "processing"})
        token = set_active_llm_config(llm_config)
        try:
            response = await asyncio.to_thread(_run_rag_query_core, request, authorization or "")
            metadata = response.metadata or {}
            session_id = str((request.metadata or {}).get("sessionId") or "")
            yield build_sse("session", {
                "sessionId": session_id,
                "model": llm_config.model,
                "agentName": metadata.get("executedAgent") or metadata.get("targetAgent") or metadata.get("agentName") or "leader_agent",
                "answerType": response.answerType,
            })
            yield build_sse("search", {
                "searchKeyword": request.keyword or "",
                "matchedResults": [document.model_dump() for document in response.documents],
                "retrievalMeta": metadata,
            })
            for chunk in chunk_answer(response.answer):
                yield build_sse("delta", {"content": chunk})
                await asyncio.sleep(0.035)
            yield build_sse("done", {
                "sessionId": session_id,
                "answer": response.answer,
                "answerType": response.answerType,
                "outputType": response.outputType,
                "outputTypes": response.outputTypes,
                "outputMeta": response.outputMeta,
                "attachments": response.attachments,
                "ragStrategy": response.strategy,
                "agentName": metadata.get("executedAgent") or metadata.get("targetAgent") or metadata.get("agentName") or "leader_agent",
                "searchKeyword": request.keyword or "",
                "matchedResults": [document.model_dump() for document in response.documents],
                "retrievalMeta": metadata,
                "trace": [step.model_dump() for step in response.trace],
            })
        except Exception as exc:
            logger.exception("rag stream failed agent=%s", request.agentName or "-")
            yield build_sse("error", {"message": str(exc)})
        finally:
            reset_active_llm_config(token)

    return StreamingResponse(event_stream(), media_type="text/event-stream")


def _run_rag_query_core(request: RagQueryRequest, authorization: str) -> RagQueryResponse:
    request.input = append_image_references_to_text(request.input, collect_request_image_references(request))
    requested_agent = normalize_agent_name(request.agentName)
    if request.agentName and not requested_agent:
        raise HTTPException(status_code=400, detail="智能体不存在")

    active_agent = requested_agent or "leader_agent"
    if active_agent == "leader_agent":
        return _run_leader_orchestration(request, authorization)
    if not _is_agent_enabled(request, active_agent):
        return _run_disabled_agent_response(request, active_agent)

    agent_profile = get_agent_profile(active_agent)
    if agent_profile and not agent_profile.get("needRetrieval", True):
        return _run_direct_agent(request, agent_profile)

    return _run_agent_without_local_retrieval(request, active_agent)


def _run_leader_orchestration(request: RagQueryRequest, authorization: str) -> RagQueryResponse:
    profile_context = _profile_context_from_request(request)
    plan = leader_agent.plan(request.input, request.ragStrategy or "", profile_context=profile_context)
    if plan.action == "direct_answer":
        return _run_leader_direct_answer(plan, profile_context=profile_context)
    if plan.action == "call_tool":
        if plan.tool_name == "text_to_sql":
            return _run_text_to_sql_tool(request, plan)
        if plan.tool_name == "java_schedule_api":
            return _run_schedule_tool(request, authorization, plan)

    agent_profile = get_agent_profile(plan.target_agent)
    if not agent_profile:
        raise HTTPException(status_code=502, detail=f"Leader 路由到了不存在的目标智能体：{plan.target_agent}")
    if not _is_agent_enabled(request, plan.target_agent):
        return _run_disabled_agent_response(request, plan.target_agent, leader_plan=plan)
    if not agent_profile.get("needRetrieval", True):
        return _run_direct_agent(request, agent_profile, leader_plan=plan)
    return _run_agent_without_local_retrieval(request, plan.target_agent, leader_plan=plan)


def _agent_toggles_from_request(request: RagQueryRequest) -> Dict[str, Any]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    toggles = metadata.get("agentToggles")
    if isinstance(toggles, dict):
        return toggles
    disabled_agents = metadata.get("disabledAgents")
    if isinstance(disabled_agents, list):
        return {str(item): False for item in disabled_agents if str(item or "").strip()}
    return {}


def _is_agent_enabled(request: RagQueryRequest, agent_name: Optional[str]) -> bool:
    normalized = normalize_agent_name(agent_name)
    if not normalized or normalized == "leader_agent":
        return True
    toggles = _agent_toggles_from_request(request)
    if normalized not in toggles:
        return True
    return _parse_agent_enabled_value(toggles.get(normalized))


def _parse_agent_enabled_value(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    text = str(value or "").strip().lower()
    return text not in {"0", "false", "off", "disabled", "no"}


def _run_disabled_agent_response(
    request: RagQueryRequest,
    agent_name: str,
    leader_plan=None,
) -> RagQueryResponse:
    normalized = normalize_agent_name(agent_name) or str(agent_name or "").strip()
    profile = get_agent_profile(normalized) or {}
    role = profile.get("role") or normalized or "目标智能体"
    if leader_plan:
        answer = (
            f"Leader 已识别到需要调用「{role}（{normalized}）」；"
            "但后台开关当前为关闭，所以本次已跳过该智能体，没有继续执行。"
        )
        execution_mode = "leader_skipped_disabled_agent"
        execution_label = "Leader 跳过已关闭智能体"
    else:
        answer = (
            f"你选择的「{role}（{normalized}）」当前未开启，"
            "所以本次没有执行该智能体。请在后台多智能体页面开启后再使用。"
        )
        execution_mode = "direct_disabled_agent"
        execution_label = "已关闭智能体未执行"

    metadata = {
        "agentName": "leader_agent" if leader_plan else normalized,
        "targetAgent": normalized,
        "executedAgent": None,
        "disabledAgent": normalized,
        "agentDisabled": True,
        "needRetrieval": False,
        "retrievalSkipped": True,
        "strategyLabel": "智能体已关闭，跳过执行",
        "executionMode": execution_mode,
        "executionModeLabel": execution_label,
        "answerType": "text",
    }
    if leader_plan:
        metadata.update({
            "intent": leader_plan.intent,
            "leaderAction": leader_plan.action,
            "leaderActionLabel": _leader_action_label(leader_plan.action),
            "routeReason": leader_plan.route_reason,
        })

    trace = []
    if leader_plan:
        trace.append(RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)))
    trace.append(RagTraceResponse(
        stage="agent_skipped",
        detail={
            "agentName": normalized,
            "reason": "agent_disabled",
            "message": "后台智能体开关关闭，跳过执行",
        },
    ))
    return _decorate_output_response(RagQueryResponse(
        strategy="agent_disabled",
        answer=answer,
        answerType="text",
        documents=[],
        trace=trace,
        metadata=metadata,
    ))


def _profile_context_from_request(request: RagQueryRequest) -> Dict[str, Any]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    profile_context = metadata.get("profileSnapshot")
    return profile_context if isinstance(profile_context, dict) else {}


def _profile_evidence_from_request(request: RagQueryRequest) -> List[Dict[str, Any]]:
    profile_context = _profile_context_from_request(request)
    if not profile_context:
        return []
    content = json.dumps({
        "overallScore": profile_context.get("overallScore"),
        "confidenceLevel": profile_context.get("confidenceLevel"),
        "profileTags": profile_context.get("profileTags"),
        "strongDimensions": profile_context.get("strongDimensions"),
        "weakDimensions": profile_context.get("weakDimensions"),
        "resourcePreference": profile_context.get("resourcePreference"),
        "dimensions": profile_context.get("dimensions"),
        "leaderUsageRules": profile_context.get("leaderUsageRules"),
    }, ensure_ascii=False)
    return [{
        "id": "user_profile_snapshot",
        "source": "user_profile",
        "content": content,
        "score": 1.0,
        "metadata": {
            "profileContextUsed": True,
            "updateMode": profile_context.get("updateMode"),
            "updateContract": profile_context.get("updateContract"),
        },
    }]


def _run_agent_without_local_retrieval(
    request: RagQueryRequest,
    active_agent: str,
    leader_plan=None,
) -> RagQueryResponse:
    profile_evidence = _profile_evidence_from_request(request)
    answer = run_specialist_agent(active_agent, request.input, profile_evidence)
    answer_type = _answer_type_for_agent(active_agent)
    trace = []
    if leader_plan:
        trace.append(RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)))
    trace.append(RagTraceResponse(
        stage="agent_answer",
        detail={
            "agentName": active_agent,
            "answerLength": len(answer or ""),
            "executionMode": "leader_routed_agent" if leader_plan else "direct_agent",
            "localRetrievalSkipped": True,
        },
    ))
    metadata = {
        "needRetrieval": False,
        "retrievalSkipped": True,
        "localKnowledgeBase": False,
        "localRagStrategies": False,
        "strategyLabel": "直接执行智能体",
        "answerType": answer_type,
        "profileContextUsed": bool(profile_evidence),
    }
    if leader_plan:
        metadata.update({
            "agentName": "leader_agent",
            "targetAgent": active_agent,
            "executedAgent": active_agent,
            "intent": leader_plan.intent,
            "leaderAction": leader_plan.action,
            "routeReason": leader_plan.route_reason,
            "executionMode": "leader_routed_agent",
            "executionModeLabel": "Leader 路由后直接执行专业智能体",
        })
    else:
        metadata["agentName"] = active_agent
        metadata["executionMode"] = "direct_agent"
        metadata["executionModeLabel"] = "专业智能体直接处理"
    return _decorate_output_response(RagQueryResponse(
        strategy="direct_agent",
        answer=answer,
        answerType=answer_type,
        documents=[],
        trace=trace,
        metadata=metadata,
    ))


def _run_leader_direct_answer(plan, profile_context: Optional[Dict[str, Any]] = None) -> RagQueryResponse:
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
        "profileContextUsed": bool(profile_context),
    }
    return _decorate_output_response(RagQueryResponse(
        strategy="leader_direct_answer",
        answer=answer,
        answerType="text",
        documents=[],
        trace=[
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(plan)),
            RagTraceResponse(stage="direct_answer", detail={"answerLength": len(answer or "")}),
        ],
        metadata=metadata,
    ))


def _run_direct_agent(
    request: RagQueryRequest,
    agent_profile: Dict[str, Any],
    leader_plan=None,
) -> RagQueryResponse:
    agent_name = agent_profile["name"]
    profile_evidence = _profile_evidence_from_request(request)
    answer = run_specialist_agent(agent_name, request.input, profile_evidence)
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
        "profileContextUsed": bool(profile_evidence),
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
    return _decorate_output_response(RagQueryResponse(
        strategy="direct_agent",
        answer=answer,
        answerType=answer_type,
        documents=[],
        trace=trace,
        metadata=metadata,
    ))


def _run_text_to_sql_tool(request: RagQueryRequest, leader_plan) -> RagQueryResponse:
    result = TextToSqlService().plan(request.input)
    metadata = {
        "sql": result.sql,
        "rows": result.rows,
        "rowCount": len(result.rows),
        "readonly": bool(result.sql),
        "error": result.error,
    }
    answer = _format_text_to_sql_answer(metadata)
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
        "strategyLabel": _strategy_label("text_to_sql"),
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用 Text-to-SQL 接口",
        "answerType": "tool_result",
    })
    trace = [
        RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
        RagTraceResponse(stage="generate_sql", detail={"readonly": bool(result.sql), "sql": result.sql, "error": result.error}),
        RagTraceResponse(stage="tool_call", detail={"toolName": "text_to_sql", "strategy": "text_to_sql"}),
    ]
    return _decorate_output_response(RagQueryResponse(
        strategy="text_to_sql",
        answer=answer,
        answerType="tool_result",
        documents=[],
        trace=trace,
        metadata=metadata,
    ))


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
    results = data_store.search_schedule(authorization, request.input)
    retrieval_meta = {"javaBackendCount": len(results), "documentCount": 0}
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
    return _decorate_output_response(RagQueryResponse(
        strategy="java_schedule_api",
        answer=answer,
        answerType="tool_result",
        documents=documents,
        trace=[
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
            RagTraceResponse(stage="tool_call", detail={"toolName": leader_plan.tool_name, **retrieval_meta}),
        ],
        metadata=metadata,
    ))


def _decorate_output_response(response: RagQueryResponse) -> RagQueryResponse:
    attachments = _extract_response_attachments(response.answer)
    output_types = _infer_output_types(response.answerType, response.metadata, attachments)
    response.attachments = attachments
    response.outputTypes = output_types
    response.outputType = output_types[0] if output_types else "text"
    response.outputMeta = {
        "pushStrategy": _push_strategy_for_output(response.answerType, response.metadata, output_types),
        "attachmentCount": len(attachments),
        "displayPolicy": "App 会话页优先展示结构化附件；文本中出现图片/文档链接时也会转成卡片。",
    }
    if response.metadata is None:
        response.metadata = {}
    response.metadata["outputType"] = response.outputType
    response.metadata["outputTypes"] = output_types
    response.metadata["attachmentCount"] = len(attachments)
    response.metadata["pushStrategy"] = response.outputMeta["pushStrategy"]
    return response


def _infer_output_types(answer_type: str, metadata: Dict[str, Any], attachments: List[Dict[str, Any]]) -> List[str]:
    types: List[str] = []
    if any(item.get("type") == "image" for item in attachments):
        types.append("image")
    if any(item.get("type") == "video" for item in attachments):
        types.append("video")
    if any(item.get("type") in {"pdf", "docx", "ppt", "excel", "file"} for item in attachments):
        types.append("document")
    normalized_answer_type = str(answer_type or "").strip()
    agent = str((metadata or {}).get("executedAgent") or (metadata or {}).get("targetAgent") or "").strip()
    if normalized_answer_type in {"image_generation", "image_prompt", "ppt_image_prompt"} or agent in {
        "image_agent",
        "diagram_mind_map_agent",
        "diagram_architecture_agent",
        "diagram_flowchart_agent",
        "diagram_activity_agent",
    }:
        if "image" not in types:
            types.append("image")
    if normalized_answer_type == "document_conversion" or agent == "ppt_to_docx_agent":
        if "document" not in types:
            types.append("document")
    if normalized_answer_type in {"mermaid_mindmap", "mermaid_flowchart", "mermaid_activity_flowchart", "mermaid_architecture"}:
        if "diagram" not in types:
            types.append("diagram")
    if not types:
        types.append("text")
    return types


def _push_strategy_for_output(answer_type: str, metadata: Dict[str, Any], output_types: List[str]) -> Dict[str, Any]:
    agent = str((metadata or {}).get("executedAgent") or (metadata or {}).get("targetAgent") or "").strip()
    if "image" in output_types:
        return {
            "pushType": "image",
            "trigger": "用户要求生成图片、流程图、活动图、架构图、思维导图图片、PPT 配图或封面图时触发。",
            "agent": agent or "image_agent",
            "display": "以图片卡片推送到会话页，支持点击预览。",
        }
    if "document" in output_types:
        return {
            "pushType": "document",
            "trigger": "用户要求导出、转换、下载、生成 Word/PDF/PPT/Excel 文档或上传 PPTX 转 DOCX 时触发。",
            "agent": agent or "document_agent",
            "display": "以文档卡片推送到会话页，支持点击打开；不支持打开时复制链接。",
        }
    return {
        "pushType": "text",
        "trigger": "普通问答、知识点解释、会议总结、题库 JSON 和策略说明默认触发。",
        "agent": agent or "leader_agent",
        "display": "以文本消息展示。",
    }


def _extract_response_attachments(answer: str) -> List[Dict[str, Any]]:
    content = str(answer or "")
    attachments: List[Dict[str, Any]] = []
    parsed = _try_parse_json_object(content)
    if parsed:
        attachments.extend(_attachments_from_json_payload(parsed))

    markdown_pattern = re.compile(
        r"!?\[([^\]]+)\]\(((?:https?://|/uploads/)[^\s\"'<>，。！？；、)]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?|xlsx?|csv)(?:\?[^\s\"'<>，。！？；、)]*)?)\)",
        re.IGNORECASE,
    )
    for match in markdown_pattern.finditer(content):
        attachments.append(_build_attachment(match.group(2), match.group(1), ""))

    plain_text = markdown_pattern.sub("", content)
    url_pattern = re.compile(
        r"(?:https?://|/uploads/)[^\s\"'<>，。！？；、]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?|xlsx?|csv)(?:\?[^\s\"'<>，。！？；、]*)?",
        re.IGNORECASE,
    )
    for match in url_pattern.finditer(plain_text):
        attachments.append(_build_attachment(match.group(0), "", ""))

    normalized: List[Dict[str, Any]] = []
    seen = set()
    for item in attachments:
        if not item:
            continue
        url = str(item.get("url") or "").strip()
        if not url or url in seen:
            continue
        seen.add(url)
        normalized.append(item)
    return normalized


def _attachments_from_json_payload(payload: Dict[str, Any]) -> List[Dict[str, Any]]:
    attachments: List[Dict[str, Any]] = []
    for key, default_type in (
        ("images", "image"),
        ("documents", "document"),
        ("files", "file"),
        ("attachments", "file"),
    ):
        value = payload.get(key)
        if isinstance(value, list):
            for item in value:
                attachments.append(_attachment_from_json_item(item, default_type))
    for key, default_type in (
        ("imageUrl", "image"),
        ("image_url", "image"),
        ("documentUrl", "document"),
        ("document_url", "document"),
        ("fileUrl", "file"),
        ("file_url", "file"),
        ("url", ""),
    ):
        if payload.get(key):
            attachments.append(_attachment_from_json_item(payload, default_type, url_key=key))
    return [item for item in attachments if item]


def _attachment_from_json_item(item: Any, default_type: str, url_key: str = "url") -> Dict[str, Any]:
    if isinstance(item, str):
        return _build_attachment(item, "", default_type)
    if not isinstance(item, dict):
        return {}
    url = str(item.get(url_key) or item.get("url") or item.get("fileUrl") or item.get("href") or "").strip()
    if not url:
        return {}
    name = str(item.get("name") or item.get("fileName") or item.get("title") or "").strip()
    type_hint = str(item.get("type") or item.get("fileType") or item.get("mimeType") or default_type or "").strip()
    attachment = _build_attachment(url, name, type_hint)
    if item.get("index") is not None:
        attachment["index"] = item.get("index")
    if item.get("status") is not None:
        attachment["status"] = item.get("status")
    return attachment


def _build_attachment(url: str, name: str = "", type_hint: str = "") -> Dict[str, Any]:
    normalized_url = str(url or "").strip()
    if not normalized_url:
        return {}
    ext = _file_ext(name or normalized_url)
    hinted = str(type_hint or "").lower()
    attachment_type = "file"
    if "image" in hinted or ext in {"png", "jpg", "jpeg", "gif", "webp", "bmp"}:
        attachment_type = "image"
    elif "video" in hinted or ext in {"mp4", "mov", "m4v", "webm", "ogg"}:
        attachment_type = "video"
    elif ext == "pdf":
        attachment_type = "pdf"
    elif ext in {"doc", "docx"}:
        attachment_type = "docx"
    elif ext in {"ppt", "pptx"}:
        attachment_type = "ppt"
    elif ext in {"xls", "xlsx", "csv"}:
        attachment_type = "excel"
    elif hinted in {"document", "file"}:
        attachment_type = "file"
    if attachment_type == "file" and not ext:
        return {}
    return {
        "url": normalized_url,
        "name": name or _file_name_from_url(normalized_url),
        "type": attachment_type,
        "ext": ext,
    }


def _try_parse_json_object(content: str) -> Dict[str, Any]:
    raw = (content or "").strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```(?:json)?", "", raw, flags=re.IGNORECASE).strip()
        raw = re.sub(r"```$", "", raw).strip()
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        return {}


def _file_ext(value: str) -> str:
    clean = str(value or "").split("?")[0].lower()
    index = clean.rfind(".")
    return clean[index + 1:] if index >= 0 else ""


def _file_name_from_url(url: str) -> str:
    clean = str(url or "").split("?")[0].rstrip("/")
    name = clean[clean.rfind("/") + 1:] if "/" in clean else clean
    return name or "文件"


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
        "strategyLabel": _strategy_label(plan.rag_strategy) if plan.rag_strategy else "直接处理",
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
        "direct_agent": "直接处理",
        "java_schedule_api": "Java 课表接口",
        "text_to_sql": "Text-to-SQL",
    }
    if strategy_name in custom_labels:
        return custom_labels[strategy_name]
    return strategy_name or "直接处理"


def _answer_type_for_agent(agent_name: str) -> str:
    mapping = {
        "leader_agent": "text",
        "mind_map_agent": "mermaid_mindmap",
        "diagram_architecture_agent": "image_generation",
        "textbook_knowledge_agent": "markdown",
        "ppt_outline_agent": "ppt_outline",
        "ppt_layout_agent": "ppt_layout",
        "ppt_review_agent": "ppt_review",
        "ppt_image_agent": "ppt_image_prompt",
        "ppt_to_docx_agent": "document_conversion",
        "image_agent": "image_generation",
    }
    if (agent_name or "").startswith("textbook_question_"):
        return "question_bank"
    if (agent_name or "").startswith("meeting_"):
        return "markdown"
    return mapping.get(agent_name or "", "text")


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


@router.post("/ppt/convert")
def convert_ppt_document(
    request: PptConvertRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    filename = request.fileName or "presentation.pptx"
    if not filename.lower().endswith(".pptx"):
        raise HTTPException(status_code=400, detail="当前仅支持上传 PPTX 文件；请先将 PPT 另存为 PPTX")
    try:
        import base64
        ppt_bytes = base64.b64decode(request.contentBase64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="PPTX Base64 内容无效") from exc
    logger.info("ppt convert request filename=%s size=%s", filename, len(ppt_bytes))
    try:
        result = convert_ppt_to_docx(ppt_bytes, filename)
        logger.info(
            "ppt convert success filename=%s output=%s content_length=%s images=%s slides=%s",
            filename,
            result.get("fileName"),
            result.get("contentLength"),
            result.get("imageCount"),
            result.get("slideCount"),
        )
        return result
    except PptConversionError as exc:
        logger.warning("ppt convert failed filename=%s reason=%s", filename, exc)
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc


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
