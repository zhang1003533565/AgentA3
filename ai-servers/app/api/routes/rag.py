import asyncio
import hashlib
import json
import re
from typing import Any, Dict, List, Optional, Tuple

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import FileResponse, StreamingResponse
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
from app.rag.document_conversion import PdfConversionError, PptConversionError, convert_pdf, convert_ppt_to_docx, export_generated_answer
from app.rag.document_conversion.generated_exporter import GeneratedExportAccessError, open_generated_export
from app.rag.structured.text_to_sql import TextToSqlService
from app.services.assistant_resource_builder import finalize_assistant_response
from app.services.data_store import data_store
from app.utils.logger import get_logger
from app.utils.sse import build_sse, chunk_answer
from app.utils.text_utils import build_session_token, normalize_text

router = APIRouter(prefix="/internal/rag", tags=["internal-rag"])
logger = get_logger("api.rag")

VISIBLE_GENERATION_AGENTS = {
    "image_agent",
    "diagram_mind_map_agent",
    "diagram_architecture_agent",
    "diagram_flowchart_agent",
    "diagram_activity_agent",
}


class AgentExecutionError(Exception):
    def __init__(
        self,
        *,
        message: str,
        agent_name: str = "",
        intent: str = "",
        stage: str = "agent_failed",
        route_reason: str = "",
        status_code: int = 500,
        raw_message: str = "",
        model_provider: str = "",
        model: str = "",
        base_url: str = "",
        model_config_prefix: str = "",
    ) -> None:
        super().__init__(message)
        self.message = message
        self.agent_name = agent_name
        self.intent = intent
        self.stage = stage
        self.route_reason = route_reason
        self.status_code = status_code
        self.raw_message = raw_message or message
        self.model_provider = model_provider
        self.model = model
        self.base_url = base_url
        self.model_config_prefix = model_config_prefix

GENERATED_CONTENT_TOOLS = [
    {
        "name": "generated_export_tools",
        "zhName": "内容整理工具",
        "displayName": "内容整理工具（generated_export_tools）",
        "category": "content_export",
        "purpose": "内容整理工具总开关。关闭后 Leader 不会调用导出整理能力，自动附件整理也会停止。",
        "trigger": "用户要求文件版、文档版、表格版、源码版，或智能体生成结果适合沉淀为附件。",
        "outputs": ["md", "docx", "xlsx", "mmd", "zip"],
        "status": "implemented",
    },
    {
        "name": "markdown_export_tool",
        "zhName": "Markdown 导出工具",
        "displayName": "Markdown 导出工具（markdown_export_tool）",
        "category": "content_export",
        "purpose": "把知识点、会议纪要、PPT 大纲、题库等生成结果保存为 Markdown 阅读文件。",
        "trigger": "专业智能体返回 markdown/question_bank/mermaid，或用户要求 md/Markdown 文件版。",
        "outputs": ["md"],
        "status": "implemented",
    },
    {
        "name": "docx_export_tool",
        "zhName": "Word 导出工具",
        "displayName": "Word 导出工具（docx_export_tool）",
        "category": "content_export",
        "purpose": "把长内容整理成 Word 文档，减少会话里大段文字堆叠。",
        "trigger": "用户要求 Word/DOCX/文档版/文件版，或内容适合沉淀为资料。",
        "outputs": ["docx"],
        "status": "implemented",
    },
    {
        "name": "excel_export_tool",
        "zhName": "Excel 导出工具",
        "displayName": "Excel 导出工具（excel_export_tool）",
        "category": "content_export",
        "purpose": "把题库 JSON 或知识点清单整理成 Excel 表格，方便导入、筛选和二次加工。",
        "trigger": "题库 JSON、知识清单、用户要求 Excel/表格。",
        "outputs": ["xlsx"],
        "status": "implemented",
    },
    {
        "name": "content_archive_tool",
        "zhName": "附件打包工具",
        "displayName": "附件打包工具（content_archive_tool）",
        "category": "content_export",
        "purpose": "把同一轮生成的 md/docx/xlsx/mmd 附件打包成一个 zip，方便一次下载。",
        "trigger": "任意内容导出工具生成两个及以上附件后自动触发。",
        "outputs": ["zip"],
        "status": "implemented",
    },
    {
        "name": "diagram_source_export_tool",
        "zhName": "图表源码导出工具",
        "displayName": "图表源码导出工具（diagram_source_export_tool）",
        "category": "diagram_export",
        "purpose": "保存 Mermaid/图表源码，方便后续继续编辑、复用或交给图片工具生成图解版。",
        "trigger": "answerType 为 mermaid_* 或回答中包含 Mermaid 代码块。",
        "outputs": ["mmd", "md", "zip"],
        "status": "implemented",
    },
]

CAMPUS_SERVICE_TOOLS = [
    {
        "name": "java_schedule_api",
        "zhName": "课表查询工具",
        "displayName": "课表查询工具（java_schedule_api）",
        "category": "campus_service",
        "purpose": "调用 Java 后端课表接口查询用户课程安排、课程清单、任课老师、上课时间、上课次数、学分、考核方式等课程信息。",
        "trigger": "用户询问今天/明天/本周有什么课、几点上课、课表安排、本学期有哪些课，或某门课什么时候学/上课、老师是谁、谁教这门课、有几节课/多少次课。",
        "outputs": ["schedule_text"],
        "status": "implemented",
        "configurable": True,
    },
    {
        "name": "java_activity_api",
        "zhName": "活动查询工具",
        "displayName": "活动查询工具（java_activity_api）",
        "category": "campus_service",
        "purpose": "调用 Java 后端活动接口查询校园活动、讲座、比赛和报名信息。",
        "trigger": "用户询问最近活动、讲座、比赛、报名活动或校园活动安排。",
        "outputs": ["activity_list"],
        "status": "implemented",
        "configurable": True,
    },
    {
        "name": "java_meeting_api",
        "zhName": "会议查询工具",
        "displayName": "会议查询工具（java_meeting_api）",
        "category": "campus_service",
        "purpose": "调用 Java 后端会议接口查询我的会议、预约会议和会议状态。",
        "trigger": "用户询问我的会议、会议列表、会议状态、预约会议安排。",
        "outputs": ["meeting_list"],
        "status": "implemented",
        "configurable": True,
    },
    {
        "name": "java_canteen_api",
        "zhName": "食堂餐饮查询工具",
        "displayName": "食堂餐饮查询工具（java_canteen_api）",
        "category": "campus_service",
        "purpose": "调用 Java 后端食堂、档口、菜品和优惠接口查询餐饮信息。",
        "trigger": "用户询问食堂、餐厅、档口、菜品、吃什么或餐饮优惠。",
        "outputs": ["canteen_list"],
        "status": "implemented",
        "configurable": True,
    },
    {
        "name": "java_facility_api",
        "zhName": "设施位置查询工具",
        "displayName": "设施位置查询工具（java_facility_api）",
        "category": "campus_service",
        "purpose": "调用 Java 后端设施和地图接口查询建筑、设施位置和定位信息。",
        "trigger": "用户询问教学楼、宿舍、操场、食堂等设施在哪、位置、地图或导航。",
        "outputs": ["facility_list", "location"],
        "status": "implemented",
        "configurable": True,
    },
    {
        "name": "java_secondhand_api",
        "zhName": "旧物查询工具",
        "displayName": "旧物查询工具（java_secondhand_api）",
        "category": "campus_service",
        "purpose": "调用 Java 后端校园旧物接口查询二手、闲置、转让物品。",
        "trigger": "用户询问旧物、二手、闲置、转让、买卖物品。",
        "outputs": ["secondhand_list"],
        "status": "implemented",
        "configurable": True,
    },
]

SERVICE_TOOL_NAMES = {tool["name"] for tool in CAMPUS_SERVICE_TOOLS}

LEADER_CALLABLE_TOOLS = [
    {
        "name": "text_to_sql",
        "zhName": "结构化查询工具",
        "displayName": "结构化查询工具（text_to_sql）",
        "category": "structured_query",
        "purpose": "把统计、列表、数量类问题转换为只读 SQL，并返回可展示查询结果。",
        "trigger": "用户询问优惠券、食堂、菜品、课程、课表等结构化数据的统计、数量、列表或排名。",
        "outputs": ["sql", "text"],
        "status": "implemented",
        "configurable": True,
    },
    *CAMPUS_SERVICE_TOOLS,
    {
        "name": "generated_export_tools",
        "zhName": "内容整理工具",
        "displayName": "内容整理工具（generated_export_tools）",
        "category": "content_export",
        "purpose": "把已有 Markdown、普通文本或标准题库 JSON 直接整理成附件。",
        "trigger": "用户已经提供要导出的内容，并明确要求文件版、文档版、表格版或打包下载。",
        "outputs": ["md", "docx", "xlsx", "mmd", "zip"],
        "status": "implemented",
        "configurable": True,
    },
]


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


@router.get("/exports/{storage_key}", response_class=FileResponse)
def download_generated_export(
    storage_key: str,
    export_capability: Optional[str] = Header(default=None, alias="X-AI-Export-Capability"),
) -> FileResponse:
    try:
        export_file = open_generated_export(storage_key, export_capability)
    except GeneratedExportAccessError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc
    return FileResponse(
        path=export_file.path,
        media_type=export_file.mime_type,
        filename=export_file.storage_key,
        headers={
            "X-AI-Export-SHA256": export_file.sha256,
            "Cache-Control": "private, no-store",
        },
    )


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
            "supportedOutputs": ["docx", "md", "xlsx", "zip", "mmd"],
            "ocr": False,
            "generatedContentExport": {
                "knowledge": ["md", "docx", "xlsx", "zip"],
                "questionBank": ["md", "docx", "xlsx", "zip"],
                "diagramSource": ["mmd", "md", "zip"],
                "trigger": "知识点、会议纪要、PPT 大纲和题库 JSON 返回后由 AI Server 自动生成附件",
                "tools": GENERATED_CONTENT_TOOLS,
            },
            "imageHandling": {
                "docx": "原生 PDF 文字会重建为 Word 文字，图片单独保留；不支持扫描件 OCR",
                "pptxDocx": "PPTX 转 DOCX 会按幻灯片顺序重排内容，保留文本、表格和图片",
            },
            "noLocalFallback": True,
        },
        "structuredKnowledge": {
            "textToSql": True,
        },
        "profileSummary": {
            "agent": "profile_summary_agent",
            "purpose": "把 Java 画像快照总结为强项、欠缺、置信依据和补证建议；不修改画像分数。",
            "output": "strict_profile_summary_json",
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
        "coverage": [
            {
                "name": "generated_export_tools",
                "category": "content_export",
                "purpose": "把智能体生成结果自动转换为可下载附件，而不是只在会话中展示长文本。",
                "status": "implemented",
            },
            {
                "name": "question_bank_validation",
                "category": "quality_gate",
                "purpose": "题库智能体必须先通过严格 JSON schema 校验，不合格结果不允许导入。",
                "status": "implemented",
            },
            {
                "name": "agent_enabled_gate",
                "category": "runtime_control",
                "purpose": "Leader 路由到关闭的智能体时跳过执行，后台开关是运行边界。",
                "status": "implemented",
            },
            {
                "name": "campus_service_tools",
                "category": "java_backend",
                "purpose": "Leader 识别课表、活动、会议、食堂、设施位置、旧物查询意图后调用对应 Java 后端接口，且受后台工具开关控制。",
                "status": "implemented",
            },
            {
                "name": "profile_summary_agent",
                "category": "profile",
                "purpose": "汇总个人画像雷达图强弱、置信度、证据状态和补证建议。",
                "status": "implemented",
            },
        ],
        "serviceTools": CAMPUS_SERVICE_TOOLS,
        "generatedTools": GENERATED_CONTENT_TOOLS,
        "runtimeFolders": {
            "modelProviders": "app/model_providers",
            "multiAgents": "app/multi_agents",
            "langgraphWorkflow": "app/langgraph",
            "documentConversion": "app/rag/document_conversion",
            "generatedContentExports": "AI_EXPORT_ROOT 或开发默认目录 data/ai-exports",
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
            "AUTO generated_export_tools: md/docx/xlsx/zip/mmd attachments for generated content",
            "GET /internal/rag/text-to-sql/schema",
            "POST /internal/rag/text-to-sql/execute",
        ],
    }


@router.get("/agents")
def list_rag_agents(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    catalog = get_agent_catalog()
    catalog["leaderTools"] = LEADER_CALLABLE_TOOLS
    catalog["serviceTools"] = CAMPUS_SERVICE_TOOLS
    catalog["leaderCallableCatalog"] = _build_leader_callable_catalog()
    catalog["generatedTools"] = GENERATED_CONTENT_TOOLS
    return catalog


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


@router.get("/tool-cache/stats")
def get_tool_cache_stats(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return data_store.get_tool_cache_stats()


@router.delete("/tool-cache")
def clear_tool_cache(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    return data_store.clear_tool_cache()


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
        response = _finalize_rag_response(request, _run_rag_query_core(request, authorization or ""))
        _save_conversation_context(request, authorization or "", response)
        return response
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
        generation_started = False
        try:
            request.input = _prepare_request_input(request)
            requested_agent = normalize_agent_name(request.agentName)
            if request.agentName and not requested_agent:
                raise HTTPException(status_code=400, detail="智能体不存在")

            active_agent = requested_agent or "leader_agent"
            if active_agent == "leader_agent":
                profile_context = _profile_context_from_request(request)
                callable_catalog = _build_leader_callable_catalog(request)
                conversation_context = _apply_conversation_context(request, authorization or "")
                plan = await asyncio.to_thread(
                    leader_agent.plan,
                    request.input,
                    request.ragStrategy or "",
                    profile_context=profile_context,
                    callable_catalog=callable_catalog,
                    conversation_context=conversation_context,
                )
                if getattr(plan, "action", "") == "call_tool" and getattr(plan, "answer", ""):
                    yield build_sse("tool_start", {
                        "stage": "tool_start",
                        "message": plan.answer,
                        "intent": plan.intent,
                        "toolName": plan.tool_name,
                        "toolDisplayName": _tool_display_name(plan.tool_name),
                        "routeReason": plan.route_reason,
                    })
                if _should_emit_generation_start(request, plan.target_agent, plan):
                    yield build_sse("generation_start", _build_generation_start_payload(request, plan))
                    generation_started = True
                response = await asyncio.to_thread(_execute_leader_plan, request, authorization or "", profile_context, plan)
            else:
                if _should_emit_generation_start(request, active_agent):
                    yield build_sse("generation_start", _build_generation_start_payload(request, None, active_agent))
                    generation_started = True
                response = await asyncio.to_thread(_run_rag_query_core, request, authorization or "")
            response = _finalize_rag_response(request, response)
            metadata = response.metadata or {}
            session_id = str((request.metadata or {}).get("sessionId") or "")
            yield build_sse("session", {
                "sessionId": session_id,
                "model": metadata.get("model") or getattr(llm_config, "model", "") or "",
                "agentName": metadata.get("executedAgent") or metadata.get("targetAgent") or metadata.get("agentName") or "leader_agent",
                "answerType": response.answerType,
            })
            yield build_sse("search", {
                "searchKeyword": request.keyword or "",
                "matchedResults": [document.model_dump() for document in response.documents],
                "retrievalMeta": metadata,
            })
            if not generation_started:
                for chunk in chunk_answer(response.answer):
                    yield build_sse("delta", {"content": chunk})
                    await asyncio.sleep(0.035)
            _save_conversation_context(request, authorization or "", response)
            yield build_sse("done", {
                "sessionId": session_id,
                "answer": response.answer,
                "answerType": response.answerType,
                "outputType": response.outputType,
                "outputTypes": response.outputTypes,
                "outputMeta": response.outputMeta,
                "attachments": response.attachments,
                "resources": response.resources,
                "evidenceChain": response.evidenceChain,
                "ragStrategy": response.strategy,
                "agentName": metadata.get("executedAgent") or metadata.get("targetAgent") or metadata.get("agentName") or "leader_agent",
                "searchKeyword": request.keyword or "",
                "matchedResults": [document.model_dump() for document in response.documents],
                "retrievalMeta": metadata,
                "trace": [step.model_dump() for step in response.trace],
            })
        except Exception as exc:
            logger.exception("rag stream failed agent=%s", request.agentName or "-")
            yield build_sse("error", _build_stream_error_payload(request, exc, llm_config))
        finally:
            reset_active_llm_config(token)

    return StreamingResponse(event_stream(), media_type="text/event-stream")


def _run_rag_query_core(request: RagQueryRequest, authorization: str) -> RagQueryResponse:
    request.input = _prepare_request_input(request)
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


def _finalize_rag_response(request: RagQueryRequest, response: RagQueryResponse) -> RagQueryResponse:
    request_metadata = request.metadata if isinstance(request.metadata, dict) else {}
    response_metadata = response.metadata if isinstance(response.metadata, dict) else {}
    return finalize_assistant_response(
        response,
        request_context={
            "requestId": request_metadata.get("requestId"),
            "query": request_metadata.get("contextOriginalInput") or request.input or "",
            "agent": response_metadata.get("executedAgent")
            or response_metadata.get("targetAgent")
            or response_metadata.get("agentName")
            or request.agentName
            or "leader_agent",
            "model": response_metadata.get("model") or "",
            "profileContextUsed": bool(
                response_metadata.get("profileContextUsed") or request_metadata.get("profileSnapshot")
            ),
            "conversationContextUsed": bool(request_metadata.get("conversationContextUsed")),
        },
    )


def _run_leader_orchestration(request: RagQueryRequest, authorization: str) -> RagQueryResponse:
    profile_context = _profile_context_from_request(request)
    callable_catalog = _build_leader_callable_catalog(request)
    conversation_context = _apply_conversation_context(request, authorization)
    plan = leader_agent.plan(
        request.input,
        request.ragStrategy or "",
        profile_context=profile_context,
        callable_catalog=callable_catalog,
        conversation_context=conversation_context,
    )
    return _execute_leader_plan(request, authorization, profile_context, plan)


def _prepare_request_input(request: RagQueryRequest) -> str:
    return append_image_references_to_text(request.input, collect_request_image_references(request))


def _apply_conversation_context(request: RagQueryRequest, authorization: str) -> Dict[str, Any]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    request.metadata = metadata
    session_token = _session_token_from_request(request, authorization)
    context = leader_agent.load_context(session_token) if session_token else {}
    bounded_context = _bounded_conversation_context(context)
    original_input = str(metadata.get("contextOriginalInput") or request.input or "")
    metadata["contextOriginalInput"] = original_input
    metadata["conversationContext"] = bounded_context
    expanded_input = _contextualize_followup_input(original_input, bounded_context)
    if expanded_input and expanded_input != request.input:
        metadata["conversationContextUsed"] = True
        metadata["contextualizedInput"] = expanded_input
        metadata["contextExpansion"] = {
            "originalInput": original_input,
            "contextualizedInput": expanded_input,
            "lastSubjects": bounded_context.get("lastSubjects") or [],
            "summaryAvailable": bool(bounded_context.get("summary")),
        }
        request.input = expanded_input
    else:
        metadata["conversationContextUsed"] = bool((bounded_context.get("turns") or []) or bounded_context.get("summary"))
    return bounded_context


def _bounded_conversation_context(context: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(context, dict):
        return {}
    turns = context.get("turns") if isinstance(context.get("turns"), list) else []
    bounded_turns = []
    for turn in turns[-6:]:
        if not isinstance(turn, dict):
            continue
        bounded_turns.append({
            "user": str(turn.get("user") or "")[:600],
            "assistant": str(turn.get("assistant") or "")[:900],
            "metadata": turn.get("metadata") if isinstance(turn.get("metadata"), dict) else {},
            "subjects": turn.get("subjects") if isinstance(turn.get("subjects"), list) else [],
        })
    return {
        "summary": str(context.get("summary") or "")[:1800],
        "turns": bounded_turns,
        "lastSubjects": [
            str(item).strip()
            for item in (context.get("lastSubjects") or [])
            if str(item or "").strip()
        ][:6],
        "compressedTurnCount": int(context.get("compressedTurnCount") or 0),
    }


def _contextualize_followup_input(input_text: str, context: Dict[str, Any]) -> str:
    text = str(input_text or "").strip()
    compact = normalize_text(text)
    if _has_explicit_current_schedule_intent(compact):
        return text
    if not text or not _is_contextual_followup(compact):
        return text
    subject = _latest_context_subject(context)
    if not subject or normalize_text(subject) in compact:
        return text
    if any(token in compact for token in ("上几次", "几次课", "多少次", "几节课", "多少节", "课时")):
        return f"{subject}本学期上几次课？"
    if any(token in compact for token in ("老师", "教师", "谁教", "谁上")):
        return f"{subject}的老师是谁？"
    if any(token in compact for token in ("什么时候", "哪天", "周几", "几点", "时间")):
        return f"{subject}什么时候上课？"
    if any(token in compact for token in ("在哪", "哪里", "哪儿", "教室", "地点")):
        return f"{subject}在哪里上课？"
    return f"{subject} {text}"


def _is_contextual_followup(compact_text: str) -> bool:
    if not compact_text:
        return False
    followup_tokens = (
        "上几次", "几次课", "多少次", "几节课", "多少节", "课时",
        "老师呢", "教师呢", "谁教", "谁上",
        "什么时候", "哪天", "周几", "几点", "时间呢",
        "在哪", "哪里", "哪儿", "教室呢", "地点呢",
        "这个呢", "它呢", "那上", "上次呢",
    )
    return len(compact_text) <= 18 and any(token in compact_text for token in followup_tokens)


def _has_explicit_current_schedule_intent(compact_text: str) -> bool:
    if not compact_text:
        return False
    no_class_tokens = (
        "没有课", "没课", "无课", "不用上课", "不上课",
        "有课吗", "有没有课", "还有课吗", "哪天没课", "哪天没有课",
        "什么时候开始没有课", "从什么时候开始没有课", "什么时候没课", "什么时候没有课",
    )
    time_scope_tokens = ("今天", "明天", "后天", "本周", "这周", "下周", "第")
    if any(token in compact_text for token in no_class_tokens):
        return True
    return any(token in compact_text for token in time_scope_tokens) and any(token in compact_text for token in ("有课", "没课", "没有课", "课吗"))


def _latest_context_subject(context: Dict[str, Any]) -> str:
    for subject in context.get("lastSubjects") or []:
        text = str(subject or "").strip()
        if text:
            return text
    for turn in reversed(context.get("turns") or []):
        if not isinstance(turn, dict):
            continue
        for subject in turn.get("subjects") or []:
            text = str(subject or "").strip()
            if text:
                return text
    return ""


def _session_token_from_request(request: RagQueryRequest, authorization: str) -> str:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    session_id = str(metadata.get("sessionId") or "").strip()
    if not session_id or not authorization:
        return ""
    try:
        return build_session_token(session_id, authorization)
    except Exception:
        return ""


def _context_metadata_from_request(request: RagQueryRequest) -> Dict[str, Any]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    result: Dict[str, Any] = {
        "conversationContextUsed": bool(metadata.get("conversationContextUsed")),
    }
    if metadata.get("contextualizedInput"):
        result["contextualizedInput"] = metadata.get("contextualizedInput")
        result["contextOriginalInput"] = metadata.get("contextOriginalInput") or ""
    if isinstance(metadata.get("contextExpansion"), dict):
        result["contextExpansion"] = metadata["contextExpansion"]
    return result


def _save_conversation_context(request: RagQueryRequest, authorization: str, response: RagQueryResponse) -> None:
    session_token = _session_token_from_request(request, authorization)
    if not session_token or response is None:
        return
    metadata = dict(response.metadata or {})
    metadata.update({
        "answerType": response.answerType,
        "strategy": response.strategy,
        "outputTypes": response.outputTypes,
        "resources": [
            {
                "id": str(resource.get("id") or "")[:80],
                "kind": str(resource.get("kind") or "")[:64],
                "deliveryType": str(resource.get("deliveryType") or "")[:64],
                "groundingStatus": str(resource.get("groundingStatus") or "")[:32],
                "title": str(resource.get("title") or "")[:160],
            }
            for resource in (response.resources or [])[:20]
            if isinstance(resource, dict)
        ],
        "evidenceChain": _safe_evidence_context_summary(response.evidenceChain),
    })
    original_input = str((request.metadata or {}).get("contextOriginalInput") or request.input or "")
    try:
        leader_agent.save_context(session_token, original_input, response.answer or "", metadata=metadata)
    except Exception as exc:
        logger.warning("leader conversation context save failed: %s", exc)


def _safe_evidence_context_summary(evidence_chain: Dict[str, Any]) -> Dict[str, Any]:
    chain = evidence_chain if isinstance(evidence_chain, dict) else {}
    generation = chain.get("generation") if isinstance(chain.get("generation"), dict) else {}
    return {
        "chainId": str(chain.get("chainId") or "")[:80],
        "status": str(chain.get("status") or "")[:32],
        "evidenceState": str(chain.get("evidenceState") or "")[:32],
        "sourceCount": len(chain.get("sources") or []) if isinstance(chain.get("sources"), list) else 0,
        "generation": {
            "agent": str(generation.get("agent") or "")[:64],
            "answerType": str(generation.get("answerType") or "")[:64],
            "profileContextUsed": bool(generation.get("profileContextUsed")),
        },
    }


def _execute_leader_plan(
    request: RagQueryRequest,
    authorization: str,
    profile_context: Optional[Dict[str, Any]],
    plan,
) -> RagQueryResponse:
    if plan.action == "direct_answer":
        return _run_leader_direct_answer(plan, profile_context=profile_context)
    if plan.action == "call_tool":
        if not _is_tool_enabled(request, plan.tool_name):
            return _run_disabled_tool_response(request, plan.tool_name, leader_plan=plan)
        if plan.tool_name == "text_to_sql":
            return _run_text_to_sql_tool(request, plan)
        if plan.tool_name in SERVICE_TOOL_NAMES:
            return _run_service_tool(request, authorization, plan)
        if plan.tool_name == "generated_export_tools":
            return _run_generated_export_tool(request, plan)
        raise HTTPException(status_code=502, detail=f"Leader 选择了未注册工具：{plan.tool_name or '空'}，已停止执行。")

    agent_profile = get_agent_profile(plan.target_agent)
    if not agent_profile:
        raise HTTPException(status_code=502, detail=f"Leader 路由到了不存在的目标智能体：{plan.target_agent}")
    if not _is_agent_enabled(request, plan.target_agent):
        return _run_disabled_agent_response(request, plan.target_agent, leader_plan=plan)
    if not agent_profile.get("needRetrieval", True):
        return _run_direct_agent(request, agent_profile, leader_plan=plan)
    return _run_agent_without_local_retrieval(request, plan.target_agent, leader_plan=plan)


def _should_emit_generation_start(request: RagQueryRequest, agent_name: Optional[str], plan=None) -> bool:
    normalized = normalize_agent_name(agent_name)
    if normalized not in VISIBLE_GENERATION_AGENTS:
        return False
    if plan is not None and getattr(plan, "action", "") != "delegate_agent":
        return False
    if not _is_agent_enabled(request, normalized):
        return False
    return get_agent_profile(normalized) is not None


def _build_generation_start_payload(
    request: RagQueryRequest,
    plan=None,
    agent_name: Optional[str] = None,
) -> Dict[str, Any]:
    target_agent = normalize_agent_name(agent_name or getattr(plan, "target_agent", "")) or ""
    profile = get_agent_profile(target_agent) or {}
    runtime_config, config_prefix = _require_agent_runtime_config(request, target_agent, leader_plan=plan)
    model_metadata = _agent_model_metadata(runtime_config, config_prefix)
    session_id = str((request.metadata or {}).get("sessionId") or "")
    intent = getattr(plan, "intent", "") or profile.get("intent") or ""
    route_reason = getattr(plan, "route_reason", "") or profile.get("purpose") or ""
    answer_type = _answer_type_for_agent(target_agent)
    role = str(profile.get("role") or target_agent or "图片智能体")
    answer = f"已识别到你要生成图片，正在调用「{role}」处理中。你可以继续提问，生成完成后我会把结果更新到这里。"
    metadata = {
        "agentName": "leader_agent" if plan else target_agent,
        "targetAgent": target_agent,
        "executedAgent": target_agent,
        "intent": intent,
        "needRetrieval": False,
        "retrievalSkipped": True,
        "strategyLabel": "图片生成中",
        "executionMode": "visible_generation",
        "executionModeLabel": "图片生成中，允许继续对话",
        "generationStatus": "running",
        "generationVisible": True,
        "answerType": answer_type,
        "outputType": "image",
        "outputTypes": ["image"],
        "routeReason": route_reason,
        **model_metadata,
    }
    trace = []
    if plan:
        trace.append(RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(plan)).model_dump())
    trace.append({
        "stage": "agent_answer",
        "detail": {
            "agentName": target_agent,
            "generationStatus": "running",
            "message": "图片生成任务已开始",
            **model_metadata,
        },
    })
    return {
        "sessionId": session_id,
        "answer": answer,
        "answerType": answer_type,
        "outputType": "image",
        "outputTypes": ["image"],
        "outputMeta": {
            "generationStatus": "running",
            "choicePrompt": "",
            "followUpActions": [],
        },
        "attachments": [],
        "ragStrategy": "direct_agent",
        "agentName": target_agent,
        "searchKeyword": "",
        "matchedResults": [],
        "retrievalMeta": metadata,
        "trace": trace,
        "model": model_metadata.get("model") or "",
    }


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


def _tool_toggles_from_request(request: RagQueryRequest) -> Dict[str, Any]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    toggles = metadata.get("toolToggles")
    return toggles if isinstance(toggles, dict) else {}


def _is_tool_enabled(request: RagQueryRequest, tool_name: str) -> bool:
    normalized = str(tool_name or "").strip()
    if not normalized:
        return True
    toggles = _tool_toggles_from_request(request)
    if normalized not in toggles:
        return True
    return _parse_agent_enabled_value(toggles.get(normalized))


def _require_tool_enabled(request: RagQueryRequest, tool_name: str) -> None:
    if _is_tool_enabled(request, tool_name):
        return
    raise HTTPException(status_code=403, detail=f"工具 {tool_name} 已在后台关闭，Leader 本次不会调用。")


def _build_leader_callable_catalog(request: Optional[RagQueryRequest] = None) -> Dict[str, Any]:
    agents = [
        _leader_callable_agent_item(agent_name, request)
        for agent_name in AGENT_ORDER
        if agent_name != "leader_agent"
    ]
    agents = [item for item in agents if item]
    tools = [_leader_callable_tool_item(tool, request) for tool in LEADER_CALLABLE_TOOLS]
    content_tools = [_leader_callable_tool_item(tool, request) for tool in GENERATED_CONTENT_TOOLS]
    return {
        "routingActions": ["direct_answer", "delegate_agent", "call_tool"],
        "agents": agents,
        "tools": tools,
        "contentTools": content_tools,
        "summary": {
            "agentCount": len(agents),
            "enabledAgentCount": sum(1 for item in agents if item.get("enabled") is not False),
            "disabledAgentCount": sum(1 for item in agents if item.get("enabled") is False),
            "toolCount": len(tools),
            "enabledToolCount": sum(1 for item in tools if item.get("enabled") is not False),
            "disabledToolCount": sum(1 for item in tools if item.get("enabled") is False),
            "contentToolCount": len(content_tools),
            "enabledContentToolCount": sum(1 for item in content_tools if item.get("enabled") is not False),
        },
        "routingRule": "Leader 只能从 enabled=true 的 agents 和 tools 中选择；关闭项只允许展示为不可用，不允许继续调用或兜底改调。",
    }


def _leader_callable_agent_item(agent_name: str, request: Optional[RagQueryRequest]) -> Dict[str, Any]:
    profile = get_agent_profile(agent_name)
    if not profile:
        return {}
    enabled = True if request is None else _is_agent_enabled(request, agent_name)
    return {
        "name": agent_name,
        "role": profile.get("role") or agent_name,
        "category": _leader_agent_category(agent_name),
        "intent": profile.get("intent") or "",
        "purpose": profile.get("purpose") or "",
        "outputs": profile.get("outputs") or [],
        "requiredModelModalities": profile.get("requiredModelModalities") or ["text"],
        "enabled": enabled,
    }


def _leader_callable_tool_item(tool: Dict[str, Any], request: Optional[RagQueryRequest]) -> Dict[str, Any]:
    name = str(tool.get("name") or "").strip()
    enabled = True if request is None else _is_tool_enabled(request, name)
    return {
        **tool,
        "zhName": tool.get("zhName") or _tool_zh_name(name),
        "displayName": tool.get("displayName") or _tool_display_name(name),
        "enabled": enabled,
    }


def _leader_agent_category(agent_name: str) -> str:
    if agent_name == "profile_summary_agent":
        return "profile"
    if agent_name == "textbook_knowledge_agent":
        return "textbook"
    if agent_name.startswith("textbook_question_"):
        return "question_bank"
    if agent_name.startswith("meeting_"):
        return "meeting"
    if agent_name.startswith("ppt_"):
        return "ppt"
    if agent_name.startswith("diagram_") or agent_name in {"mind_map_agent", "architecture_prompt_agent"}:
        return "diagram"
    if agent_name == "image_agent":
        return "image"
    return "other"


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
        "dataStatus": profile_context.get("dataStatus"),
        "dataStatusText": profile_context.get("dataStatusText"),
        "dataSourceText": profile_context.get("dataSourceText"),
        "profileTags": profile_context.get("profileTags"),
        "strongDimensions": profile_context.get("strongDimensions"),
        "weakDimensions": profile_context.get("weakDimensions"),
        "advantageDimensions": profile_context.get("advantageDimensions"),
        "gapDimensions": profile_context.get("gapDimensions"),
        "aiSummary": profile_context.get("aiSummary"),
        "strengthSummary": profile_context.get("strengthSummary"),
        "weaknessSummary": profile_context.get("weaknessSummary"),
        "improvementSuggestions": profile_context.get("improvementSuggestions"),
        "confidenceNotes": profile_context.get("confidenceNotes"),
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


def _output_preference_hints_from_request(request: RagQueryRequest) -> Dict[str, Any]:
    return _output_preference_hints(_profile_context_from_request(request))


def _output_preference_hints(profile_context: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if not isinstance(profile_context, dict):
        return {}
    hints = profile_context.get("outputPreferenceHints")
    return hints if isinstance(hints, dict) else {}


def _run_specialist_agent_with_bound_model(
    request: RagQueryRequest,
    agent_name: str,
    input_text: str,
    evidence: List[Dict[str, Any]],
    leader_plan=None,
    chat_service=None,
) -> Tuple[str, Dict[str, Any]]:
    runtime_config, config_prefix = _require_agent_runtime_config(request, agent_name, leader_plan=leader_plan)
    token = set_active_llm_config(runtime_config)
    try:
        answer = run_specialist_agent(agent_name, input_text, evidence, chat_service=chat_service)
    except AgentExecutionError:
        raise
    except Exception as exc:
        _raise_agent_execution_error(
            exc,
            agent_name,
            leader_plan=leader_plan,
            runtime_config=runtime_config,
            config_prefix=config_prefix,
        )
    finally:
        reset_active_llm_config(token)
    return answer, _agent_model_metadata(runtime_config, config_prefix)


def _require_agent_runtime_config(
    request: RagQueryRequest,
    agent_name: str,
    leader_plan=None,
):
    normalized_agent = normalize_agent_name(agent_name) or (agent_name or "").strip()
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    configs = metadata.get("agentModelConfigs") if isinstance(metadata.get("agentModelConfigs"), dict) else {}
    config = configs.get(normalized_agent) if isinstance(configs, dict) else None
    display_name = _agent_display_name(normalized_agent)
    if not isinstance(config, dict):
        raise AgentExecutionError(
            message=f"{display_name} 未绑定模型配置，请到后台 AI 模块 > 智能体设置为它绑定模型。",
            agent_name=normalized_agent,
            intent=getattr(leader_plan, "intent", "") or "",
            stage="agent_model_config",
            route_reason=getattr(leader_plan, "route_reason", "") or "",
            status_code=400,
        )

    config_prefix = str(config.get("configPrefix") or "").strip()
    provider = str(config.get("provider") or "").strip()
    base_url = str(config.get("baseUrl") or config.get("base_url") or "").strip()
    api_key = str(config.get("apiKey") or config.get("api_key") or "").strip()
    model = str(config.get("model") or "").strip()
    missing = []
    if not provider:
        missing.append("provider")
    if not base_url:
        missing.append("base-url")
    if not api_key:
        missing.append("api-key")
    if not model:
        missing.append("model")
    if missing:
        prefix_text = f"（{config_prefix}）" if config_prefix else ""
        raise AgentExecutionError(
            message=f"{display_name} 的模型配置不完整{prefix_text}：缺少 {'、'.join(missing)}。",
            agent_name=normalized_agent,
            intent=getattr(leader_plan, "intent", "") or "",
            stage="agent_model_config",
            route_reason=getattr(leader_plan, "route_reason", "") or "",
            status_code=400,
            model_provider=provider,
            model=model,
            base_url=base_url,
            model_config_prefix=config_prefix,
        )

    runtime_config = build_llm_runtime_config(
        provider=provider,
        base_url=base_url,
        api_key=api_key,
        model=model,
    )
    if runtime_config is None:
        raise AgentExecutionError(
            message=f"{display_name} 未收到有效模型配置，请检查后台智能体设置。",
            agent_name=normalized_agent,
            intent=getattr(leader_plan, "intent", "") or "",
            stage="agent_model_config",
            route_reason=getattr(leader_plan, "route_reason", "") or "",
            status_code=400,
            model_config_prefix=config_prefix,
        )
    return runtime_config, config_prefix


def _agent_model_metadata(runtime_config, config_prefix: str = "") -> Dict[str, Any]:
    return {
        "modelProvider": getattr(runtime_config, "provider", "") or "",
        "model": getattr(runtime_config, "model", "") or "",
        "modelBaseUrl": getattr(runtime_config, "base_url", "") or "",
        "modelConfigPrefix": config_prefix or "",
    }


def _agent_display_name(agent_name: str) -> str:
    profile = get_agent_profile(agent_name) or {}
    return str(profile.get("role") or agent_name or "专业智能体")


def _run_agent_without_local_retrieval(
    request: RagQueryRequest,
    active_agent: str,
    leader_plan=None,
) -> RagQueryResponse:
    profile_evidence = _profile_evidence_from_request(request)
    answer, model_metadata = _run_specialist_agent_with_bound_model(
        request,
        active_agent,
        request.input,
        profile_evidence,
        leader_plan=leader_plan,
    )
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
            **model_metadata,
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
        "outputPreferenceHints": _output_preference_hints_from_request(request),
        "toolToggles": _tool_toggles_from_request(request),
        **model_metadata,
    }
    metadata.update(_context_metadata_from_request(request))
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
        "outputPreferenceHints": _output_preference_hints(profile_context),
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
    answer, model_metadata = _run_specialist_agent_with_bound_model(
        request,
        agent_name,
        request.input,
        profile_evidence,
        leader_plan=leader_plan,
    )
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
        "outputPreferenceHints": _output_preference_hints_from_request(request),
        "toolToggles": _tool_toggles_from_request(request),
        **model_metadata,
    }
    metadata.update(_context_metadata_from_request(request))
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


def _raise_agent_execution_error(
    exc: Exception,
    agent_name: str,
    leader_plan=None,
    runtime_config=None,
    config_prefix: str = "",
) -> None:
    raw_message = _exception_message(exc)
    friendly_message = _friendly_agent_failure_message(raw_message, agent_name)
    raise AgentExecutionError(
        message=friendly_message,
        agent_name=agent_name,
        intent=getattr(leader_plan, "intent", "") or "",
        route_reason=getattr(leader_plan, "route_reason", "") or "",
        status_code=getattr(exc, "status_code", 500) or 500,
        raw_message=raw_message,
        model_provider=getattr(runtime_config, "provider", "") or "",
        model=getattr(runtime_config, "model", "") or "",
        base_url=getattr(runtime_config, "base_url", "") or "",
        model_config_prefix=config_prefix,
    ) from exc


def _exception_message(exc: Exception) -> str:
    detail = getattr(exc, "detail", None)
    if detail:
        return str(detail)
    return str(exc)


def _friendly_agent_failure_message(raw_message: str, agent_name: str = "") -> str:
    message = str(raw_message or "").strip()
    lowered = message.lower()
    is_image_agent = agent_name in {
        "image_agent",
        "diagram_mind_map_agent",
        "diagram_architecture_agent",
        "diagram_flowchart_agent",
        "diagram_activity_agent",
        "ppt_image_agent",
    }
    if is_image_agent and "api.deepseek.com" in lowered and "services/aigc" in lowered:
        return (
            "图片模型服务配置不匹配：当前把 Qwen/DashScope 图片生成接口请求发到了 DeepSeek 地址。"
            "请给该图片智能体绑定 ai.service.image.* 的 Qwen/DashScope 图片模型配置。"
        )
    if is_image_agent and "404" in lowered and "services/aigc" in lowered:
        return "图片生成接口返回 404，通常是图片模型 base-url 或模型服务商配置不正确。"
    if is_image_agent and ("未传入图片 base url" in lowered or "未传入图片 api key" in lowered or "未传入图片模型 id" in lowered):
        return "图片模型配置不完整，请检查图片模型的 base-url、api-key 和 model。"
    return message or "智能体执行失败"


def _build_stream_error_payload(
    request: RagQueryRequest,
    exc: Exception,
    llm_config,
) -> Dict[str, Any]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    session_id = str(metadata.get("sessionId") or "")
    if isinstance(exc, AgentExecutionError):
        agent_name = exc.agent_name or "leader_agent"
        intent = exc.intent
        route_reason = exc.route_reason
        message = exc.message
        raw_message = exc.raw_message
        status_code = exc.status_code
        stage = exc.stage
        is_specialist_failure = bool(agent_name and agent_name != "leader_agent")
        model_provider = exc.model_provider or ("" if is_specialist_failure else getattr(llm_config, "provider", "") or "")
        model = exc.model or ("" if is_specialist_failure else getattr(llm_config, "model", "") or "")
        base_url = exc.base_url or ("" if is_specialist_failure else getattr(llm_config, "base_url", "") or "")
        model_config_prefix = exc.model_config_prefix or ""
    else:
        agent_name = normalize_agent_name(request.agentName) or "leader_agent"
        intent = ""
        route_reason = ""
        message = _exception_message(exc)
        raw_message = message
        status_code = getattr(exc, "status_code", 500) or 500
        stage = "error"
        model_provider = getattr(llm_config, "provider", "") or ""
        model = getattr(llm_config, "model", "") or ""
        base_url = getattr(llm_config, "base_url", "") or ""
        model_config_prefix = ""
    retrieval_meta = {
        "agentName": "leader_agent" if request.agentName in {"", None, "leader_agent"} else agent_name,
        "targetAgent": agent_name,
        "executedAgent": agent_name,
        "failedAgent": agent_name,
        "intent": intent,
        "routeReason": route_reason,
        "executionMode": "agent_failed" if agent_name != "leader_agent" else "leader_failed",
        "executionModeLabel": "专业智能体执行失败" if agent_name != "leader_agent" else "Leader 执行失败",
        "failureStage": stage,
        "failureReason": message,
        "rawFailureReason": raw_message,
        "modelProvider": model_provider,
        "model": model,
        "baseUrl": base_url,
        "modelConfigPrefix": model_config_prefix,
    }
    trace = []
    if route_reason or intent:
        trace.append({
            "stage": "leader_route",
            "detail": {
                "intent": intent,
                "targetAgent": agent_name,
                "needRetrieval": False,
                "routeReason": route_reason,
            },
        })
    trace.append({
        "stage": stage,
        "detail": {
            "agentName": agent_name,
            "message": message,
            "rawMessage": raw_message,
            "statusCode": status_code,
            "modelProvider": model_provider,
            "model": model,
            "modelConfigPrefix": model_config_prefix,
        },
    })
    return {
        "sessionId": session_id,
        "message": message,
        "rawMessage": raw_message,
        "statusCode": status_code,
        "agentName": agent_name,
        "failedAgent": agent_name,
        "intent": intent,
        "stage": stage,
        "routeReason": route_reason,
        "modelProvider": model_provider,
        "model": model,
        "modelConfigPrefix": model_config_prefix,
        "retrievalMeta": retrieval_meta,
        "trace": trace,
    }


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
        "toolDisplayName": _tool_display_name(leader_plan.tool_name),
        "routeReason": leader_plan.route_reason,
        "strategyLabel": _strategy_label("text_to_sql"),
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用 Text-to-SQL 接口",
        "answerType": "tool_result",
        "toolToggles": _tool_toggles_from_request(request),
    })
    metadata.update(_context_metadata_from_request(request))
    trace = [
        RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
        RagTraceResponse(stage="generate_sql", detail={"readonly": bool(result.sql), "sql": result.sql, "error": result.error}),
        RagTraceResponse(stage="tool_call", detail={"toolName": "text_to_sql", "toolDisplayName": _tool_display_name("text_to_sql"), "strategy": "text_to_sql"}),
    ]
    return _decorate_output_response(RagQueryResponse(
        strategy="text_to_sql",
        answer=answer,
        answerType="tool_result",
        documents=[],
        trace=trace,
        metadata=metadata,
    ))


def _run_generated_export_tool(request: RagQueryRequest, leader_plan) -> RagQueryResponse:
    metadata = {
        "agentName": "leader_agent",
        "targetAgent": "generated_export_tools",
        "executedAgent": "generated_export_tools",
        "intent": leader_plan.intent,
        "needRetrieval": False,
        "retrievalSkipped": True,
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "toolName": leader_plan.tool_name,
        "toolDisplayName": _tool_display_name(leader_plan.tool_name),
        "routeReason": leader_plan.route_reason,
        "strategyLabel": "内容导出工具",
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用内容导出工具",
        "answerType": "document_export",
        "requestedOutputType": "document",
        "allowGeneratedExportTool": True,
        "toolToggles": _tool_toggles_from_request(request),
    }
    metadata.update(_context_metadata_from_request(request))
    parsed_input = _try_parse_json_object(request.input)
    export_answer_type = "question_bank" if isinstance(parsed_input.get("questions"), list) else "markdown"
    export_result = export_generated_answer(request.input, export_answer_type, metadata)
    if not export_result.attachments:
        reason = export_result.diagnostics.get("reason") if isinstance(export_result.diagnostics, dict) else ""
        if reason == "tool_disabled":
            disabled_tool = export_result.diagnostics.get("disabledTool") or "generated_export_tools"
            raise HTTPException(status_code=403, detail=f"工具 {_tool_display_name(disabled_tool)} 已在后台关闭，Leader 本次不会调用。")
        if reason == "no_enabled_export_format":
            raise HTTPException(status_code=403, detail="当前没有开启可生成的附件格式，Leader 本次不会调用内容整理工具。")
        raise HTTPException(status_code=400, detail="当前内容无法导出，请提供 Markdown 文本或标准题库 JSON")
    metadata["generatedExports"] = export_result.diagnostics
    metadata.pop("allowGeneratedExportTool", None)
    formats = "、".join(item.get("ext", "").upper() for item in export_result.attachments if item.get("ext"))
    answer = f"已按文件形式整理完成，生成附件格式：{formats or '文件'}。"
    return _decorate_output_response(RagQueryResponse(
        strategy="generated_export_tools",
        answer=answer,
        answerType="document_export",
        documents=[],
        trace=[
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
            RagTraceResponse(stage="tool_call", detail={"toolName": leader_plan.tool_name, "toolDisplayName": _tool_display_name(leader_plan.tool_name), **export_result.diagnostics}),
        ],
        metadata=metadata,
        attachments=export_result.attachments,
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


def _run_disabled_tool_response(request: RagQueryRequest, tool_name: str, leader_plan) -> RagQueryResponse:
    normalized = str(tool_name or "").strip()
    display_name = _tool_display_name(normalized) or "目标工具"
    answer = (
        f"Leader 已识别到需要调用「{display_name}」；"
        "但后台工具开关当前为关闭，所以本次已跳过，没有继续调用，也没有改用其他兜底能力。"
    )
    metadata = {
        "agentName": "leader_agent",
        "targetAgent": normalized,
        "executedAgent": None,
        "disabledTool": normalized,
        "toolDisabled": True,
        "intent": leader_plan.intent,
        "needRetrieval": False,
        "retrievalSkipped": True,
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "toolName": normalized,
        "toolDisplayName": display_name,
        "routeReason": leader_plan.route_reason,
        "strategyLabel": "工具已关闭，跳过执行",
        "executionMode": "leader_skipped_disabled_tool",
        "executionModeLabel": "Leader 跳过已关闭工具",
        "answerType": "text",
        "toolToggles": _tool_toggles_from_request(request),
    }
    metadata.update(_context_metadata_from_request(request))
    return _decorate_output_response(RagQueryResponse(
        strategy="tool_disabled",
        answer=answer,
        answerType="text",
        documents=[],
        trace=[
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
            RagTraceResponse(stage="tool_call", detail={
                "toolName": normalized,
                "toolDisplayName": display_name,
                "reason": "tool_disabled",
                "message": "后台工具开关关闭，跳过执行",
            }),
        ],
        metadata=metadata,
    ))


def _run_service_tool(request: RagQueryRequest, authorization: str, leader_plan) -> RagQueryResponse:
    tool_name = leader_plan.tool_name
    tool_display_name = _tool_display_name(tool_name)
    planning_answer = str(getattr(leader_plan, "answer", "") or "").strip()
    answer_type = "service_tool_result"
    results, cache_meta = data_store.search_service_tool_with_meta(authorization, tool_name, request.input)
    tool_cache = cache_meta.get("toolCache", {}) if isinstance(cache_meta, dict) else {}
    retrieval_meta = {
        "javaBackendCount": len(results),
        "documentCount": len(results),
        "toolCache": tool_cache,
        "toolCacheHit": bool(tool_cache.get("cacheHit")),
        "toolCachePartialHit": bool(tool_cache.get("partialHit")),
        "toolCacheRequestCount": int(tool_cache.get("requestCount") or 0),
        "toolCacheHitCount": int(tool_cache.get("hitCount") or 0),
        "toolCacheMissCount": int(tool_cache.get("missCount") or 0),
    }
    documents = [_tool_result_to_document(item, index) for index, item in enumerate(results, start=1)]
    summary_error = ""
    summarized_by_model = False
    try:
        answer = leader_agent.summarize_tool_result(
            input_text=request.input,
            plan=leader_plan,
            tool_display_name=tool_display_name,
            tool_results=results,
        )
        summarized_by_model = bool(answer)
    except Exception as exc:
        logger.warning("leader tool result summarization failed tool=%s error=%s", tool_name, exc)
        answer = ""
        summary_error = str(exc)
    if not answer and results:
        answer = _format_service_tool_answer(tool_name, results)
    elif not answer:
        answer = (
            f"Leader 已识别为「{_tool_zh_name(tool_name)}」，并调用了对应 Java 后端接口；"
            "但当前没有返回可展示的数据。请确认 Java 服务、登录态或该业务数据是否正常。"
        )
    metadata = {
        "agentName": "leader_agent",
        "targetAgent": tool_name,
        "executedAgent": tool_name,
        "intent": leader_plan.intent,
        "needRetrieval": False,
        "retrievalSkipped": True,
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "toolName": tool_name,
        "toolDisplayName": tool_display_name,
        "planningAnswer": planning_answer,
        "routeReason": leader_plan.route_reason,
        "strategyLabel": _strategy_label(tool_name),
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用 Java 后端接口并整理结果",
        "answerType": answer_type,
        "toolResultSummarized": summarized_by_model,
        "toolToggles": _tool_toggles_from_request(request),
        **retrieval_meta,
    }
    metadata.update(_context_metadata_from_request(request))
    if summary_error:
        metadata["toolResultSummaryError"] = summary_error
    return _decorate_output_response(RagQueryResponse(
        strategy=tool_name,
        answer=answer,
        answerType=answer_type,
        documents=documents,
        trace=[
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
            RagTraceResponse(stage="tool_call", detail={
                "toolName": tool_name,
                "toolDisplayName": tool_display_name,
                "planningAnswer": planning_answer,
                **retrieval_meta,
            }),
            RagTraceResponse(stage="tool_result_summary", detail={
                "agentName": "leader_agent",
                "toolName": tool_name,
                "toolDisplayName": tool_display_name,
                "summarizedByModel": summarized_by_model,
                "resultCount": len(results),
                **({"error": summary_error} if summary_error else {}),
            }),
        ],
        metadata=metadata,
    ))


def _format_service_tool_answer(tool_name: str, results: List[Dict[str, Any]]) -> str:
    title = _tool_zh_name(tool_name)
    lines = [f"## {title}结果"]
    for item in results[:8]:
        lines.append(f"- {_format_service_tool_item(item)}")
    if len(results) > 8:
        lines.append(f"- 另有 {len(results) - 8} 条结果未展开。")
    return "\n".join(lines)


def _format_service_tool_item(item: Dict[str, Any]) -> str:
    item_type = str(item.get("type") or "")
    name = item.get("name") or item.get("courseName") or item.get("title") or "未命名"
    if item_type == "course_schedule":
        return (
            f"{item.get('weekdayText') or ''} {item.get('classSessions') or ''}：{name}"
            f"（{item.get('location') or '地点待补充'}，{item.get('teacherName') or '教师待补充'}）"
        )
    if item_type == "course_schedule_summary":
        schedule_items = item.get("scheduleItems") if isinstance(item.get("scheduleItems"), list) else []
        schedule_text = "；".join(str(value) for value in schedule_items[:3] if str(value or "").strip())
        if len(schedule_items) > 3:
            schedule_text += f"；另有 {len(schedule_items) - 3} 次安排"
        suffix = _join_non_empty(item.get("teacherName"), item.get("assessmentType"), f"{item.get('credit')} 学分" if item.get("credit") not in (None, "") else "")
        detail = f"（{suffix}）" if suffix else ""
        semester_label = str(item.get("semesterLabel") or "").strip()
        prefix = f"[{semester_label}] " if semester_label else ""
        return f"{prefix}{name}{detail}" + (f"：{schedule_text}" if schedule_text else "")
    if item_type == "activity":
        time_text = _join_non_empty(item.get("startTime"), item.get("endTime"), separator=" - ")
        suffix = _join_non_empty(time_text, item.get("location"), item.get("organizerName"))
        return f"{name}" + (f"（{suffix}）" if suffix else "")
    if item_type == "meeting":
        suffix = _join_non_empty(item.get("scheduledStartTime"), item.get("status"), item.get("roomCode"))
        return f"{name}" + (f"（{suffix}）" if suffix else "")
    if item_type in {"restaurant", "stall", "dish", "coupon"}:
        suffix = _join_non_empty(item.get("location"), item.get("category"), item.get("avgPrice") or item.get("price") or item.get("pickupLocation"))
        return f"{name}" + (f"（{suffix}）" if suffix else "")
    if item_type in {"facility", "facility_location"}:
        suffix = _join_non_empty(item.get("location"), item.get("facilityTypeName"), _coordinate_text(item))
        return f"{name}" + (f"（{suffix}）" if suffix else "")
    if item_type == "secondhand_item":
        suffix = _join_non_empty(item.get("price"), item.get("condition"), item.get("sellerName"), item.get("location"))
        return f"{name}" + (f"（{suffix}）" if suffix else "")
    return str(name)


def _join_non_empty(*values: Any, separator: str = "，") -> str:
    parts = [str(value).strip() for value in values if value is not None and str(value).strip()]
    return separator.join(parts)


def _coordinate_text(item: Dict[str, Any]) -> str:
    longitude = item.get("longitude")
    latitude = item.get("latitude")
    if longitude is None or latitude is None:
        return ""
    return f"{longitude},{latitude}"


def _decorate_output_response(response: RagQueryResponse) -> RagQueryResponse:
    if response.metadata is None:
        response.metadata = {}
    existing_attachments = response.attachments if isinstance(response.attachments, list) else []
    extracted_attachments = _extract_response_attachments(response.answer)
    export_result = export_generated_answer(response.answer, response.answerType, response.metadata)
    attachments = _merge_attachments(existing_attachments, extracted_attachments, export_result.attachments)
    export_diagnostics = export_result.diagnostics
    if not export_result.attachments and isinstance(response.metadata.get("generatedExports"), dict):
        export_diagnostics = response.metadata["generatedExports"]
    output_types = _infer_output_types(response.answerType, response.metadata, attachments)
    follow_up_actions = _follow_up_actions_for_output(response.answerType, response.metadata, output_types)
    response.attachments = attachments
    response.outputTypes = output_types
    response.outputType = output_types[0] if output_types else "text"
    response.outputMeta = {
        "pushStrategy": _push_strategy_for_output(response.answerType, response.metadata, output_types),
        "attachmentCount": len(attachments),
        "displayPolicy": "App 会话页优先展示结构化附件；文本中出现图片/文档链接时也会转成卡片。",
        "generatedExports": export_diagnostics,
        "followUpActions": follow_up_actions,
        "choicePrompt": _choice_prompt_for_output(response.metadata, output_types, follow_up_actions),
        "outputPreferenceHints": (response.metadata or {}).get("outputPreferenceHints") or {},
    }
    response.metadata["outputType"] = response.outputType
    response.metadata["outputTypes"] = output_types
    response.metadata["attachmentCount"] = len(attachments)
    response.metadata["pushStrategy"] = response.outputMeta["pushStrategy"]
    response.metadata["generatedExports"] = export_diagnostics
    return response


def _merge_attachments(*groups: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    normalized: List[Dict[str, Any]] = []
    seen = set()
    for group in groups:
        for item in group or []:
            if not item:
                continue
            url = str(item.get("url") or "").strip()
            if not url or url in seen:
                continue
            seen.add(url)
            normalized.append(item)
    return normalized


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
            "trigger": "用户要求导出、转换、下载、生成 Word/PDF/PPT/Excel 文档，或知识/题库内容更适合文件阅读时触发。",
            "agent": agent or "generated_export_tools",
            "display": "以文档卡片推送到会话页，支持点击打开；不支持打开时复制链接。",
        }
    return {
        "pushType": "text",
        "trigger": "普通问答、知识点解释、会议总结、题库 JSON 和策略说明默认触发。",
        "agent": agent or "leader_agent",
        "display": "以文本消息展示。",
    }


def _follow_up_actions_for_output(answer_type: str, metadata: Dict[str, Any], output_types: List[str]) -> List[Dict[str, Any]]:
    metadata = metadata or {}
    agent = str(metadata.get("executedAgent") or metadata.get("targetAgent") or "").strip()
    hints = metadata.get("outputPreferenceHints") if isinstance(metadata.get("outputPreferenceHints"), dict) else {}
    preferred_format = str(hints.get("preferredFormat") or "").strip()
    confidence_level = str(hints.get("confidenceLevel") or "").strip()
    convertible_agents = {
        "leader_agent",
        "textbook_knowledge_agent",
        "meeting_summary_agent",
        "meeting_resource_recommendation_agent",
        "ppt_outline_agent",
        "ppt_layout_agent",
        "diagram_mind_map_agent",
        "diagram_flowchart_agent",
        "diagram_activity_agent",
        "diagram_architecture_agent",
    }

    actions: List[Dict[str, Any]] = []
    if "document" in output_types:
        actions.append(_follow_up_action("再来图片版", "请在当前内容基础上，再生成图片形式或图解版。", "image", "secondary"))
    elif "image" in output_types:
        actions.append(_follow_up_action("生成文件版", "请在当前内容基础上，整理成文件或文档形式。", "document", "secondary"))
    elif agent in convertible_agents or str(answer_type or "").startswith("mermaid"):
        if preferred_format == "document" and confidence_level in {"high", "medium"}:
            actions.append(_follow_up_action("生成文件版", "请把刚才的内容整理成文件或文档形式。", "document", "primary"))
            actions.append(_follow_up_action("再来图片版", "请把刚才的内容再生成图片形式或图解版。", "image", "secondary"))
        elif preferred_format == "image" and confidence_level in {"high", "medium"}:
            actions.append(_follow_up_action("生成图片版", "请把刚才的内容生成图片形式或图解版。", "image", "primary"))
            actions.append(_follow_up_action("再来文件版", "请把刚才的内容整理成文件或文档形式。", "document", "secondary"))
        else:
            actions.append(_follow_up_action("生成文件版", "请把刚才的内容整理成文件或文档形式。", "document", "primary"))
            actions.append(_follow_up_action("生成图片版", "请把刚才的内容生成图片形式或图解版。", "image", "primary"))

    return actions[:3]


def _follow_up_action(label: str, prompt: str, output_type: str, style: str) -> Dict[str, Any]:
    return {
        "label": label,
        "prompt": prompt,
        "outputType": output_type,
        "style": style,
        "rememberPreference": True,
    }


def _choice_prompt_for_output(metadata: Dict[str, Any], output_types: List[str], follow_up_actions: List[Dict[str, Any]]) -> str:
    if not follow_up_actions:
        return ""
    hints = metadata.get("outputPreferenceHints") if isinstance((metadata or {}).get("outputPreferenceHints"), dict) else {}
    preferred_format = str(hints.get("preferredFormat") or "").strip()
    confidence_level = str(hints.get("confidenceLevel") or "").strip()
    if "document" in output_types:
        return "已按文件形式推送；如果还需要图片形式，可以继续生成图片版。"
    if "image" in output_types:
        return "已按图片形式推送；如果还需要文件形式，可以继续生成文件版。"
    if preferred_format == "document" and confidence_level in {"high", "medium"}:
        return "根据你最近的选择，我会优先给文件版；也可以补一份图片版。"
    if preferred_format == "image" and confidence_level in {"high", "medium"}:
        return "根据你最近的选择，我会优先给图片版；也可以补一份文件版。"
    return "这类内容可以继续做成文件版或图片版，你可以选一种。"


def _extract_response_attachments(answer: str) -> List[Dict[str, Any]]:
    content = str(answer or "")
    attachments: List[Dict[str, Any]] = []
    parsed = _try_parse_json_object(content)
    if parsed:
        attachments.extend(_attachments_from_json_payload(parsed))

    markdown_pattern = re.compile(
        r"!?\[([^\]]+)\]\(((?:https?://|/uploads/)[^\s\"'<>，。！？；、)]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?|xlsx?|csv|md|mmd|zip)(?:\?[^\s\"'<>，。！？；、)]*)?)\)",
        re.IGNORECASE,
    )
    for match in markdown_pattern.finditer(content):
        attachments.append(_build_attachment(match.group(2), match.group(1), ""))

    plain_text = markdown_pattern.sub("", content)
    url_pattern = re.compile(
        r"(?:https?://|/uploads/)[^\s\"'<>，。！？；、]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?|xlsx?|csv|md|mmd|zip)(?:\?[^\s\"'<>，。！？；、]*)?",
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
        str(item.get("semesterLabel") or "").strip(),
        str(item.get("name") or item.get("courseName") or item.get("title") or item.get("content") or "").strip(),
        str(item.get("location") or "").strip(),
        str(item.get("teacherName") or "").strip(),
        str(item.get("classSessions") or "").strip(),
        " ".join(str(value) for value in item.get("scheduleItems", [])[:3]) if isinstance(item.get("scheduleItems"), list) else "",
        str(item.get("description") or "").strip(),
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
        "toolDisplayName": _tool_display_name(plan.tool_name) if getattr(plan, "tool_name", "") else "",
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
        "java_schedule_api": "课表查询工具",
        "java_activity_api": "活动查询工具",
        "java_meeting_api": "会议查询工具",
        "java_canteen_api": "食堂餐饮查询工具",
        "java_facility_api": "设施位置查询工具",
        "java_secondhand_api": "旧物查询工具",
        "generated_export_tools": "内容导出工具",
        "text_to_sql": "Text-to-SQL",
    }
    if strategy_name in custom_labels:
        return custom_labels[strategy_name]
    return strategy_name or "直接处理"


def _tool_zh_name(tool_name: str) -> str:
    labels = {
        "text_to_sql": "结构化查询工具",
        "java_schedule_api": "课表查询工具",
        "java_activity_api": "活动查询工具",
        "java_meeting_api": "会议查询工具",
        "java_canteen_api": "食堂餐饮查询工具",
        "java_facility_api": "设施位置查询工具",
        "java_secondhand_api": "旧物查询工具",
        "generated_export_tools": "内容整理工具",
        "markdown_export_tool": "Markdown 导出工具",
        "docx_export_tool": "Word 导出工具",
        "excel_export_tool": "Excel 导出工具",
        "content_archive_tool": "附件打包工具",
        "diagram_source_export_tool": "图表源码导出工具",
    }
    return labels.get(str(tool_name or "").strip(), str(tool_name or "").strip())


def _tool_display_name(tool_name: str) -> str:
    name = str(tool_name or "").strip()
    if not name:
        return ""
    zh_name = _tool_zh_name(name)
    return f"{zh_name}（{name}）" if zh_name and zh_name != name else name


def _answer_type_for_agent(agent_name: str) -> str:
    mapping = {
        "leader_agent": "text",
        "profile_summary_agent": "profile_summary_json",
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
