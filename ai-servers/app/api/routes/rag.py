import asyncio
import base64
import hashlib
import json
import os
import re
import threading
import time
from typing import Any, Dict, List, Optional, Tuple

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.models.schemas import (
    RagDocumentResponse,
    RagQueryRequest,
    RagQueryResponse,
    RagTraceResponse,
)
from app.model_providers.multimodal import (
    append_attachment_references_to_text,
    append_image_references_to_text,
    collect_request_image_references,
    extract_image_references,
)
from app.model_providers.runtime_config import build_llm_runtime_config, reset_active_llm_config, set_active_llm_config
from app.observability.langfuse import observe_request, settings_from_headers, use_settings
from app.multi_agents.catalog import (
    INTERNAL_ONLY_AGENT_NAMES,
    LEADER_CALLABLE_AGENT_ORDER,
    get_agent_catalog,
    get_agent_detail,
    get_agent_profile,
    normalize_agent_name,
    normalize_leader_request_agent,
    update_agent_example_input,
)
from app.multi_agents.leader_agent.agent import LeaderPlan, leader_agent
from app.multi_agents.question_bank_schema import review_question_bank_payload
from app.multi_agents.runner import run_specialist_agent
from app.multi_agents.textbook_knowledge_agent import resolve_knowledge_source_mode
from app.multi_agents.tool_intent_router_agent import TOOL_INTENT_ROUTER_TOOL, tool_intent_router_agent
from app.services.tool_index import tool_index
from app.services.image_stitching import ImageStitchingError, StitchImage, collect_stitch_images, stitch_images
from app.services.file_format_registry import get_detectable_extensions, get_file_format_registry, get_output_aliases, resolve_file_format
from app.learning_workflow import (
    LearningWorkflowRequest,
    export_learning_resources,
    run_learning_workflow,
)
from app.rag.document_conversion import DocxConversionError, FileContentExtractionError, PdfConversionError, PptConversionError, convert_docx_to_pdf, convert_docx_to_ppt, convert_pdf, convert_ppt_to_docx, convert_ppt_to_pdf, export_generated_answer, export_text_to_file, extract_file_content, materialize_generated_image_answer
from app.rag.document_conversion.generated_exporter import GeneratedExportAccessError, open_generated_export
from app.rag.structured.text_to_sql import TextToSqlService
from app.services.assistant_resource_builder import (
    build_learning_resource_bundle,
    finalize_assistant_response,
)
from app.services.data_store import data_store
from app.security.internal_auth import require_internal_token
from app.safety.learning_content_guard import (
    LearningContentGuardError,
    sanitize_learning_references,
)
from app.utils.logger import get_logger
from app.utils.sse import build_sse, chunk_answer
from app.utils.text_utils import build_session_token, normalize_text

router = APIRouter(
    prefix="/internal/rag",
    tags=["internal-rag"],
    dependencies=[Depends(require_internal_token)],
)
export_router = APIRouter(prefix="/internal/rag", tags=["internal-rag-exports"])
logger = get_logger("api.rag")

DEFAULT_RAG_INPUT_MAX_LENGTH = 4_000
QUESTION_GENERATION_INPUT_MAX_LENGTH = 210_000

VISUAL_GENERATION_TOOL_CONFIG = {
    "generate_image_tool": {
        "zhName": "通用图片生成工具",
        "purpose": "根据用户明确提供的图片描述生成图片。",
        "trigger": "用户明确要求生成普通图片、插图、封面、海报或图片素材。",
        "promptAgent": "",
    },
    "generate_mind_map_image_tool": {
        "zhName": "思维导图图片生成工具",
        "purpose": "先生成思维导图专用提示词，再统一调用图片生成入口。",
        "trigger": "用户要求生成思维导图或脑图图片。",
        "promptAgent": "mind_map_agent",
    },
    "generate_flowchart_image_tool": {
        "zhName": "流程图图片生成工具",
        "purpose": "先生成流程图专用提示词，再统一调用图片生成入口。",
        "trigger": "用户要求生成流程图、算法流程或步骤流程图片。",
        "promptAgent": "diagram_flowchart_prompt_agent",
    },
    "generate_activity_image_tool": {
        "zhName": "活动图图片生成工具",
        "purpose": "先生成活动图专用提示词，再统一调用图片生成入口。",
        "trigger": "用户要求生成活动图、泳道图或角色任务流程图片。",
        "promptAgent": "diagram_activity_prompt_agent",
    },
    "generate_architecture_image_tool": {
        "zhName": "架构图图片生成工具",
        "purpose": "先生成架构图专用提示词，再统一调用图片生成入口。",
        "trigger": "用户要求生成系统架构图、技术架构图或模块依赖图。",
        "promptAgent": "architecture_prompt_agent",
    },
    "generate_knowledge_graph_image_tool": {
        "zhName": "知识图谱图片生成工具",
        "purpose": "先生成知识图谱专用提示词，再统一调用图片生成入口。",
        "trigger": "用户要求生成知识图谱、实体关系图或概念关系图。",
        "promptAgent": "knowledge_graph_prompt_agent",
    },
    "generate_ppt_image_tool": {
        "zhName": "PPT 配图生成工具",
        "purpose": "先生成 PPT 配图专用提示词，再统一调用图片生成入口。",
        "trigger": "用户要求生成 PPT 封面、课件配图或页面插图。",
        "promptAgent": "ppt_image_agent",
    },
}

VISUAL_GENERATION_TOOLS = [
    {
        "name": tool_name,
        "zhName": config["zhName"],
        "displayName": f"{config['zhName']}（{tool_name}）",
        "category": "visual_generation",
        "purpose": config["purpose"],
        "trigger": config["trigger"],
        "outputs": ["image"],
        "status": "implemented",
        "configurable": True,
    }
    for tool_name, config in VISUAL_GENERATION_TOOL_CONFIG.items()
]
IMAGE_STITCHING_TOOL = {
    "name": "image_stitching_tool",
    "zhName": "图片拼接工具",
    "displayName": "图片拼接工具（image_stitching_tool）",
    "category": "visual_generation",
    "purpose": "将多张图片按上传顺序，以最多三列的自适应网格拼接，并在每张图片左侧标注序号。",
    "trigger": "用户明确要求把两张或多张图片拼接或合并。",
    "outputs": ["image"],
    "status": "implemented",
    "configurable": True,
}
VISUAL_GENERATION_TOOLS.insert(1, IMAGE_STITCHING_TOOL)
VISUAL_GENERATION_TOOL_NAMES = frozenset(VISUAL_GENERATION_TOOL_CONFIG)
IMAGE_RECOGNITION_TOOL_NAME = "recognize_image_tool"
IMAGE_RECOGNITION_AGENT_NAME = "vision_agent"
FILE_CONTENT_PLANNER_AGENT_NAME = "file_content_planner_agent"
TOOL_BOUND_UNBOUND_MARKER = "-"
IMAGE_RECOGNITION_TOOL = {
    "name": IMAGE_RECOGNITION_TOOL_NAME,
    "zhName": "图片识别工具",
    "displayName": "图片识别工具（recognize_image_tool）",
    "category": "vision_understanding",
    "purpose": "识别聊天中上传的图片，支持视觉问答、OCR、截图理解、图表分析和多图对比。",
    "trigger": "聊天消息包含图片附件、图片 URL 或图片数据时自动调用。",
    "outputs": ["image_analysis_text"],
    "status": "implemented",
    "configurable": True,
    "boundAgent": IMAGE_RECOGNITION_AGENT_NAME,
}

FILE_CONTENT_EXTRACTION_TOOLS = [
    {
        "name": "markdown_to_text_tool",
        "zhName": "Markdown 转文本工具",
        "displayName": "Markdown 转文本工具（markdown_to_text_tool）",
        "category": "file_content_extraction",
        "purpose": "提取 Markdown 文件内容：纯文本返回文本；包含图片时返回文本和图片；只有图片或扫描内容时返回图片。",
        "trigger": "上传 .md/.markdown 文件并要求读取、提取、解析或转为可供智能体使用的内容。",
        "inputFormats": ["md", "markdown"],
        "contentModes": ["text", "text_with_images", "scanned_or_image_only"],
        "outputs": ["text", "image"],
        "status": "implemented",
        "configurable": True,
        "invocation": "file_parse_pipeline",
    },
    {
        "name": "txt_to_text_tool",
        "zhName": "TXT 转文本工具",
        "displayName": "TXT 转文本工具（txt_to_text_tool）",
        "category": "file_content_extraction",
        "purpose": "读取 TXT 纯文本；存在随文件提交或引用的图片时同时保留图片，图片型内容则按图片输出。",
        "trigger": "上传 .txt 文件并要求读取、提取、解析或转为可供智能体使用的内容。",
        "inputFormats": ["txt"],
        "contentModes": ["text", "text_with_images", "scanned_or_image_only"],
        "outputs": ["text", "image"],
        "status": "implemented",
        "configurable": True,
        "invocation": "file_parse_pipeline",
    },
    {
        "name": "word_to_text_tool",
        "zhName": "Word 转文本工具",
        "displayName": "Word 转文本工具（word_to_text_tool）",
        "category": "file_content_extraction",
        "purpose": "提取 Word 段落、表格与图片：纯文本返回文本；图文文档返回文本和图片；扫描件返回页面图片。",
        "trigger": "上传 .doc/.docx 文件并要求读取、提取、解析或转为可供智能体使用的内容。",
        "inputFormats": ["doc", "docx"],
        "contentModes": ["text", "text_with_images", "scanned_or_image_only"],
        "outputs": ["text", "image"],
        "status": "implemented",
        "configurable": True,
        "invocation": "file_parse_pipeline",
    },
    {
        "name": "ppt_to_text_tool",
        "zhName": "PPT 转文本工具",
        "displayName": "PPT 转文本工具（ppt_to_text_tool）",
        "category": "file_content_extraction",
        "purpose": "按页提取 PPT 文本、表格与图片：纯文本返回文本；图文幻灯片返回文本和图片；扫描型幻灯片返回图片。",
        "trigger": "上传 .ppt/.pptx 文件并要求读取、提取、解析或转为可供智能体使用的内容。",
        "inputFormats": ["ppt", "pptx"],
        "contentModes": ["text", "text_with_images", "scanned_or_image_only"],
        "outputs": ["text", "image"],
        "status": "implemented",
        "configurable": True,
        "invocation": "file_parse_pipeline",
    },
    {
        "name": "pdf_to_text_tool",
        "zhName": "PDF 转文本工具",
        "displayName": "PDF 转文本工具（pdf_to_text_tool）",
        "category": "file_content_extraction",
        "purpose": "按页提取 PDF 文本与图片：文本 PDF 返回文本；图文 PDF 返回文本和图片；扫描 PDF 返回页面图片。",
        "trigger": "上传 .pdf 文件并要求读取、提取、解析或转为可供智能体使用的内容。",
        "inputFormats": ["pdf"],
        "contentModes": ["text", "text_with_images", "scanned_or_image_only"],
        "outputs": ["text", "image"],
        "status": "implemented",
        "configurable": True,
        "invocation": "file_parse_pipeline",
    },
]


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
        "outputs": ["md", "docx", "xlsx", "pptx", "mmd", "zip"],
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
        "boundAgent": FILE_CONTENT_PLANNER_AGENT_NAME,
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
        "boundAgent": FILE_CONTENT_PLANNER_AGENT_NAME,
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
        "boundAgent": FILE_CONTENT_PLANNER_AGENT_NAME,
    },
    {
        "name": "pptx_export_tool",
        "zhName": "PPT 导出工具",
        "displayName": "PPT 导出工具（pptx_export_tool）",
        "category": "content_export",
        "purpose": "把文件内容编排智能体生成的逐页大纲转换为真实 PPTX 文件。",
        "trigger": "用户明确要求 PPT/PPTX/幻灯片文件。",
        "outputs": ["pptx"],
        "status": "implemented",
    },
    {
        "name": "ai_ppt_generation_tool",
        "zhName": "AI PPT 生成工具",
        "displayName": "AI PPT 生成工具（ai_ppt_generation_tool）",
        "category": "presentation_generation",
        "purpose": "接收已确认的大纲、逐页内容、公共提示词和单页私有提示词，生成可预览、可编辑、可导出的 PPTX 任务结果。",
        "trigger": "仅供统一 AIPPT 专用流程显式调用；当前只注册工具与开关，暂未接入 Leader 或工作流调用。",
        "outputs": ["outline_json", "slide_json", "preview", "pptx"],
        "status": "registered",
        "configurable": True,
        "invocation": "unwired",
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
    {
        "name": "text_to_markdown_tool",
        "zhName": "文本转 Markdown 工具",
        "displayName": "文本转 Markdown 工具（text_to_markdown_tool）",
        "category": "content_export",
        "purpose": "把用户提供的文本按原文导出为 Markdown 文件，不整理、不改写内容。",
        "trigger": "用户要求把已提供的文本按原文转成 md/Markdown 文件时调用。",
        "outputs": ["md"],
        "status": "implemented",
    },
    {
        "name": "text_to_txt_tool",
        "zhName": "文本转 TXT 工具",
        "displayName": "文本转 TXT 工具（text_to_txt_tool）",
        "category": "content_export",
        "purpose": "把用户提供的文本按原文导出为纯文本文件，不整理、不改写内容。",
        "trigger": "用户要求把已提供的文本按原文转成 txt/纯文本文件时调用。",
        "outputs": ["txt"],
        "status": "implemented",
    },
    {
        "name": "text_to_docx_tool",
        "zhName": "文本转 Word 工具",
        "displayName": "文本转 Word 工具（text_to_docx_tool）",
        "category": "content_export",
        "purpose": "把用户提供的文本按原文导出为 Word 文档，不整理、不改写内容。",
        "trigger": "用户要求把已提供的文本按原文转成 word/docx 文件时调用。",
        "outputs": ["docx"],
        "status": "implemented",
    },
    *FILE_CONTENT_EXTRACTION_TOOLS,
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

TEXT_TO_MARKDOWN_TOOL_NAME = "text_to_markdown_tool"
TEXT_TO_TXT_TOOL_NAME = "text_to_txt_tool"
TEXT_TO_DOCX_TOOL_NAME = "text_to_docx_tool"
TEXT_TO_FILE_TOOL_BY_FORMAT = {
    "md": TEXT_TO_MARKDOWN_TOOL_NAME,
    "txt": TEXT_TO_TXT_TOOL_NAME,
    "docx": TEXT_TO_DOCX_TOOL_NAME,
}
TEXT_TO_FILE_TOOL_NAMES = frozenset(TEXT_TO_FILE_TOOL_BY_FORMAT.values())
TEXT_TO_FILE_FORMAT_NAMES = frozenset(TEXT_TO_FILE_TOOL_BY_FORMAT)
TEXT_TO_FILE_TOOL_LABELS = {
    TEXT_TO_MARKDOWN_TOOL_NAME: "文本转 Markdown 工具",
    TEXT_TO_TXT_TOOL_NAME: "文本转 TXT 工具",
    TEXT_TO_DOCX_TOOL_NAME: "文本转 Word 工具",
}
TOOL_CAPABILITY_QUERY_NAME = "tool_capability_query"
TOOL_CAPABILITY_QUERY = {
    "name": TOOL_CAPABILITY_QUERY_NAME,
    "zhName": "工具能力查询",
    "displayName": "工具能力查询（tool_capability_query）",
    "category": "capability_query",
    "purpose": "查询当前后台已启用的系统工具能力，并以用户可理解的方式返回。",
    "trigger": "用户询问系统能做什么、有哪些工具或支持哪些能力时调用。",
    "outputs": ["capability_list"],
    "status": "implemented",
    "configurable": False,
}

LEADER_CALLABLE_TOOLS = [
    IMAGE_RECOGNITION_TOOL,
    *VISUAL_GENERATION_TOOLS,
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
    TOOL_CAPABILITY_QUERY,
    {
        "name": "generated_export_tools",
        "zhName": "内容整理工具",
        "displayName": "内容整理工具（generated_export_tools）",
        "category": "content_export",
        "purpose": "先由文件内容编排智能体确定内容与结构，再调用指定格式工具生成真实附件。",
        "trigger": "用户明确要求 Word、Excel、Markdown、PPT 文件版，或打包下载。",
        "outputs": ["md", "docx", "xlsx", "pptx", "mmd", "zip"],
        "status": "implemented",
        "configurable": True,
    },
    {
        "name": "text_to_markdown_tool",
        "zhName": "文本转 Markdown 工具",
        "displayName": "文本转 Markdown 工具（text_to_markdown_tool）",
        "category": "content_export",
        "purpose": "把用户提供的文本按原文导出为 Markdown 文件，不整理、不改写内容。",
        "trigger": "用户要求把已提供的文本按原文转成 md/Markdown 文件时调用。",
        "outputs": ["md"],
        "status": "implemented",
        "configurable": True,
    },
    {
        "name": "text_to_txt_tool",
        "zhName": "文本转 TXT 工具",
        "displayName": "文本转 TXT 工具（text_to_txt_tool）",
        "category": "content_export",
        "purpose": "把用户提供的文本按原文导出为纯文本文件，不整理、不改写内容。",
        "trigger": "用户要求把已提供的文本按原文转成 txt/纯文本文件时调用。",
        "outputs": ["txt"],
        "status": "implemented",
        "configurable": True,
    },
    {
        "name": "text_to_docx_tool",
        "zhName": "文本转 Word 工具",
        "displayName": "文本转 Word 工具（text_to_docx_tool）",
        "category": "content_export",
        "purpose": "把用户提供的文本按原文导出为 Word 文档，不整理、不改写内容。",
        "trigger": "用户要求把已提供的文本按原文转成 word/docx 文件时调用。",
        "outputs": ["docx"],
        "status": "implemented",
        "configurable": True,
    },
]


class PdfConvertRequest(BaseModel):
    fileName: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)
    targetFormat: str = Field(min_length=1, max_length=16)
    convertMode: str = Field(default="image", max_length=16)


class PptConvertRequest(BaseModel):
    fileName: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)
    convertMode: str = Field(default="reflow", max_length=16)


class DocxConvertRequest(BaseModel):
    fileName: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)
    convertMode: str = Field(default="smart", max_length=16)


class FileContentToolTestRequest(BaseModel):
    toolName: str = Field(min_length=1, max_length=80)
    fileName: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)


class AgentExampleInputUpdateRequest(BaseModel):
    input: str = Field(min_length=1, max_length=12000)


class QuestionBankReviewRequest(BaseModel):
    payload: Dict[str, Any]
    expectedType: Optional[str] = Field(default=None, max_length=64)


@export_router.get("/exports/{storage_key}")
def download_generated_export(
    storage_key: str,
    export_capability: Optional[str] = Header(default=None, alias="X-AI-Export-Capability"),
) -> StreamingResponse:
    try:
        export_file = open_generated_export(storage_key, export_capability)
    except GeneratedExportAccessError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc
    def verified_chunks():
        try:
            for chunk in iter(lambda: export_file.stream.read(1024 * 1024), b""):
                yield chunk
        finally:
            export_file.stream.close()

    return StreamingResponse(
        verified_chunks(),
        media_type=export_file.mime_type,
        headers={
            "X-AI-Export-SHA256": export_file.sha256,
            "Cache-Control": "private, no-store",
            "Content-Length": str(export_file.size),
            "Content-Disposition": f'attachment; filename="{export_file.storage_key}"',
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
        "visualGeneration": {
            "leaderMode": "tool_only",
            "tools": VISUAL_GENERATION_TOOLS,
            "internalAgentsExposedToLeader": False,
            "imageProviderEntry": "image_agent",
        },
        "internalTools": [TOOL_INTENT_ROUTER_TOOL],
        "profileSummary": {
            "agent": "profile_summary_agent",
            "purpose": "把 Java 画像快照总结为强项、欠缺、置信依据和补证建议；不修改画像分数。",
            "output": "strict_profile_summary_json",
        },
        "agents": ["leader_agent", *LEADER_CALLABLE_AGENT_ORDER],
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
                "name": "visual_generation_tools",
                "category": "visual_generation",
                "purpose": "Leader 只选择视觉工具；工具内部调用专用提示词智能体，再通过唯一图片入口生成图片。",
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
        "visualTools": VISUAL_GENERATION_TOOLS,
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
                "name": "opencode",
                "runtime": "app.model_providers.deepseek.provider",
                "status": "implemented",
                "defaultBaseUrl": "https://opencode.ai/zen/go/v1",
                "exampleModel": "deepseek-v4-flash",
                "supportedModalities": ["text"],
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
    catalog["leaderTools"] = [_annotate_tool_trigger(tool) for tool in LEADER_CALLABLE_TOOLS]
    catalog["serviceTools"] = [_annotate_tool_trigger(tool) for tool in CAMPUS_SERVICE_TOOLS]
    catalog["leaderCallableCatalog"] = _build_leader_callable_catalog()
    catalog["generatedTools"] = [_annotate_tool_trigger(tool) for tool in GENERATED_CONTENT_TOOLS]
    catalog["internalTools"] = [TOOL_INTENT_ROUTER_TOOL]
    catalog["fileFormats"] = get_file_format_registry()
    return catalog


_SYSTEM_TRIGGER_TOOLS = frozenset({
    IMAGE_RECOGNITION_TOOL_NAME,
    IMAGE_STITCHING_TOOL["name"],
    *[tool["name"] for tool in FILE_CONTENT_EXTRACTION_TOOLS],
})
_RULE_DIRECT_TRIGGER_TOOLS = frozenset(TEXT_TO_FILE_TOOL_NAMES)
_WORKFLOW_DEPENDENCY_TOOLS = frozenset({
    "markdown_export_tool", "docx_export_tool", "excel_export_tool", "pptx_export_tool",
    "content_archive_tool", "diagram_source_export_tool",
})


def _annotate_tool_trigger(tool: Dict[str, Any]) -> Dict[str, Any]:
    item = dict(tool or {})
    name = str(item.get("name") or "").strip()
    if item.get("invocation") == "unwired":
        trigger_type, stage = "unwired", "unwired"
    elif name in _SYSTEM_TRIGGER_TOOLS:
        trigger_type, stage = "system", "input_preprocessing"
    elif name in _RULE_DIRECT_TRIGGER_TOOLS:
        trigger_type, stage = "rule_direct", "direct_conversion"
    elif name in _WORKFLOW_DEPENDENCY_TOOLS:
        trigger_type, stage = "workflow_dependency", "output_materialization"
    else:
        trigger_type, stage = "leader", "business_orchestration"
    item["triggerType"] = trigger_type
    item["pipelineStage"] = stage
    return item


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
    x_langfuse_enabled: Optional[str] = Header(default=None, alias="X-Langfuse-Enabled"),
    x_langfuse_base_url: Optional[str] = Header(default=None, alias="X-Langfuse-Base-Url"),
    x_langfuse_public_key: Optional[str] = Header(default=None, alias="X-Langfuse-Public-Key"),
    x_langfuse_secret_key: Optional[str] = Header(default=None, alias="X-Langfuse-Secret-Key"),
) -> RagQueryResponse:
    request_started_at = time.perf_counter()
    _require_authorization(authorization)
    _validate_rag_input_length(request)
    audit = _llm_header_audit_fields(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    )
    logger.info(
        "rag query request received agent=%s input_len=%s provider=%s base_url=%s model=%s api_key_len=%s api_key_suffix=%s api_key_sha256_8=%s",
        request.agentName or "-",
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
        with use_settings(settings_from_headers(x_langfuse_enabled, x_langfuse_base_url, x_langfuse_public_key, x_langfuse_secret_key)):
            with observe_request(
                "internal.rag.query",
                session_id=str((request.metadata or {}).get("sessionId") or "") or None,
                metadata={
                    "agentName": request.agentName or "leader_agent",
                    "streaming": False,
                },
            ):
                response = _run_rag_query_core(request, authorization or "")
        finalization_started_at = time.perf_counter()
        response = _finalize_rag_response(request, response)
        finalization_ms = _elapsed_ms(finalization_started_at)
        _merge_response_performance(
            response,
            profileMs=_profile_ms_from_request(request),
            finalizeMs=finalization_ms,
        )
        persistence_started_at = time.perf_counter()
        _save_conversation_context(request, authorization or "", response)
        persistence_ms = _elapsed_ms(persistence_started_at)
        _merge_response_performance(
            response,
            persistMs=persistence_ms,
            totalMs=_elapsed_ms(request_started_at),
        )
        timings = (response.metadata or {}).get("timings") or {}
        logger.info(
            "rag query completed agent=%s route_mode=%s timings=%s",
            request.agentName or "leader_agent",
            (response.metadata or {}).get("routeMode") or "direct",
            timings,
        )
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
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
    x_langfuse_enabled: Optional[str] = Header(default=None, alias="X-Langfuse-Enabled"),
    x_langfuse_base_url: Optional[str] = Header(default=None, alias="X-Langfuse-Base-Url"),
    x_langfuse_public_key: Optional[str] = Header(default=None, alias="X-Langfuse-Public-Key"),
    x_langfuse_secret_key: Optional[str] = Header(default=None, alias="X-Langfuse-Secret-Key"),
):
    request_started_at = time.perf_counter()
    _require_authorization(authorization)
    _validate_rag_input_length(request)
    llm_config = build_llm_runtime_config(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    )
    learning_workflow = _is_learning_workflow_request(request)

    async def event_stream():
        stream_started_at = request_started_at
        settings_scope = use_settings(settings_from_headers(x_langfuse_enabled, x_langfuse_base_url, x_langfuse_public_key, x_langfuse_secret_key))
        settings_scope.__enter__()
        observation_scope = observe_request(
            "internal.rag.query",
            session_id=str((request.metadata or {}).get("sessionId") or "") or None,
            user_id=int(x_user_id) if x_user_id and x_user_id.isdigit() else None,
            metadata={
                "agentName": request.agentName or "leader_agent",
                "streaming": True,
            },
        )
        observation_scope.__enter__()
        token = set_active_llm_config(llm_config)
        generation_started = False
        plan = None
        plan_ms = 0
        execution_ms = 0
        first_event_ms = 0
        try:
            if learning_workflow:
                async for event in _stream_learning_workflow(request, x_user_id):
                    yield event
                return

            first_event_ms = _elapsed_ms(stream_started_at)
            yield build_sse("status", {"stage": "processing"})
            request.input = _prepare_request_input(request)
            requested_agent = _normalize_requested_agent(request)
            if request.agentName and not requested_agent:
                raise HTTPException(status_code=400, detail="智能体不存在")
            if requested_agent in INTERNAL_ONLY_AGENT_NAMES and not (
                isinstance(request.metadata, dict)
                and request.metadata.get("testFrom") == "admin_agent_console"
            ):
                raise HTTPException(status_code=400, detail="该智能体仅由系统内部工具自动调用，不能直接执行")

            active_agent = requested_agent or "leader_agent"
            if active_agent == "leader_agent":
                planning_started_at = time.perf_counter()
                profile_context = _profile_context_from_request(request)
                callable_catalog = _build_leader_callable_catalog(request)
                conversation_context = _apply_conversation_context(request, authorization or "")
                if _should_run_attachment_input_pipeline(request):
                    plan_ms = _elapsed_ms(planning_started_at)
                    yield build_sse("tool_start", {
                        "stage": "input_pipeline",
                        "message": "正在自动提取文件内容并处理图片。",
                        "triggerType": "system",
                        "attachmentCount": len(request.attachments or []),
                    })
                    execution_started_at = time.perf_counter()
                    response = await asyncio.to_thread(
                        _run_attachment_input_pipeline,
                        request,
                        authorization or "",
                        profile_context,
                        callable_catalog,
                    )
                    execution_ms = _elapsed_ms(execution_started_at)
                else:
                    try:
                        plan = _requested_image_stitching_plan(request)
                        if plan is None:
                            plan = _requested_image_recognition_plan(request)
                        if plan is None:
                            plan = _requested_file_transform_plan(request)
                        if plan is None:
                            plan = await asyncio.to_thread(
                                leader_agent.plan,
                                request.input,
                                "",
                                profile_context=profile_context,
                                callable_catalog=callable_catalog,
                                conversation_context=conversation_context,
                            )
                    except AgentExecutionError:
                        raise
                    except Exception as exc:
                        raise AgentExecutionError(
                            message="Leader 模型规划调用失败，请检查 Leader 智能体的模型配置。",
                            agent_name="leader_agent",
                            stage="leader_plan",
                            status_code=getattr(exc, "status_code", 500) or 500,
                            raw_message=_exception_message(exc),
                            model_provider=getattr(llm_config, "provider", "") or "",
                            model=getattr(llm_config, "model", "") or "",
                            base_url=getattr(llm_config, "base_url", "") or "",
                        ) from exc
                    plan_ms = _elapsed_ms(planning_started_at)
                    if getattr(plan, "action", "") == "call_tool" and getattr(plan, "answer", ""):
                        yield build_sse("tool_start", {
                            "stage": "tool_start",
                            "message": plan.answer,
                            "intent": plan.intent,
                            "toolName": plan.tool_name,
                            "toolDisplayName": _tool_display_name(plan.tool_name),
                            "routeReason": plan.route_reason,
                            "triggerType": "leader",
                        })
                    if _should_emit_generation_start(request, plan.target_agent, plan):
                        yield build_sse("generation_start", _build_generation_start_payload(request, plan))
                        generation_started = True
                    execution_started_at = time.perf_counter()
                    response = await asyncio.to_thread(_execute_leader_plan, request, authorization or "", profile_context, plan, callable_catalog)
                    execution_ms = _elapsed_ms(execution_started_at)
            else:
                if _should_emit_generation_start(request, active_agent):
                    yield build_sse("generation_start", _build_generation_start_payload(request, None, active_agent))
                    generation_started = True
                execution_started_at = time.perf_counter()
                response = await asyncio.to_thread(_run_rag_query_core, request, authorization or "")
                execution_ms = _elapsed_ms(execution_started_at)
            finalization_started_at = time.perf_counter()
            response = _finalize_rag_response(request, response)
            finalization_ms = _elapsed_ms(finalization_started_at)
            if plan is not None:
                _set_response_route_mode(response, plan)
            else:
                response.metadata = dict(response.metadata or {})
                response.metadata.setdefault("routeMode", "direct")
            _merge_response_performance(
                response,
                profileMs=_profile_ms_from_request(request),
                planMs=plan_ms,
                executionMs=execution_ms,
                finalizeMs=finalization_ms,
                firstEventMs=first_event_ms,
            )
            metadata = response.metadata or {}
            request_metadata = request.metadata if isinstance(request.metadata, dict) else {}
            if request_metadata.get("profileContextSource"):
                metadata["profileContextSource"] = request_metadata.get("profileContextSource")
            session_id = str((request.metadata or {}).get("sessionId") or "")
            for trace_item in response.trace or []:
                trace_payload = trace_item.model_dump() if hasattr(trace_item, "model_dump") else trace_item
                if isinstance(trace_payload, dict):
                    yield build_sse("workflow_step", trace_payload)
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
                first_token_recorded = False
                for chunk in chunk_answer(response.answer):
                    if not first_token_recorded:
                        _merge_response_performance(response, firstTokenMs=_elapsed_ms(stream_started_at))
                        first_token_recorded = True
                    yield build_sse("delta", {"content": chunk})
            persistence_started_at = time.perf_counter()
            _save_conversation_context(request, authorization or "", response)
            persistence_ms = _elapsed_ms(persistence_started_at)
            _merge_response_performance(
                response,
                persistMs=persistence_ms,
                totalMs=_elapsed_ms(stream_started_at),
            )
            metadata = response.metadata or {}
            logger.info(
                "rag stream completed agent=%s route_mode=%s timings=%s",
                request.agentName or "leader_agent",
                metadata.get("routeMode") or "direct",
                metadata.get("timings") or {},
            )
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
            if learning_workflow:
                yield build_sse("error", _build_learning_error_payload(request, exc))
            else:
                yield build_sse("error", _build_stream_error_payload(request, exc, llm_config))
        finally:
            reset_active_llm_config(token)
            observation_scope.__exit__(None, None, None)
            settings_scope.__exit__(None, None, None)

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "X-Accel-Buffering": "no",
        },
    )


LEARNING_WORKFLOW_INTENTS = frozenset({
    "resource_package",
    "learning_plan",
    "weakness_review",
    "path_replanning",
})


def _is_learning_workflow_request(request: RagQueryRequest) -> bool:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    return (
        str(metadata.get("courseKey") or "").strip().lower() == "python"
        and str(request.intent or "").strip() in LEARNING_WORKFLOW_INTENTS
    )


async def _stream_learning_workflow(
    request: RagQueryRequest,
    x_user_id: Optional[str],
):
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    workflow_id = str(metadata.get("workflowId") or "").strip()
    yield build_sse("accepted", _learning_event_payload(
        workflow_id,
        "accepted",
        0,
        "学习资源生成请求已受理",
    ))

    plan = leader_agent.plan(
        request.input,
        "",
        learning_context={
            "courseKey": metadata.get("courseKey"),
            "intent": request.intent,
        },
    )
    if plan.action != "run_learning_workflow":
        raise LearningContentGuardError("学习请求未进入受控工作流")

    try:
        user_id = int(str(x_user_id or "").strip())
    except (TypeError, ValueError) as exc:
        raise LearningContentGuardError("学习工作流缺少已认证用户身份") from exc
    if user_id <= 0:
        raise LearningContentGuardError("学习工作流用户身份无效")

    profile_snapshot = metadata.get("profileSnapshot")
    mastery_snapshot = metadata.get("masterySnapshot")
    path_snapshot = metadata.get("pathSnapshot")
    if not isinstance(profile_snapshot, dict):
        raise LearningContentGuardError("学习画像快照无效")
    if not isinstance(mastery_snapshot, list):
        raise LearningContentGuardError("知识掌握度快照无效")
    if not isinstance(path_snapshot, dict):
        raise LearningContentGuardError("学习路径快照无效")
    yield build_sse("profile", _learning_event_payload(
        workflow_id,
        "profiling",
        5,
        "已加载动态学习画像与掌握度",
    ))

    references = sanitize_learning_references(metadata.get("references") or [])
    yield build_sse("retrieval", {
        **_learning_event_payload(
            workflow_id,
            "retrieving",
            12,
            "已接收并净化课程知识证据",
        ),
        "evidenceCount": len(references),
    })

    learning_request = LearningWorkflowRequest.model_validate({
        "workflowId": workflow_id,
        "userId": user_id,
        "courseKey": "python",
        "topic": request.input,
        "profileSnapshot": profile_snapshot,
        "masterySnapshot": mastery_snapshot,
        "pathSnapshot": path_snapshot,
        "references": references,
        "requestedResourceTypes": metadata.get("requestedResourceTypes") or [],
    })

    loop = asyncio.get_running_loop()
    queue = asyncio.Queue()
    callback_lock = threading.Lock()
    transition_count = 0
    review_emitted = False

    def event_callback(name: str, payload: Dict[str, Any]) -> None:
        nonlocal transition_count, review_emitted
        with callback_lock:
            event_name = ""
            progress = 0
            event_payload = dict(payload or {})
            if name == "planning_start":
                event_name, progress = "planning", 18
            elif name in {"agent_start", "agent_done"}:
                transition_count += 1
                event_name = name
                progress = min(68, 18 + transition_count * 4)
                # Draft resources have not passed review yet. Keep progress events
                # deliberately content-free until the final reviewed delivery.
                event_payload.pop("resource", None)
            elif name == "review_start":
                event_name, progress = "review_start", 74
            elif name == "review_done" and not review_emitted:
                review_emitted = True
                event_name, progress = "review_result", 82
            if not event_name:
                return
            event_data = _learning_event_payload(
                workflow_id,
                _learning_stage(event_name),
                progress,
                str(event_payload.pop("message", "") or _learning_event_message(event_name)),
                agent_name=str(event_payload.pop("agentName", "") or ""),
                resource_type=str(event_payload.pop("resourceType", "") or ""),
            )
            event_data.update(_jsonable(event_payload))
            loop.call_soon_threadsafe(queue.put_nowait, (event_name, event_data))

    worker = asyncio.create_task(asyncio.to_thread(
        run_learning_workflow,
        learning_request,
        event_callback=event_callback,
    ))
    while not worker.done() or not queue.empty():
        try:
            event_name, event_data = await asyncio.wait_for(queue.get(), timeout=0.05)
        except asyncio.TimeoutError:
            continue
        yield build_sse(event_name, event_data)
    workflow_result = await worker

    yield build_sse("exporting", _learning_event_payload(
        workflow_id,
        "exporting",
        88,
        "正在生成可直接使用的课程文件",
    ))
    attachments_by_type, attachments, failed_resources = await asyncio.to_thread(
        export_learning_resources,
        workflow_result,
    )
    failed_types = {
        str(item.get("resourceType") or "")
        for item in failed_resources
        if isinstance(item, dict)
    }
    delivered_workflow_resources = [
        item for item in workflow_result.resources
        if item.resourceType not in failed_types
    ]

    yield build_sse("pathing", _learning_event_payload(
        workflow_id,
        "pathing",
        93,
        "个性化学习路径已准备完成",
    ))
    bundle = build_learning_resource_bundle(
        workflow_id=workflow_id,
        topic=request.input,
        resources=delivered_workflow_resources,
        references=references,
        attachments_by_type=attachments_by_type,
        resource_metadata=_learning_resource_metadata(path_snapshot, mastery_snapshot),
    )
    status = "partial" if failed_resources else "completed"
    yield build_sse("persisting", {
        **_learning_event_payload(
            workflow_id,
            "persisting",
            97,
            "正在向 Java 控制面交付资源与路径",
            retryable=bool(failed_resources),
        ),
        "status": status,
        "failedResources": failed_resources,
    })
    raw_result = workflow_result.model_dump(mode="json")
    delivery_result = {
        "workflowId": workflow_id,
        "status": status,
        "resources": bundle["resources"],
        "attachments": attachments,
        "evidenceChain": bundle["evidenceChain"],
        "pathDraft": raw_result["pathDraft"],
        "packageMetadata": raw_result["packageMetadata"],
        "events": raw_result["events"],
        "failedResources": failed_resources,
    }
    yield build_sse("done", {
        **_learning_event_payload(
            workflow_id,
            "completed" if status == "completed" else "partial",
            100,
            "学习资源包已完成" if status == "completed" else "部分资源待重试",
            retryable=bool(failed_resources),
        ),
        **delivery_result,
        "result": delivery_result,
    })


def _learning_event_payload(
    workflow_id: str,
    stage: str,
    progress: int,
    message: str,
    *,
    agent_name: str = "",
    resource_type: str = "",
    retryable: bool = False,
) -> Dict[str, Any]:
    return {
        "workflowId": workflow_id,
        "stage": stage,
        "progress": max(0, min(100, int(progress))),
        "agentName": agent_name,
        "resourceType": resource_type,
        "message": message,
        "retryable": retryable,
    }


def _learning_stage(event_name: str) -> str:
    return {
        "planning": "planning",
        "agent_start": "generating",
        "agent_done": "generating",
        "review_start": "reviewing",
        "review_result": "reviewing",
    }.get(event_name, event_name)


def _learning_event_message(event_name: str) -> str:
    return {
        "planning": "正在规划个性化学习路径",
        "agent_start": "专业学习智能体开始生成资源",
        "agent_done": "专业学习智能体已完成资源",
        "review_start": "正在审核资源事实与质量",
        "review_result": "资源审核已完成",
    }.get(event_name, "学习工作流正在执行")


def _learning_resource_metadata(
    path_snapshot: Dict[str, Any],
    mastery_snapshot: List[Dict[str, Any]],
) -> Dict[str, Any]:
    first_mastery = mastery_snapshot[0] if mastery_snapshot and isinstance(mastery_snapshot[0], dict) else {}
    return {
        "learningPathId": path_snapshot.get("learningPathId") or path_snapshot.get("id") or "",
        "learningPathItemKey": path_snapshot.get("currentItemKey") or path_snapshot.get("currentItem") or "",
        "knowledgePoint": first_mastery.get("knowledgePointKey") or first_mastery.get("knowledgePoint") or "",
    }


def _jsonable(value: Any) -> Any:
    if hasattr(value, "model_dump"):
        return _jsonable(value.model_dump(mode="json"))
    if isinstance(value, dict):
        return {str(key): _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    return value


def _build_learning_error_payload(
    request: RagQueryRequest,
    exc: Exception,
) -> Dict[str, Any]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    if isinstance(exc, LearningContentGuardError):
        message = str(exc)
    else:
        message = "学习资源工作流执行失败，请重试"
    return {
        **_learning_event_payload(
            str(metadata.get("workflowId") or ""),
            "failed",
            100,
            message,
            retryable=True,
        ),
        "errorType": exc.__class__.__name__,
    }


def _run_rag_query_core(request: RagQueryRequest, authorization: str) -> RagQueryResponse:
    request.input = _prepare_request_input(request)
    direct_tool_response = _run_admin_direct_tool_test(request, authorization)
    if direct_tool_response is not None:
        return direct_tool_response
    requested_agent = _normalize_requested_agent(request)
    if request.agentName and not requested_agent:
        raise HTTPException(status_code=400, detail="智能体不存在")
    if requested_agent in INTERNAL_ONLY_AGENT_NAMES and not (
        isinstance(request.metadata, dict)
        and request.metadata.get("testFrom") == "admin_agent_console"
    ):
        raise HTTPException(status_code=400, detail="该智能体仅由系统内部工具自动调用，不能直接执行")

    active_agent = requested_agent or "leader_agent"
    if active_agent == "leader_agent":
        return _run_leader_orchestration(request, authorization)
    if not _is_agent_enabled(request, active_agent):
        return _run_disabled_agent_response(request, active_agent)

    agent_profile = get_agent_profile(active_agent)
    if agent_profile and not agent_profile.get("needRetrieval", True):
        return _run_direct_agent(request, agent_profile)

    return _run_agent_without_local_retrieval(request, active_agent)


ADMIN_SUB_EXPORT_TOOL_TARGETS = {
    "markdown_export_tool": ("generated_export_tools", "md"),
    "docx_export_tool": ("generated_export_tools", "docx"),
    "excel_export_tool": ("generated_export_tools", "xlsx"),
    "pptx_export_tool": ("generated_export_tools", "pptx"),
    "content_archive_tool": ("generated_export_tools", "zip"),
    "diagram_source_export_tool": ("generated_export_tools", "mmd"),
}


def _admin_direct_testable_tool_names() -> frozenset:
    names = set()
    for tool in [*LEADER_CALLABLE_TOOLS, *GENERATED_CONTENT_TOOLS]:
        name = str(tool.get("name") or "").strip()
        if name:
            names.add(name)
    return frozenset(names)


def _is_admin_tool_console_request(request: RagQueryRequest) -> bool:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    if metadata.get("testFrom") == "admin_tool_console" and metadata.get("directToolTest") is True:
        return True
    return metadata.get("testFrom") == "admin_agent_console"


def _build_direct_tool_test_plan(tool_name: str) -> LeaderPlan:
    intent = "tool_test"
    if tool_name == IMAGE_STITCHING_TOOL["name"]:
        intent = "image_stitching"
    elif tool_name in TEXT_TO_FILE_TOOL_NAMES or tool_name == "generated_export_tools":
        intent = "document_export"
    elif tool_name == IMAGE_RECOGNITION_TOOL_NAME:
        intent = "image_understanding"
    elif tool_name in VISUAL_GENERATION_TOOL_NAMES:
        intent = "image_generation"
    elif tool_name == "text_to_sql":
        intent = "structured_query"
    elif tool_name in SERVICE_TOOL_NAMES:
        intent = "campus_service"
    elif tool_name == TOOL_CAPABILITY_QUERY_NAME:
        intent = "capability_inquiry"
    return LeaderPlan(
        intent=intent,
        target_agent=tool_name,
        need_retrieval=False,
        rag_strategy="",
        action="call_tool",
        tool_name=tool_name,
        route_reason="管理台直接运行指定工具。",
        route_mode="direct_tool_test",
    )


def _resolve_admin_direct_tool_test(request: RagQueryRequest, tool_name: str) -> Tuple[str, RagQueryRequest]:
    mapped = ADMIN_SUB_EXPORT_TOOL_TARGETS.get(tool_name)
    if not mapped:
        return tool_name, request
    executable_tool, output_type = mapped
    metadata = dict(request.metadata or {})
    metadata["requestedOutputType"] = output_type
    request.metadata = metadata
    return executable_tool, request


def _normalize_direct_tool_test_response(
    response: RagQueryResponse,
    requested_tool: str,
) -> RagQueryResponse:
    tool_label = _tool_zh_name(requested_tool) or requested_tool
    metadata = dict(response.metadata or {})
    metadata.update({
        "executionMode": "direct_tool_test",
        "executionModeLabel": f"管理台直接运行{tool_label}",
        "routeMode": "direct_tool_test",
        "agentName": requested_tool,
        "targetAgent": requested_tool,
        "executedAgent": requested_tool,
        "toolName": requested_tool,
        "toolDisplayName": _tool_display_name(requested_tool),
    })
    response.metadata = metadata
    response.trace = [
        item for item in (response.trace or [])
        if getattr(item, "stage", None) != "leader_route"
    ]
    return response


def _run_admin_direct_tool_test(
    request: RagQueryRequest,
    authorization: str,
) -> Optional[RagQueryResponse]:
    """Run an explicitly selected admin-console tool without Leader routing."""
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    if metadata.get("testFrom") != "admin_tool_console" or metadata.get("directToolTest") is not True:
        return None
    requested_tool = str(metadata.get("expectedToolName") or "").strip()
    if not requested_tool:
        raise HTTPException(status_code=400, detail="请选择要测试的工具。")
    if requested_tool not in _admin_direct_testable_tool_names():
        raise HTTPException(status_code=400, detail="当前工具暂不支持直接测试。")
    if not _is_tool_enabled(request, requested_tool):
        raise HTTPException(
            status_code=403,
            detail=f"工具 {_tool_display_name(requested_tool)} 已在后台关闭，无法运行测试。",
        )
    executable_tool, request = _resolve_admin_direct_tool_test(request, requested_tool)
    plan = _build_direct_tool_test_plan(executable_tool)
    if executable_tool in TEXT_TO_FILE_TOOL_NAMES:
        return _run_text_to_file_tool(request, plan, direct_tool_test=True)
    response = _execute_leader_plan(request, authorization, None, plan, callable_catalog=None)
    return _normalize_direct_tool_test_response(response, requested_tool)


def _normalize_requested_agent(request: RagQueryRequest) -> Optional[str]:
    """Allow the admin test console to exercise registered internal agents directly."""
    normalized = normalize_leader_request_agent(request.agentName)
    if normalized:
        return normalized
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    if metadata.get("testFrom") == "admin_agent_console":
        return normalize_agent_name(request.agentName)
    return None


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


def _elapsed_ms(started_at: float) -> int:
    return max(0, int((time.perf_counter() - started_at) * 1000))


def _optional_nonnegative_int(value: Any) -> Optional[int]:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return None
    return max(0, parsed)


def _profile_ms_from_request(request: RagQueryRequest) -> Optional[int]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    return _optional_nonnegative_int(metadata.get("profileContextMs"))


def _merge_response_performance(
    response: RagQueryResponse,
    **values: Optional[int],
) -> RagQueryResponse:
    metadata = dict(response.metadata or {})
    timings = dict(metadata.get("timings") or {})
    for name, value in values.items():
        normalized = _optional_nonnegative_int(value)
        if normalized is None:
            continue
        timings[name] = normalized
        # Keep the flat fields for the current App trace panel while also exposing a
        # grouped object for future observability screens.
        metadata[name] = normalized
    metadata["timings"] = timings
    if "totalMs" in timings:
        # Both the synchronous and SSE endpoints measure through conversation
        # context persistence. Socket delivery after the response is yielded is
        # intentionally outside this Python processing scope.
        metadata["timingScope"] = "python_processing_including_persistence"
    response.metadata = metadata
    return response


def _set_response_route_mode(response: RagQueryResponse, plan: Any) -> RagQueryResponse:
    metadata = dict(response.metadata or {})
    route_mode = str(getattr(plan, "route_mode", "") or "llm").strip()
    metadata["routeMode"] = route_mode
    response.metadata = metadata
    return response


def _feature_enabled(name: str, default: bool = True) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return str(raw).strip().lower() not in {"0", "false", "no", "off", "disabled"}


def _service_tool_backend_failure(cache_meta: Any) -> Dict[str, Any]:
    """Return failure diagnostics when an empty adapter result is not real empty data.

    The Java adapter intentionally converts transport errors to ``{}``/``[]``.
    Its per-call cache events are therefore the only non-invasive signal available
    at this layer: successful calls are ``hit``/``miss`` and failed calls are
    ``error``. A circuit-open or administratively disabled adapter makes no request,
    so it produces zero events.
    """
    meta = cache_meta if isinstance(cache_meta, dict) else {}
    tool_cache = meta.get("toolCache") if isinstance(meta.get("toolCache"), dict) else {}
    events = [event for event in (tool_cache.get("events") or []) if isinstance(event, dict)]
    failure_events: List[Dict[str, Any]] = []
    failure_kind = ""
    for event in events:
        status = str(event.get("status") or "").strip().lower()
        status_code = _optional_nonnegative_int(event.get("statusCode") or event.get("httpStatus"))
        if status_code == 401 or "unauthor" in status:
            failure_kind = failure_kind or "unauthorized"
        elif status_code == 403 or "forbidden" in status:
            failure_kind = failure_kind or "forbidden"
        elif status_code in {408, 504} or "timeout" in status or "timed_out" in status:
            failure_kind = failure_kind or "timeout"
        elif status_code is not None and status_code >= 400:
            failure_kind = failure_kind or "request_error"
        elif status in {"error", "failed", "failure", "circuit_open", "disabled"}:
            failure_kind = failure_kind or (status if status != "error" else "request_error")
        else:
            continue
        failure_events.append(event)

    if failure_events:
        return {
            "status": failure_kind or "request_error",
            "reason": "java_backend_request_failed",
            "errorCount": len(failure_events),
        }

    successful_event_count = sum(
        1
        for event in events
        if str(event.get("status") or "").strip().lower() in {"hit", "miss"}
    )
    if successful_event_count > 0:
        return {}

    request_count = _optional_nonnegative_int(tool_cache.get("requestCount"))
    if request_count is None:
        request_count = len(events)
    if request_count > 0:
        return {
            "status": "unverified",
            "reason": "java_backend_result_status_missing",
            "errorCount": 0,
        }

    retriever = getattr(data_store, "_retriever", None)
    if not bool(getattr(data_store, "enabled", True)):
        status = "disabled"
        reason = "java_backend_disabled"
    else:
        disabled_until = getattr(retriever, "disabled_until", None)
        if isinstance(disabled_until, (int, float)) and disabled_until > time.monotonic():
            status = "circuit_open"
            reason = "java_backend_circuit_open"
        else:
            status = "not_called"
            reason = "java_backend_request_not_executed"
    return {"status": status, "reason": reason, "errorCount": 0}


def _run_leader_orchestration(request: RagQueryRequest, authorization: str) -> RagQueryResponse:
    planning_started_at = time.perf_counter()
    profile_context = _profile_context_from_request(request)
    callable_catalog = _build_leader_callable_catalog(request)
    conversation_context = _apply_conversation_context(request, authorization)
    if _should_run_attachment_input_pipeline(request):
        return _run_attachment_input_pipeline(
            request, authorization, profile_context, callable_catalog,
        )
    plan = _requested_image_stitching_plan(request)
    if plan is None:
        plan = _requested_image_recognition_plan(request)
    if plan is None:
        plan = _requested_file_transform_plan(request)
    if plan is None:
        plan = leader_agent.plan(
            request.input,
            "",
            profile_context=profile_context,
            callable_catalog=callable_catalog,
            conversation_context=conversation_context,
        )
    plan_ms = _elapsed_ms(planning_started_at)
    execution_started_at = time.perf_counter()
    response = _execute_leader_plan(request, authorization, profile_context, plan, callable_catalog=callable_catalog)
    execution_ms = _elapsed_ms(execution_started_at)
    _set_response_route_mode(response, plan)
    return _merge_response_performance(
        response,
        profileMs=_profile_ms_from_request(request),
        planMs=plan_ms,
        executionMs=execution_ms,
    )


def _requested_image_stitching_plan(request: RagQueryRequest) -> Optional[LeaderPlan]:
    image_count = len(collect_stitch_images(request))
    if image_count < 2:
        if _is_automatic_upload_request(request) and _has_image_container_attachment(request):
            return LeaderPlan(
                intent="image_stitching",
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="",
                answer="文件中未检测到至少两张可拼接的图片。",
                action="direct_answer",
                route_reason="已检查上传文件，但未找到至少两张可提取图片。",
                route_mode="attachment",
            )
        return None
    if not _is_tool_enabled(request, IMAGE_STITCHING_TOOL["name"]):
        return LeaderPlan(
            intent="image_stitching",
            target_agent="leader_agent",
            need_retrieval=False,
            rag_strategy="",
            answer="图片拼接工具当前已关闭，请先在后台开启后再试。",
            action="direct_answer",
            route_reason=f"检测到 {image_count} 个图片资源，但图片拼接工具已关闭。",
            route_mode="tool_disabled",
        )
    return LeaderPlan(
        intent="image_stitching",
        target_agent="leader_agent",
        need_retrieval=False,
        rag_strategy="",
        answer="正在按上传顺序拼接图片。",
        action="call_tool",
        tool_name=IMAGE_STITCHING_TOOL["name"],
        route_reason=f"检测到 {image_count} 个图片资源，自动调用图片拼接工具。",
        route_mode="attachment",
    )


def _is_automatic_upload_request(request: RagQueryRequest) -> bool:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    return metadata.get("source") == "web_ai_conversation" and metadata.get("uploadOnly") is True


def _has_image_container_attachment(request: RagQueryRequest) -> bool:
    for raw in request.attachments or []:
        if not isinstance(raw, dict):
            continue
        name = str(raw.get("name") or raw.get("fileName") or "").lower()
        mime_type = str(raw.get("mimeType") or raw.get("contentType") or raw.get("type") or "").lower()
        if (
            mime_type in {"application/pdf", "application/zip"}
            or "presentationml.presentation" in mime_type
            or "wordprocessingml.document" in mime_type
            or "spreadsheetml.sheet" in mime_type
            or name.endswith((".pdf", ".pptx", ".docx", ".xlsx", ".zip"))
        ):
            return True
    return False


def _requested_image_recognition_plan(request: RagQueryRequest) -> Optional[LeaderPlan]:
    if isinstance(request.metadata, dict) and request.metadata.get("uploadOnly") is True:
        return None
    image_urls = collect_request_image_references(request)
    if not image_urls:
        return None
    if not _is_tool_enabled(request, IMAGE_RECOGNITION_TOOL_NAME):
        return LeaderPlan(
            intent="image_understanding",
            target_agent="leader_agent",
            need_retrieval=False,
            rag_strategy="",
            answer="图片识别工具当前已关闭，请先在后台开启后再试。",
            action="direct_answer",
            route_reason="检测到图片资源，但图片识别工具已关闭，未执行工具调用。",
            route_mode="tool_disabled",
        )
    return LeaderPlan(
        intent="image_understanding",
        target_agent=IMAGE_RECOGNITION_AGENT_NAME,
        need_retrieval=False,
        rag_strategy="",
        action="call_tool",
        tool_name=IMAGE_RECOGNITION_TOOL_NAME,
        route_reason=f"检测到 {len(image_urls)} 个图片资源，自动调用图片识别工具。",
        route_mode="attachment",
    )


def _requested_file_transform_plan(request: RagQueryRequest) -> Optional[LeaderPlan]:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    interaction_type = str(metadata.get("interactionType") or "").strip().lower()
    requested_output_type = _normalize_requested_file_type(metadata.get("requestedOutputType") or "")
    supported_file_types = get_output_aliases()
    if requested_output_type not in supported_file_types:
        return None
    if interaction_type == "transform" and (
        not metadata.get("sourceMessageId") or not str(metadata.get("sourceMessageContent") or "").strip()
    ):
        return None
    if interaction_type == "transform" and requested_output_type in TEXT_TO_FILE_FORMAT_NAMES:
        tool_name = TEXT_TO_FILE_TOOL_BY_FORMAT[requested_output_type]
        tool_label = TEXT_TO_FILE_TOOL_LABELS.get(tool_name, "文本转文件工具")
        if not _is_tool_enabled(request, tool_name):
            return LeaderPlan(
                intent="document_export",
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="",
                answer=f"{tool_label}当前已关闭，请先在后台开启后再试。",
                action="direct_answer",
                route_reason=f"用户请求文本转文件，但{tool_label}已关闭，未执行工具调用。",
                route_mode="tool_disabled",
            )
        return LeaderPlan(
            intent="document_export",
            target_agent="leader_agent",
            need_retrieval=False,
            rag_strategy="",
            action="call_tool",
            tool_name=tool_name,
            route_reason=f"用户选择将当前消息按原文生成 {requested_output_type} 文件，调用已启用的{tool_label}。",
            route_mode="rules",
        )
    if not _is_tool_enabled(request, "generated_export_tools"):
        return LeaderPlan(
            intent="document_export",
            target_agent="leader_agent",
            need_retrieval=False,
            rag_strategy="",
            answer="内容导出工具当前已关闭，请先在后台开启后再试。",
            action="direct_answer",
            route_reason="用户请求文件导出，但内容导出工具已关闭，未执行工具调用。",
            route_mode="tool_disabled",
        )
    return LeaderPlan(
        intent="document_export",
        target_agent="leader_agent",
        need_retrieval=False,
        rag_strategy="",
        action="call_tool",
        tool_name="generated_export_tools",
        route_reason=f"用户选择将当前消息生成 {requested_output_type} 文件，直接调用已启用的内容导出工具。",
        route_mode="rules",
    )


def _prepare_request_input(request: RagQueryRequest) -> str:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    request.metadata = metadata
    metadata.setdefault("contextOriginalInput", str(request.input or ""))
    with_images = append_image_references_to_text(request.input, collect_request_image_references(request))
    return append_attachment_references_to_text(with_images, request.attachments)


_FILE_EXTRACTION_TOOL_BY_EXTENSION = {
    "md": "markdown_to_text_tool",
    "markdown": "markdown_to_text_tool",
    "txt": "txt_to_text_tool",
    "docx": "word_to_text_tool",
    "pptx": "ppt_to_text_tool",
    "pdf": "pdf_to_text_tool",
}
_INPUT_PIPELINE_MAX_IMAGES = 80
_INPUT_PIPELINE_STITCH_GROUP_SIZE = 9


def _should_run_attachment_input_pipeline(request: RagQueryRequest) -> bool:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    return bool(request.attachments) and not metadata.get("testFrom")


def _decode_attachment_content(raw: Dict[str, Any]) -> bytes:
    value = str(raw.get("contentBase64") or "").strip()
    if not value:
        return b""
    if value.lower().startswith("data:"):
        marker_index = value.find(",")
        if marker_index < 0 or ";base64" not in value[:marker_index].lower():
            return b""
        value = value[marker_index + 1:].strip()
    try:
        return base64.b64decode(value, validate=True)
    except (ValueError, base64.binascii.Error):
        return b""


def _attachment_extension(raw: Dict[str, Any]) -> str:
    name = str(raw.get("name") or raw.get("fileName") or "").strip()
    return name.rsplit(".", 1)[-1].lower() if "." in name else ""


def _attachment_is_image(raw: Dict[str, Any]) -> bool:
    mime_type = str(raw.get("mimeType") or raw.get("type") or "").lower()
    return mime_type.startswith("image/") or _attachment_extension(raw) in {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff",
    }


def _data_url_stitch_image(raw: Dict[str, Any], fallback_name: str) -> Optional[StitchImage]:
    data_url = str(raw.get("dataUrl") or "")
    match = re.match(r"^data:(image/[^;,]+);base64,(.+)$", data_url, re.IGNORECASE | re.DOTALL)
    if not match:
        return None
    try:
        content = base64.b64decode(match.group(2), validate=True)
    except (ValueError, base64.binascii.Error):
        return None
    return StitchImage(content, str(raw.get("name") or fallback_name), match.group(1).lower())


def _run_attachment_input_pipeline(
    request: RagQueryRequest,
    authorization: str,
    profile_context: Optional[Dict[str, Any]],
    callable_catalog: Optional[Dict[str, Any]],
) -> RagQueryResponse:
    """Normalize uploaded files/images before Leader performs business routing."""
    original_input = str((request.metadata or {}).get("contextOriginalInput") or request.input or "").strip()
    trace: List[RagTraceResponse] = []
    extracted_blocks: List[str] = []
    images: List[StitchImage] = []

    for index, raw in enumerate(request.attachments or [], start=1):
        if not isinstance(raw, dict):
            continue
        name = str(raw.get("name") or raw.get("fileName") or f"附件-{index}")
        content = _decode_attachment_content(raw)
        if not content:
            trace.append(RagTraceResponse(stage="input_attachment_skipped", detail={
                "triggerType": "system", "fileName": name,
                "reason": "附件缺少可供内部工具读取的文件内容",
            }))
            continue
        if _attachment_is_image(raw):
            images.append(StitchImage(content, name, str(raw.get("mimeType") or "image/png")))
            trace.append(RagTraceResponse(stage="input_image_collected", detail={
                "triggerType": "system", "fileName": name, "imageCount": 1,
            }))
            continue

        tool_name = _FILE_EXTRACTION_TOOL_BY_EXTENSION.get(_attachment_extension(raw))
        if not tool_name:
            trace.append(RagTraceResponse(stage="file_content_extraction_skipped", detail={
                "triggerType": "system", "fileName": name, "reason": "当前格式没有文件内容提取工具",
            }))
            continue
        if not _is_tool_enabled(request, tool_name):
            trace.append(RagTraceResponse(stage="file_content_extraction_skipped", detail={
                "triggerType": "system", "toolName": tool_name, "fileName": name, "reason": "工具已关闭",
            }))
            continue
        started_at = time.perf_counter()
        try:
            result = extract_file_content(tool_name, name, content)
        except FileContentExtractionError as exc:
            trace.append(RagTraceResponse(stage="file_content_extraction_failed", detail={
                "triggerType": "system", "toolName": tool_name, "fileName": name,
                "reason": str(exc), "durationMs": _elapsed_ms(started_at),
            }))
            continue
        text = str(result.get("text") or "").strip()
        if text:
            extracted_blocks.append(f"【文件：{name}】\n{text}")
        for image_index, image in enumerate(result.get("images") or [], start=1):
            decoded = _data_url_stitch_image(image, f"{name}-图片-{image_index}")
            if decoded:
                images.append(decoded)
        trace.append(RagTraceResponse(stage="file_content_extraction", detail={
            "triggerType": "system", "toolName": tool_name, "fileName": name,
            "mode": result.get("mode"), "textLength": result.get("textLength") or 0,
            "imageCount": result.get("imageCount") or 0, "durationMs": _elapsed_ms(started_at),
        }))

    if len(images) > _INPUT_PIPELINE_MAX_IMAGES:
        raise HTTPException(status_code=413, detail=f"单次最多处理 {_INPUT_PIPELINE_MAX_IMAGES} 张图片，请减少文件页数或附件数量。")

    vision_answer = ""
    if images:
        vision_inputs: List[str] = []
        groups = [images[offset:offset + _INPUT_PIPELINE_STITCH_GROUP_SIZE]
                  for offset in range(0, len(images), _INPUT_PIPELINE_STITCH_GROUP_SIZE)]
        trace.append(RagTraceResponse(stage="image_grouping", detail={
            "triggerType": "system", "imageCount": len(images), "groupSize": _INPUT_PIPELINE_STITCH_GROUP_SIZE,
            "groupCount": len(groups), "groupImageCounts": [len(group) for group in groups],
        }))
        for group_index, group in enumerate(groups, start=1):
            started_at = time.perf_counter()
            if len(group) == 1:
                blob = group[0].content
                mime_type = group[0].mime_type or "image/png"
                stage = "image_stitching_skipped"
            else:
                blob = stitch_images(group, columns=3)
                mime_type = "image/png"
                stage = "image_stitching_tool"
            vision_inputs.append(f"data:{mime_type};base64,{base64.b64encode(blob).decode('ascii')}")
            trace.append(RagTraceResponse(stage=stage, detail={
                "triggerType": "system", "toolName": IMAGE_STITCHING_TOOL["name"],
                "groupIndex": group_index, "inputCount": len(group), "outputCount": 1,
                "sourceNames": [item.name for item in group], "durationMs": _elapsed_ms(started_at),
                "reason": "单张图片无需拼接" if len(group) == 1 else "按每组最多9张自动拼接",
            }))

        if _is_tool_enabled(request, IMAGE_RECOGNITION_TOOL_NAME):
            vision_request = request.model_copy(deep=True)
            vision_request.attachments = []
            vision_request.imageUrls = []
            vision_request.images = []
            vision_request.imageDataUrls = vision_inputs
            vision_request.input = append_image_references_to_text(
                f"{original_input or '请识别并汇总上传内容。'}\n请按拼接图中的编号和顺序识别内容。",
                vision_inputs,
            )
            started_at = time.perf_counter()
            vision_plan = LeaderPlan(
                intent="image_understanding", target_agent=IMAGE_RECOGNITION_AGENT_NAME,
                need_retrieval=False, rag_strategy="", action="call_tool",
                tool_name=IMAGE_RECOGNITION_TOOL_NAME,
                route_reason="输入预处理检测到图片，系统自动调用图片识别工具。",
                route_mode="input_pipeline",
            )
            vision_answer, vision_model_metadata = _run_specialist_agent_with_bound_model(
                vision_request, IMAGE_RECOGNITION_AGENT_NAME, vision_request.input, [], leader_plan=vision_plan,
            )
            trace.extend([
                RagTraceResponse(stage="tool_call", detail={
                    "triggerType": "system", "toolName": IMAGE_RECOGNITION_TOOL_NAME,
                    "toolDisplayName": _tool_display_name(IMAGE_RECOGNITION_TOOL_NAME),
                    "imageCount": len(vision_inputs), "boundAgent": IMAGE_RECOGNITION_AGENT_NAME,
                }),
                RagTraceResponse(stage="vision_agent", detail={
                    "triggerType": "workflow_dependency", "agentName": IMAGE_RECOGNITION_AGENT_NAME,
                    "inputImageCount": len(vision_inputs), "answerLength": len(vision_answer or ""),
                    "durationMs": _elapsed_ms(started_at), **vision_model_metadata,
                }),
            ])

    context_parts = [original_input]
    if extracted_blocks:
        context_parts.append("文件内容提取结果：\n" + "\n\n".join(extracted_blocks))
    if vision_answer:
        context_parts.append("图片识别结果：\n" + vision_answer)
    enriched_input = "\n\n".join(part for part in context_parts if part).strip()
    trace.append(RagTraceResponse(stage="multimodal_context_merged", detail={
        "triggerType": "system", "fileTextCount": len(extracted_blocks),
        "sourceImageCount": len(images), "visionResultAvailable": bool(vision_answer),
        "contextLength": len(enriched_input),
    }))

    leader_request = request.model_copy(deep=True)
    leader_request.input = enriched_input or original_input
    leader_request.attachments = []
    leader_request.imageUrls = []
    leader_request.images = []
    leader_request.imageDataUrls = []
    leader_plan = leader_agent.plan(
        leader_request.input, "", profile_context=profile_context,
        callable_catalog=callable_catalog, conversation_context=(request.metadata or {}).get("conversationContext") or {},
    )
    response = _execute_leader_plan(
        leader_request, authorization, profile_context, leader_plan, callable_catalog=callable_catalog,
    )
    response.trace = trace + list(response.trace or [])
    response.metadata = {
        **dict(response.metadata or {}),
        "inputPipelineApplied": True,
        "inputPipelineImageCount": len(images),
        "inputPipelineFileCount": len(extracted_blocks),
    }
    _set_response_route_mode(response, leader_plan)
    return response


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
    knowledge_source_choice = _contextualize_knowledge_source_choice(text, compact, context)
    if knowledge_source_choice:
        return knowledge_source_choice
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


def _contextualize_knowledge_source_choice(input_text: str, compact_text: str, context: Dict[str, Any]) -> str:
    if not compact_text:
        return ""
    turns = [turn for turn in (context.get("turns") or []) if isinstance(turn, dict)]
    authorized_turn = next(
        (
            turn for turn in reversed(turns)
            if str((turn.get("metadata") or {}).get("knowledgeSourceMode") or "").strip() == "model_generated"
        ),
        None,
    )
    contextual_continue = compact_text in {
        "继续", "继续生成", "接着生成", "按这个生成", "就这样生成", "可以", "确认", "确定",
    }
    if authorized_turn and contextual_continue:
        authorized_topic = str((authorized_turn.get("metadata") or {}).get("knowledgeTopic") or "").strip()
        authorized_topic = authorized_topic or str(authorized_turn.get("user") or "").strip() or "当前主题"
        return f"用户此前已经授权模型在无材料时自行生成知识材料，无需再次确认来源。请继续处理原始主题：{authorized_topic}"

    selection_turn = next(
        (
            turn for turn in reversed(turns)
            if str((turn.get("metadata") or {}).get("knowledgeSourceMode") or "").strip() == "source_selection_required"
            or _looks_like_knowledge_source_prompt(str(turn.get("assistant") or ""))
        ),
        None,
    )
    if not selection_turn:
        return ""

    assistant_prompt = str(selection_turn.get("assistant") or "")
    explicit_model_choice = any(token in compact_text for token in (
        "授权模型", "模型自行生成", "自行生成", "自己生成", "直接生成", "不用材料", "无需材料",
    ))
    second_choice = compact_text in {"2", "选2", "选择2", "第二种", "第二种方式", "方式二", "第二项", "选第二个"}
    third_choice = compact_text in {"3", "选3", "选择3", "第三种", "第三种方式", "方式三", "第三项", "选第三个"}
    third_model_option = any(token in assistant_prompt for token in (
        "3. 授权", "3、授权", "3.授权", "3．授权", "第三种方式",
    ))
    second_is_model = second_choice and (
        "两种" in assistant_prompt
        or "2. 授权" in assistant_prompt
        or "2、授权" in assistant_prompt
        or "2.授权" in assistant_prompt
        or "第二种方式" in assistant_prompt
        or not third_model_option
    )
    third_is_model = third_choice and third_model_option
    if not (explicit_model_choice or second_is_model or third_is_model):
        return ""

    topic = str((selection_turn.get("metadata") or {}).get("knowledgeTopic") or "").strip()
    if not topic:
        for turn in reversed(turns):
            candidate = str(turn.get("user") or "").strip()
            candidate_compact = normalize_text(candidate)
            if candidate and candidate != input_text and not _is_bare_source_choice(candidate_compact):
                topic = candidate
                break
    topic = topic or _latest_context_subject(context) or "当前主题"
    return f"我明确授权模型在没有上传材料或知识库证据时自行生成知识材料。请继续处理原始主题：{topic}"


def _looks_like_knowledge_source_prompt(answer: str) -> bool:
    normalized = normalize_text(answer)
    return (
        any(token in normalized for token in ("知识来源", "来源方式", "上传材料", "知识库内容"))
        and any(token in normalized for token in ("授权模型", "自行生成", "自己生成"))
    )


def _is_bare_source_choice(compact_text: str) -> bool:
    return compact_text in {
        "1", "2", "3", "选1", "选2", "选3", "选择1", "选择2", "选择3",
        "第一种", "第二种", "第三种", "第一种方式", "第二种方式", "第三种方式",
        "方式一", "方式二", "方式三", "第一项", "第二项", "第三项",
    }


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
    callable_catalog: Optional[Dict[str, Any]] = None,
) -> RagQueryResponse:
    if (
        plan.action == "call_tool"
        and plan.tool_name in VISUAL_GENERATION_TOOL_NAMES
        and _normalize_requested_file_type(_requested_file_type_from_text(request.input)) == "docx"
        and _is_tool_enabled(request, "generated_export_tools")
    ):
        response = _run_visual_docx_workflow(request, plan)
        _inject_tool_selection_into_response(response, callable_catalog)
        return response
    if plan.action == "direct_answer":
        response = _run_leader_direct_answer(plan, profile_context=profile_context)
        _inject_tool_selection_into_response(response, callable_catalog)
        return response
    if plan.action == "call_tool":
        if not _is_tool_enabled(request, plan.tool_name):
            return _run_disabled_tool_response(request, plan.tool_name, leader_plan=plan)
        if plan.tool_name == TOOL_CAPABILITY_QUERY_NAME:
            response = _run_tool_capability_query(request, plan)
        elif plan.tool_name == "text_to_sql":
            response = _run_text_to_sql_tool(request, plan)
        elif plan.tool_name in SERVICE_TOOL_NAMES:
            response = _run_service_tool(request, authorization, plan)
        elif plan.tool_name == "generated_export_tools":
            response = _run_generated_export_tool(request, plan)
        elif plan.tool_name == IMAGE_STITCHING_TOOL["name"]:
            response = _run_image_stitching_tool(request, plan)
        elif plan.tool_name in TEXT_TO_FILE_TOOL_NAMES:
            response = _run_text_to_file_tool(request, plan)
        elif plan.tool_name == IMAGE_RECOGNITION_TOOL_NAME:
            response = _run_image_recognition_tool(request, plan)
        elif plan.tool_name in VISUAL_GENERATION_TOOL_NAMES:
            response = _run_visual_generation_tool(request, plan)
        else:
            raise HTTPException(status_code=502, detail=f"Leader 选择了未注册工具：{plan.tool_name or '空'}，已停止执行。")
        _inject_tool_selection_into_response(response, callable_catalog)
        return response

    raise HTTPException(status_code=502, detail=f"Leader 只允许直接回答或调用系统工具，已拒绝动作：{plan.action}")


def _run_visual_docx_workflow(request: RagQueryRequest, visual_plan: LeaderPlan) -> RagQueryResponse:
    """Execute the common multi-tool workflow: generate an image, organize content, then export DOCX."""
    visual_response = _run_visual_generation_tool(request, visual_plan)
    image_bytes: List[bytes] = []
    for attachment in visual_response.attachments or []:
        storage_key = str(attachment.get("storageKey") or "")
        capability = str(attachment.get("internalCapability") or "")
        if not storage_key or not capability:
            continue
        export_file = open_generated_export(storage_key, capability)
        try:
            image_bytes.append(export_file.stream.read())
        finally:
            export_file.stream.close()

    export_request = request.model_copy(deep=True)
    export_request.metadata = dict(export_request.metadata or {})
    export_request.metadata.update({
        "requestedOutputType": "docx",
        "sourceMessageContent": request.input,
        "embeddedImageBytes": image_bytes,
    })
    export_plan = LeaderPlan(
        intent="document_export",
        target_agent="leader_agent",
        need_retrieval=False,
        rag_strategy="",
        action="call_tool",
        tool_name="generated_export_tools",
        route_reason="图片生成完成，继续调用内容整理和 Word 导出工具。",
    )
    export_response = _run_generated_export_tool(export_request, export_plan)
    export_response.attachments = [*(visual_response.attachments or []), *(export_response.attachments or [])]
    export_response.trace = [
        *(visual_response.trace or []),
        RagTraceResponse(stage="workflow_dependency", detail={
            "triggerType": "workflow_dependency",
            "fromTool": visual_plan.tool_name,
            "toAgent": "file_content_planner_agent",
            "reason": "生成 Word 前先整理内容和配图布局。",
        }),
        *(export_response.trace or []),
    ]
    export_response.answer = f"{visual_response.answer}\n\n{export_response.answer}".strip()
    export_response.metadata = dict(export_response.metadata or {})
    export_response.metadata.update({
        "executionMode": "leader_multi_tool_workflow",
        "executionModeLabel": "Leader 协调图片生成、内容整理和 Word 导出",
        "workflowTools": [visual_plan.tool_name, "file_content_planner_agent", "docx_export_tool"],
        "generatedImageCount": len(image_bytes),
    })
    return export_response


def _inject_tool_selection_into_response(response: RagQueryResponse, callable_catalog: Optional[Dict[str, Any]]) -> None:
    """Inject toolSelection (candidate tools with scores) into response metadata for monitoring."""
    if not callable_catalog or not isinstance(callable_catalog, dict):
        return
    tool_selection = callable_catalog.get("toolSelection")
    if not isinstance(tool_selection, dict):
        return
    if response.metadata is None:
        response.metadata = {}
    response.metadata["toolSelection"] = tool_selection


def _should_emit_generation_start(request: RagQueryRequest, agent_name: Optional[str], plan=None) -> bool:
    if plan is not None and getattr(plan, "action", "") == "call_tool":
        tool_name = str(getattr(plan, "tool_name", "") or "").strip()
        return tool_name in VISUAL_GENERATION_TOOL_NAMES and _is_tool_enabled(request, tool_name)
    return False


def _build_generation_start_payload(
    request: RagQueryRequest,
    plan=None,
    agent_name: Optional[str] = None,
) -> Dict[str, Any]:
    visual_tool_name = ""
    if plan is not None and getattr(plan, "action", "") == "call_tool":
        candidate = str(getattr(plan, "tool_name", "") or "").strip()
        if candidate in VISUAL_GENERATION_TOOL_NAMES:
            visual_tool_name = candidate
    requested_agent = normalize_agent_name(agent_name or getattr(plan, "target_agent", "")) or ""
    prompt_agent = str(VISUAL_GENERATION_TOOL_CONFIG[visual_tool_name].get("promptAgent") or "") if visual_tool_name else ""
    target_agent = visual_tool_name or requested_agent
    profile = get_agent_profile("image_agent") or {}
    runtime_config, config_prefix = _require_agent_runtime_config(request, "image_agent", leader_plan=plan)
    model_metadata = _agent_model_metadata(runtime_config, config_prefix)
    session_id = str((request.metadata or {}).get("sessionId") or "")
    intent = getattr(plan, "intent", "") or profile.get("intent") or ""
    route_reason = getattr(plan, "route_reason", "") or profile.get("purpose") or ""
    answer_type = "image_generation" if visual_tool_name else _answer_type_for_agent(target_agent)
    role = str(profile.get("role") or target_agent or "图片智能体")
    answer = f"已识别到你要生成图片，正在调用「{role}」处理中。你可以继续提问，生成完成后我会把结果更新到这里。"
    metadata = {
        "agentName": "leader_agent" if plan else target_agent,
        "targetAgent": target_agent,
        "executedAgent": target_agent,
        "promptAgent": prompt_agent,
        "imageAgent": "image_agent" if visual_tool_name else "",
        "toolName": visual_tool_name,
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
    if normalized in INTERNAL_ONLY_AGENT_NAMES:
        # 系统必经的内部智能体不受普通智能体开关控制；模型配置校验由执行阶段统一给出明确错误。
        return True
    toggles = _agent_toggles_from_request(request)
    if normalized in toggles and not _parse_agent_enabled_value(toggles.get(normalized)):
        return False
    return _is_agent_model_ready(request, normalized)


def _is_agent_model_ready(request: RagQueryRequest, agent_name: str) -> bool:
    metadata = request.metadata if isinstance(request.metadata, dict) else {}
    configs = metadata.get("agentModelConfigs")
    if not isinstance(configs, dict):
        return False
    config = configs.get(agent_name)
    if not isinstance(config, dict) or config.get("tested") is not True:
        return False
    required_fields = (
        config.get("provider"),
        config.get("baseUrl") or config.get("base_url"),
        config.get("apiKey") or config.get("api_key"),
        config.get("model"),
    )
    return all(str(value or "").strip() for value in required_fields)


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
        return False
    if normalized == TOOL_CAPABILITY_QUERY_NAME:
        return True
    toggles = _tool_toggles_from_request(request)
    if normalized not in toggles:
        enabled = True
    else:
        enabled = _parse_agent_enabled_value(toggles.get(normalized))
    if not enabled:
        return False
    # 绑定智能体属于工具内部实现细节，不再作为 Leader 工具目录的二次开关。
    # Leader 是否可以调用，只由后台的工具开关决定。
    return True


def _require_tool_enabled(request: RagQueryRequest, tool_name: str) -> None:
    if _is_tool_enabled(request, tool_name):
        return
    raise HTTPException(status_code=403, detail=f"工具 {tool_name} 已在后台关闭，Leader 本次不会调用。")


def _build_leader_callable_catalog(request: Optional[RagQueryRequest] = None) -> Dict[str, Any]:
    # 所有能力统一进入 tools；内容导出工具只通过 category=content_export 区分。
    tool_by_name = {str(tool.get("name") or "").strip(): tool for tool in LEADER_CALLABLE_TOOLS}
    for tool in GENERATED_CONTENT_TOOLS:
        tool_by_name.setdefault(str(tool.get("name") or "").strip(), tool)
    # 运行时目录只暴露当前已启用的工具；禁用项留在后台管理接口，不进入 Leader 上下文。
    available_tools = [
        _leader_callable_tool_item(tool, request)
        for tool in tool_by_name.values()
        if tool.get("name") and (
            request is None or _is_tool_enabled(request, str(tool.get("name") or "").strip())
        )
    ]
    selection = {
        "intent": "",
        "keywords": [],
        "entities": {},
        "constraints": [],
        "queryVariants": [],
        "candidateTools": available_tools,
        "candidateCount": len(available_tools),
        "topK": len(available_tools),
    }
    all_tool_scores: List[Dict[str, Any]] = []
    if request is not None:
        intent_result = tool_intent_router_agent.extract(getattr(request, "input", ""))
        metadata = request.metadata if isinstance(request.metadata, dict) else {}
        model_configs = metadata.get("agentModelConfigs") if isinstance(metadata.get("agentModelConfigs"), dict) else {}
        router_config = model_configs.get("tool_intent_router_agent") if isinstance(model_configs, dict) else None
        if isinstance(router_config, dict) and router_config.get("tested") is True:
            try:
                model_answer, _ = _run_specialist_agent_with_bound_model(
                    request,
                    "tool_intent_router_agent",
                    getattr(request, "input", ""),
                    [],
                )
                parsed_result = tool_intent_router_agent.parse_model_result(model_answer)
                if parsed_result:
                    intent_result = parsed_result
            except Exception as exc:
                logger.warning("tool intent model extraction failed; using local extraction: %s", exc)
        # 能力询问是系统级固定路由，不能因为模型输出了不完整或错误的 intent
        # 就退回旧的 Leader 直接回答逻辑。
        if tool_intent_router_agent.is_capability_query(getattr(request, "input", "")):
            intent_result = {
                **intent_result,
                "intent": "capability_inquiry",
            }
        selection = tool_index.search(
            getattr(request, "input", ""),
            available_tools,
            intent_result=intent_result,
            retrieval_profiles=metadata.get("toolRetrievalProfiles") if isinstance(metadata.get("toolRetrievalProfiles"), dict) else {},
            top_k=3,
        )
        # 全量打分：给所有已启用工具打分（含 0 分），供监控页面展示完整快照
        all_tool_scores = tool_index.score_all_tools(
            getattr(request, "input", ""),
            available_tools,
            retrieval_profiles=metadata.get("toolRetrievalProfiles") if isinstance(metadata.get("toolRetrievalProfiles"), dict) else {},
        )
    is_capability_inquiry = selection.get("intent") == "capability_inquiry"
    # 能力询问由系统固定工具路由，不把工具本身或业务工具清单发送给 Leader。
    # 普通问题仍只保留索引后的少量候选工具。
    tools = [] if is_capability_inquiry else (selection.get("candidateTools") or [])
    return {
        "routingActions": ["direct_answer", "call_tool"],
        "tools": tools,
        "toolSelection": {
            "intent": selection.get("intent") or "direct_answer",
            "keywords": selection.get("keywords") or [],
            "entities": selection.get("entities") or {},
            "constraints": selection.get("constraints") or [],
            "queryVariants": selection.get("queryVariants") or [],
            "candidateTools": tools,
            "candidateCount": len(tools),
            "allToolScores": all_tool_scores,
            "fixedRoute": TOOL_CAPABILITY_QUERY_NAME if is_capability_inquiry else "",
        },
        "summary": {
        "toolCount": len(tools),
        "fixedRoute": TOOL_CAPABILITY_QUERY_NAME if is_capability_inquiry else "",
        },
        "routingRule": "普通问题由 Leader 从 tools 候选中选择系统工具；能力询问由 toolSelection.fixedRoute 固定调用能力查询工具；专业智能体不作为独立路由目标。",
    }


def _run_tool_capability_query(request: RagQueryRequest, leader_plan) -> RagQueryResponse:
    """查询后台开关后的能力，不把完整工具目录交给 Leader。"""
    tool_by_name = {
        str(tool.get("name") or "").strip(): tool
        for tool in [*LEADER_CALLABLE_TOOLS, *GENERATED_CONTENT_TOOLS]
        if str(tool.get("name") or "").strip()
    }
    enabled_tools = [
        tool
        for name, tool in tool_by_name.items()
        if name != TOOL_CAPABILITY_QUERY_NAME and _is_tool_enabled(request, name)
    ]
    documents: List[Dict[str, Any]] = []
    for tool in enabled_tools:
        name = str(tool.get("name") or "").strip()
        item = {
            "name": name,
            "displayName": tool.get("zhName") or tool.get("displayName") or _tool_display_name(name),
            "category": str(tool.get("category") or "other").strip(),
            "purpose": str(tool.get("purpose") or "").strip(),
            "outputs": tool.get("outputs") or [],
        }
        documents.append({
            "id": f"capability:{name}",
            "type": "tool_capability",
            "title": item["displayName"],
            "content": item["purpose"],
            "metadata": item,
        })

    tool_result = {
        "type": "tool_capability_result",
        "enabledToolCount": len(enabled_tools),
        "enabledTools": enabled_tools,
    }
    try:
        answer = leader_agent.summarize_tool_result(
            input_text=request.input,
            plan=leader_plan,
            tool_display_name=_tool_display_name(TOOL_CAPABILITY_QUERY_NAME),
            tool_results=[tool_result],
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail="模型未能总结工具能力查询结果，已禁止直接透传工具内容。") from exc
    if not str(answer or "").strip():
        raise HTTPException(status_code=502, detail="模型返回空的工具能力总结，已禁止直接透传工具内容。")

    metadata = {
        "agentName": "leader_agent",
        "targetAgent": TOOL_CAPABILITY_QUERY_NAME,
        "executedAgent": TOOL_CAPABILITY_QUERY_NAME,
        "intent": "capability_inquiry",
        "needRetrieval": False,
        "retrievalSkipped": True,
        "leaderAction": "call_tool",
        "leaderActionLabel": _leader_action_label("call_tool"),
        "toolName": TOOL_CAPABILITY_QUERY_NAME,
        "toolDisplayName": _tool_display_name(TOOL_CAPABILITY_QUERY_NAME),
        "routeReason": getattr(leader_plan, "route_reason", "用户询问系统能力，调用能力查询工具。"),
        "strategyLabel": "工具能力查询",
        "executionMode": "leader_call_tool",
        "executionModeLabel": "调用工具查询当前已启用能力",
        "answerType": "capability_list",
        "toolResultSummarized": True,
        "toolResultSummaryMode": "model",
        "toolToggles": _tool_toggles_from_request(request),
        "enabledToolCount": len(enabled_tools),
        "retrievalCandidateCount": 1,
    }
    metadata.update(_context_metadata_from_request(request))
    return _decorate_output_response(RagQueryResponse(
        strategy=TOOL_CAPABILITY_QUERY_NAME,
        answer=answer,
        answerType="capability_list",
        documents=[_tool_result_to_document(item, index) for index, item in enumerate(documents, start=1)],
        trace=[
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
            RagTraceResponse(stage="tool_call", detail={
                "toolName": TOOL_CAPABILITY_QUERY_NAME,
                "enabledToolCount": len(enabled_tools),
                "retrievalSkipped": True,
            }),
            RagTraceResponse(stage="tool_result_summary", detail={
                "toolResultSummaryMode": "model",
                "answerLength": len(answer),
            }),
        ],
        metadata=metadata,
    ))


def _leader_callable_agent_item(agent_name: str, request: Optional[RagQueryRequest]) -> Dict[str, Any]:
    profile = get_agent_profile(agent_name)
    if not profile:
        return {}
    if request is None:
        enabled = True
    else:
        enabled = _is_agent_enabled(request, agent_name)
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
    annotated = _annotate_tool_trigger(tool)
    name = str(annotated.get("name") or "").strip()
    return {
        "name": name,
        "zhName": annotated.get("zhName") or _tool_zh_name(name),
        "displayName": annotated.get("displayName") or _tool_display_name(name),
        "category": annotated.get("category") or "",
        "purpose": annotated.get("purpose") or "",
        "trigger": annotated.get("trigger") or "",
        "outputs": annotated.get("outputs") or [],
        "triggerType": annotated.get("triggerType"),
        "pipelineStage": annotated.get("pipelineStage"),
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
    raise HTTPException(status_code=403, detail=f"智能体 {role}（{normalized}）已在后台关闭，本次未执行。")


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

    profile = get_agent_profile(normalized_agent) or {}
    required_modalities = profile.get("requiredModelModalities") or []
    if "vision" in required_modalities:
        from app.model_providers.catalog import model_supports_vision

        if not model_supports_vision(provider, model):
            raise AgentExecutionError(
                message=(
                    f"{display_name} 需要绑定支持视觉理解的模型（例如 Qwen-VL 或 deepseek-v4-flash-vision-exp），"
                    f"当前绑定的 {model or '未命名模型'} 不支持图片输入。"
                ),
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


def _run_visual_generation_tool(
    request: RagQueryRequest,
    leader_plan,
) -> RagQueryResponse:
    tool_name = str(getattr(leader_plan, "tool_name", "") or "").strip()
    config = VISUAL_GENERATION_TOOL_CONFIG.get(tool_name)
    if not config:
        raise HTTPException(status_code=502, detail=f"未注册的视觉生成工具：{tool_name or '空'}")
    prompt_agent = str(config.get("promptAgent") or "").strip()
    if not _is_agent_enabled(request, "image_agent"):
        return _run_disabled_tool_response(request, tool_name, leader_plan=leader_plan)
    if prompt_agent and not _is_agent_enabled(request, prompt_agent):
        return _run_disabled_tool_response(request, tool_name, leader_plan=leader_plan)

    evidence = _profile_evidence_from_request(request)
    prompt_text = request.input
    prompt_model_metadata: Dict[str, Any] = {}
    if prompt_agent:
        prompt_text, prompt_model_metadata = _run_specialist_agent_with_bound_model(
            request,
            prompt_agent,
            request.input,
            evidence,
            leader_plan=leader_plan,
        )
    image_answer, image_model_metadata = _run_specialist_agent_with_bound_model(
        request,
        "image_agent",
        prompt_text,
        [],
        leader_plan=leader_plan,
    )
    image_answer, image_attachments = materialize_generated_image_answer(
        image_answer,
        display_stem=str(config.get("zhName") or "生成图片").removesuffix("工具"),
        tool_name=tool_name,
    )
    metadata = {
        "agentName": "leader_agent",
        "targetAgent": tool_name,
        "executedAgent": tool_name,
        "toolName": tool_name,
        "toolDisplayName": _tool_display_name(tool_name),
        "promptAgent": prompt_agent,
        "imageAgent": "image_agent",
        "intent": getattr(leader_plan, "intent", "") or "image_generation",
        "needRetrieval": False,
        "retrievalSkipped": True,
        "strategyLabel": config.get("purpose") or _tool_display_name(tool_name),
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用视觉生成工具",
        "answerType": "image_generation",
        "profileContextUsed": bool(evidence),
        "outputPreferenceHints": _output_preference_hints_from_request(request),
        "toolToggles": _tool_toggles_from_request(request),
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "routeReason": leader_plan.route_reason,
        "promptModelProvider": prompt_model_metadata.get("modelProvider") or "",
        "promptModel": prompt_model_metadata.get("model") or "",
        **image_model_metadata,
    }
    metadata.update(_context_metadata_from_request(request))
    trace = [
        RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
        RagTraceResponse(stage="tool_call", detail={
            "toolName": tool_name,
            "toolDisplayName": _tool_display_name(tool_name),
            "promptAgent": prompt_agent,
            "imageAgent": "image_agent",
        }),
    ]
    if prompt_agent:
        trace.append(RagTraceResponse(stage="prompt_agent", detail={
            "agentName": prompt_agent,
            "output": "image_prompt",
            "answerLength": len(prompt_text or ""),
            **prompt_model_metadata,
        }))
    trace.append(RagTraceResponse(stage="image_generation_tool", detail={
        "agentName": "image_agent",
        "input": "prompt_agent_output" if prompt_agent else "user_input",
        "answerLength": len(image_answer or ""),
        **image_model_metadata,
    }))
    return _decorate_output_response(RagQueryResponse(
        strategy=tool_name,
        answer=image_answer,
        answerType="image_generation",
        documents=[],
        trace=trace,
        metadata=metadata,
        attachments=image_attachments,
    ))


def _run_image_stitching_tool(
    request: RagQueryRequest,
    leader_plan,
) -> RagQueryResponse:
    images = collect_stitch_images(request)
    if len(images) < 2:
        raise HTTPException(status_code=400, detail="图片拼接工具需要至少两张图片资源。")
    try:
        stitched = stitch_images(images, columns=3)
    except ImageStitchingError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    encoded = base64.b64encode(stitched).decode("ascii")
    _, attachments = materialize_generated_image_answer(
        json.dumps({
            "status": "success",
            "message": "图片拼接完成",
            "images": [{
                "index": 0,
                "status": "success",
                "contentType": "image/png",
                "base64": encoded,
            }],
        }, ensure_ascii=False),
        display_stem="图片拼接结果",
        tool_name=IMAGE_STITCHING_TOOL["name"],
    )
    # Admin tool tests preview images through the export download API instead of
    # embedding another base64 copy in the JSON response.
    metadata = {
        "agentName": "leader_agent",
        "targetAgent": IMAGE_STITCHING_TOOL["name"],
        "executedAgent": IMAGE_STITCHING_TOOL["name"],
        "toolName": IMAGE_STITCHING_TOOL["name"],
        "toolDisplayName": IMAGE_STITCHING_TOOL["displayName"],
        "imageCount": len(images),
        "sourceNames": [item.name for item in images],
        "layout": "grid",
        "columns": min(3, len(images)),
        "numbered": True,
        "intent": getattr(leader_plan, "intent", "") or "image_stitching",
        "needRetrieval": False,
        "retrievalSkipped": True,
        "strategyLabel": "图片拼接工具",
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用图片拼接工具",
        "answerType": "image_stitching",
        "toolToggles": _tool_toggles_from_request(request),
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "routeReason": leader_plan.route_reason,
    }
    metadata.update(_context_metadata_from_request(request))
    trace = [
        RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
        RagTraceResponse(stage="tool_call", detail={
            "toolName": IMAGE_STITCHING_TOOL["name"],
            "toolDisplayName": IMAGE_STITCHING_TOOL["displayName"],
            "imageCount": len(images),
            "layout": metadata["layout"],
            "columns": metadata["columns"],
            "numbered": metadata["numbered"],
        }),
        RagTraceResponse(stage="image_stitching_tool", detail={
            "imageCount": len(images),
            "layout": metadata["layout"],
            "columns": metadata["columns"],
            "numbered": metadata["numbered"],
            "outputCount": len(attachments),
        }),
    ]
    return _decorate_output_response(RagQueryResponse(
        strategy=IMAGE_STITCHING_TOOL["name"],
        answer=f"已按上传顺序以最多 3 列的自适应网格拼接 {len(images)} 张图片，并在每张图片左侧标注顺序编号。",
        answerType="image_stitching",
        documents=[],
        trace=trace,
        metadata=metadata,
        attachments=attachments,
    ))


def _run_image_recognition_tool(
    request: RagQueryRequest,
    leader_plan,
) -> RagQueryResponse:
    image_urls = collect_request_image_references(request)
    if not image_urls:
        raise HTTPException(status_code=400, detail="图片识别工具需要至少一个图片资源。")
    _, embedded_urls = extract_image_references(request.input)
    if not embedded_urls:
        request.input = append_image_references_to_text(request.input, image_urls)
        _, embedded_urls = extract_image_references(request.input)
    if not embedded_urls:
        raise HTTPException(status_code=400, detail="图片识别工具未能将上传图片注入请求上下文。")
    answer, model_metadata = _run_specialist_agent_with_bound_model(
        request,
        IMAGE_RECOGNITION_AGENT_NAME,
        request.input,
        [],
        leader_plan=leader_plan,
    )
    metadata = {
        "agentName": "leader_agent",
        "targetAgent": IMAGE_RECOGNITION_AGENT_NAME,
        "executedAgent": IMAGE_RECOGNITION_AGENT_NAME,
        "toolName": IMAGE_RECOGNITION_TOOL_NAME,
        "toolDisplayName": _tool_display_name(IMAGE_RECOGNITION_TOOL_NAME),
        "boundAgent": IMAGE_RECOGNITION_AGENT_NAME,
        "imageCount": len(image_urls),
        "intent": getattr(leader_plan, "intent", "") or "image_understanding",
        "needRetrieval": False,
        "retrievalSkipped": True,
        "strategyLabel": "图片识别",
        "executionMode": "leader_call_tool",
        "executionModeLabel": "Leader 调用图片识别工具",
        "answerType": "image_analysis",
        "toolToggles": _tool_toggles_from_request(request),
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "routeReason": leader_plan.route_reason,
        **model_metadata,
    }
    metadata.update(_context_metadata_from_request(request))
    trace = [
        RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
        RagTraceResponse(stage="tool_call", detail={
            "toolName": IMAGE_RECOGNITION_TOOL_NAME,
            "toolDisplayName": _tool_display_name(IMAGE_RECOGNITION_TOOL_NAME),
            "boundAgent": IMAGE_RECOGNITION_AGENT_NAME,
            "imageCount": len(image_urls),
        }),
        RagTraceResponse(stage="vision_agent", detail={
            "agentName": IMAGE_RECOGNITION_AGENT_NAME,
            "answerLength": len(answer or ""),
            "multimodalImageCount": len(embedded_urls),
            **model_metadata,
        }),
    ]
    return _decorate_output_response(RagQueryResponse(
        strategy=IMAGE_RECOGNITION_TOOL_NAME,
        answer=answer,
        answerType="image_analysis",
        documents=[],
        trace=trace,
        metadata=metadata,
    ))


def _run_direct_agent(
    request: RagQueryRequest,
    agent_profile: Dict[str, Any],
    leader_plan=None,
) -> RagQueryResponse:
    agent_name = agent_profile["name"]
    profile_evidence = _profile_evidence_from_request(request)
    knowledge_source_mode = (
        resolve_knowledge_source_mode(request.input, profile_evidence)
        if agent_name == "textbook_knowledge_agent"
        else ""
    )
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
        "intent": "knowledge_source_clarification" if knowledge_source_mode == "source_selection_required" else agent_profile["intent"],
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
    if knowledge_source_mode:
        metadata.update({
            "knowledgeSourceMode": knowledge_source_mode,
            "knowledgeTopic": request.input,
            "modelGeneratedMaterial": knowledge_source_mode == "model_generated",
            "materialSourceLabel": {
                "provided_material": "用户材料或知识库证据",
                "model_generated": "模型根据用户主题生成",
                "source_selection_required": "等待用户选择材料来源",
            }[knowledge_source_mode],
        })
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
    is_image_agent = agent_name == "image_agent"
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
        agent_name = _normalize_requested_agent(request) or "leader_agent"
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
    try:
        answer = leader_agent.summarize_tool_result(
            input_text=request.input,
            plan=leader_plan,
            tool_display_name=_tool_display_name(leader_plan.tool_name),
            tool_results=[{"type": "text_to_sql_result", **metadata}],
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail="模型未能生成 Text-to-SQL 结果回复，已禁止系统兜底回复。") from exc
    if not str(answer or "").strip():
        raise HTTPException(status_code=502, detail="模型返回空的 Text-to-SQL 结果回复，已禁止系统兜底回复。")
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
    request_metadata = request.metadata if isinstance(request.metadata, dict) else {}
    requested_output_type = _normalize_requested_file_type(
        request_metadata.get("requestedOutputType") or _requested_file_type_from_text(request.input) or "document"
    )
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
        "requestedOutputType": requested_output_type,
        "allowGeneratedExportTool": True,
        "toolToggles": _tool_toggles_from_request(request),
    }
    if isinstance(request_metadata.get("embeddedImageBytes"), list):
        metadata["embeddedImageBytes"] = request_metadata["embeddedImageBytes"]
    metadata.update(_context_metadata_from_request(request))
    source_content = str(request_metadata.get("sourceMessageContent") or "").strip()
    planner_payload = json.dumps({
        "userRequest": str((request.metadata or {}).get("contextOriginalInput") or request.input or ""),
        "targetFormat": requested_output_type,
        "sourceContent": source_content,
        "sourceCandidates": request_metadata.get("sourceMessageCandidates") or [],
        "conversationContext": request_metadata.get("conversationContext") or {},
    }, ensure_ascii=False)
    planner_answer, planner_model_metadata = _run_file_content_planner(
        request,
        planner_payload,
        leader_plan,
    )
    planner_result = _try_parse_json_object(planner_answer)
    planner_action = str(planner_result.get("action") or "").strip().lower()
    if planner_action == "clarify":
        question = str(planner_result.get("question") or "").strip()
        if not question:
            raise HTTPException(status_code=502, detail="文件内容编排智能体要求澄清，但没有返回问题。")
        clarification_metadata = {
            **metadata,
            **planner_model_metadata,
            "targetAgent": "file_content_planner_agent",
            "executedAgent": "file_content_planner_agent",
            "intent": "file_source_clarification",
            "answerType": "text",
            "fileContentPlannerAction": "clarify",
        }
        clarification_metadata.pop("embeddedImageBytes", None)
        return _decorate_output_response(RagQueryResponse(
            strategy="file_content_planner_agent",
            answer=question,
            answerType="text",
            documents=[],
            trace=[
                RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
                RagTraceResponse(stage="agent_answer", detail={
                    "agentName": "file_content_planner_agent",
                    "action": "clarify",
                    **planner_model_metadata,
                }),
            ],
            metadata=clarification_metadata,
        ))
    if planner_action != "export":
        raise HTTPException(status_code=502, detail="文件内容编排智能体返回了无效 action。")
    export_content = str(planner_result.get("content") or "").strip()
    export_title = str(planner_result.get("title") or "").strip()
    if not export_content or not export_title:
        raise HTTPException(status_code=502, detail="文件内容编排智能体没有返回完整的标题和内容。")
    metadata.update({
        **planner_model_metadata,
        "sourceTitle": export_title,
        "promptAgent": "file_content_planner_agent",
        "fileContentPlannerAction": "export",
        "sourceMessageOrigin": request_metadata.get("sourceMessageOrigin") or ("selected_message" if source_content else "user_request"),
    })
    selected_source_message_id = planner_result.get("selectedSourceMessageId")
    if selected_source_message_id is not None:
        metadata["selectedSourceMessageId"] = selected_source_message_id
    parsed_input = _try_parse_json_object(export_content)
    export_answer_type = "question_bank" if isinstance(parsed_input.get("questions"), list) else "markdown"
    export_result = export_generated_answer(export_content, export_answer_type, metadata)
    if not export_result.attachments:
        reason = export_result.diagnostics.get("reason") if isinstance(export_result.diagnostics, dict) else ""
        if reason == "tool_disabled":
            disabled_tool = export_result.diagnostics.get("disabledTool") or "generated_export_tools"
            raise HTTPException(status_code=403, detail=f"工具 {_tool_display_name(disabled_tool)} 已在后台关闭，Leader 本次不会调用。")
        if reason == "no_enabled_export_format":
            raise HTTPException(status_code=403, detail="当前没有开启可生成的附件格式，Leader 本次不会调用内容整理工具。")
        raise HTTPException(status_code=400, detail="当前内容无法导出，请提供 Markdown 文本或标准题库 JSON")
    metadata["generatedExports"] = export_result.diagnostics
    metadata.pop("embeddedImageBytes", None)
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
            RagTraceResponse(stage="agent_answer", detail={
                "agentName": "file_content_planner_agent",
                "action": "export",
                "title": export_title,
                **planner_model_metadata,
            }),
            RagTraceResponse(stage="tool_call", detail={"toolName": leader_plan.tool_name, "toolDisplayName": _tool_display_name(leader_plan.tool_name), **export_result.diagnostics}),
        ],
        metadata=metadata,
        attachments=export_result.attachments,
    ))




_TEXT_EXPORT_INSTRUCTION_RE = re.compile(
    r"^\s*(?:请|帮我|麻烦|谢谢)?\s*(?:把|将)?\s*"
    r"(?:以下内容|下面内容|这段文字|这段话|这段文本|以下文字|以下文本|这份|这段内容)?\s*"
    r"(?:转成|转换成|转换为|导出为|保存为|整理成|制作成|做成|生成)\s*"
    r"(?:纯文本|txt|md|markdown|word|docx|ppt|pptx|pdf)\s*(?:文件|文档)?\s*(?:[：:]|\n)?",
    re.IGNORECASE,
)


def _extract_text_content_from_export_request(input_text: str) -> str:
    """Strip the leading export instruction so the remaining text is exported verbatim."""
    text = str(input_text or "")
    stripped = _TEXT_EXPORT_INSTRUCTION_RE.sub("", text, count=1)
    return stripped.strip()


def _text_file_title(content: str) -> str:
    for line in str(content or "").splitlines():
        match = re.match(r"^#\s+(.+)$", line.strip())
        if match:
            return match.group(1).strip()[:60]
    return "文本文件"


def _run_text_to_file_tool(request: RagQueryRequest, leader_plan, direct_tool_test: bool = False) -> RagQueryResponse:
    request_metadata = request.metadata if isinstance(request.metadata, dict) else {}
    tool_name = str(leader_plan.tool_name or "").strip()
    if tool_name not in TEXT_TO_FILE_TOOL_NAMES:
        raise HTTPException(status_code=400, detail="未识别的文本转文件工具。")
    format_from_tool = next(
        (fmt for fmt, name in TEXT_TO_FILE_TOOL_BY_FORMAT.items() if name == tool_name),
        "",
    )
    requested_output_type = _normalize_requested_file_type(
        request_metadata.get("requestedOutputType")
        or _requested_file_type_from_text(request.input)
        or format_from_tool
        or "md"
    )
    if TEXT_TO_FILE_TOOL_BY_FORMAT.get(requested_output_type) != tool_name:
        requested_output_type = format_from_tool or requested_output_type
    tool_label = TEXT_TO_FILE_TOOL_LABELS.get(tool_name, "文本转文件工具")
    metadata = {
        "agentName": tool_name if direct_tool_test else "leader_agent",
        "targetAgent": tool_name,
        "executedAgent": tool_name,
        "intent": leader_plan.intent,
        "needRetrieval": False,
        "retrievalSkipped": True,
        "leaderAction": leader_plan.action,
        "leaderActionLabel": _leader_action_label(leader_plan.action),
        "toolName": tool_name,
        "toolDisplayName": _tool_display_name(tool_name),
        "routeReason": leader_plan.route_reason,
        "strategyLabel": tool_label,
        "executionMode": "direct_tool_test" if direct_tool_test else "leader_call_tool",
        "executionModeLabel": f"管理台直接运行{tool_label}" if direct_tool_test else f"Leader 调用{tool_label}",
        "answerType": "document_export",
        "requestedOutputType": requested_output_type,
        "toolToggles": _tool_toggles_from_request(request),
    }
    metadata.update(_context_metadata_from_request(request))
    source_content = str(request_metadata.get("sourceMessageContent") or "").strip()
    export_content = source_content or _extract_text_content_from_export_request(request.input)
    if not export_content.strip():
        raise HTTPException(status_code=400, detail="未找到可导出的文本内容，请先提供需要导出的文本。")
    metadata["sourceTitle"] = _text_file_title(export_content)
    metadata["sourceMessageOrigin"] = request_metadata.get("sourceMessageOrigin") or (
        "selected_message" if source_content else "user_request"
    )
    export_result = export_text_to_file(export_content, requested_output_type, metadata, tool_name=tool_name)
    if not export_result.attachments:
        diagnostics = export_result.diagnostics if isinstance(export_result.diagnostics, dict) else {}
        reason = str(diagnostics.get("reason") or "").strip()
        if reason == "tool_disabled":
            disabled_tool = str(diagnostics.get("disabledTool") or tool_name)
            raise HTTPException(status_code=403, detail=f"工具 {_tool_display_name(disabled_tool)} 已在后台关闭，Leader 本次不会调用。")
        if reason == "empty_answer":
            raise HTTPException(status_code=400, detail="未找到可导出的文本内容，请先提供需要导出的文本。")
        if reason == "unsupported_format":
            raise HTTPException(status_code=400, detail=f"{tool_label}仅支持对应输出格式。")
        raise HTTPException(status_code=400, detail="当前文本无法导出为所选格式，请检查后台工具开关后重试。")
    metadata["generatedExports"] = export_result.diagnostics
    formats = "、".join(str(item.get("ext") or "").upper() for item in export_result.attachments if item.get("ext"))
    answer = f"已按原文生成文本文件，附件格式：{formats or '文件'}。"
    return _decorate_output_response(RagQueryResponse(
        strategy=tool_name,
        answer=answer,
        answerType="document_export",
        documents=[],
        trace=([] if direct_tool_test else [
            RagTraceResponse(stage="leader_route", detail=_leader_plan_detail(leader_plan)),
        ]) + [
            RagTraceResponse(stage="tool_call", detail={
                "toolName": tool_name,
                "toolDisplayName": _tool_display_name(tool_name),
                **export_result.diagnostics,
            }),
        ],
        metadata=metadata,
        attachments=export_result.attachments,
    ))


def _run_file_content_planner(request: RagQueryRequest, planner_payload: str, leader_plan) -> Tuple[str, Dict[str, Any]]:
    config_agent = (
        "file_content_planner_agent"
        if _is_agent_model_ready(request, "file_content_planner_agent")
        else "leader_agent"
    )
    runtime_config, config_prefix = _require_agent_runtime_config(request, config_agent, leader_plan=leader_plan)
    token = set_active_llm_config(runtime_config)
    try:
        answer = run_specialist_agent("file_content_planner_agent", planner_payload, [], chat_service=None)
    except Exception as exc:
        _raise_agent_execution_error(
            exc,
            "file_content_planner_agent",
            leader_plan=leader_plan,
            runtime_config=runtime_config,
            config_prefix=config_prefix,
        )
    finally:
        reset_active_llm_config(token)
    model_metadata = _agent_model_metadata(runtime_config, config_prefix)
    model_metadata["plannerModelConfigAgent"] = config_agent
    return answer, model_metadata


_STANDALONE_MD_RE = re.compile(r"(?:^|[^a-z0-9])md(?:$|[^a-z0-9])", re.IGNORECASE)


def _requested_file_type_from_text(input_text: str) -> str:
    normalized = normalize_text(input_text)
    if "word" in normalized or "docx" in normalized or "文档版" in normalized:
        return "docx"
    if "excel" in normalized or "xlsx" in normalized or "表格版" in normalized:
        return "xlsx"
    if "markdown" in normalized or "md文件" in normalized or _STANDALONE_MD_RE.search(normalized):
        return "md"
    if "pptx" in normalized or "ppt" in normalized or "幻灯片" in normalized:
        return "pptx"
    if "pdf" in normalized:
        return "pdf"
    if "txt" in normalized or "纯文本" in normalized:
        return "txt"
    return ""


def _normalize_requested_file_type(value: Any) -> str:
    normalized = str(value or "").strip().lower()
    aliases = {
        "word": "docx",
        "excel": "xlsx",
        "markdown": "md",
        "ppt": "pptx",
        "纯文本": "txt",
        "document": "",
        "file": "",
    }
    return aliases.get(normalized, normalized)


def _run_disabled_tool_response(request: RagQueryRequest, tool_name: str, leader_plan) -> RagQueryResponse:
    normalized = str(tool_name or "").strip()
    display_name = _tool_display_name(normalized) or "目标工具"
    answer = f"{display_name}当前已在后台关闭，本次未执行。如需使用该能力，请先在管理后台开启对应工具。"
    disabled_plan = LeaderPlan(
        intent=str(getattr(leader_plan, "intent", "") or "tool_disabled"),
        target_agent="leader_agent",
        need_retrieval=False,
        rag_strategy="",
        action="direct_answer",
        route_reason=(
            f"Leader 计划调用 {display_name}（{normalized}），但该工具已在后台关闭，未执行工具调用。"
        ),
        answer=answer,
        route_mode="tool_disabled",
    )
    return _run_leader_direct_answer(disabled_plan)


def _run_service_tool(request: RagQueryRequest, authorization: str, leader_plan) -> RagQueryResponse:
    tool_name = leader_plan.tool_name
    tool_display_name = _tool_display_name(tool_name)
    planning_answer = str(getattr(leader_plan, "answer", "") or "").strip()
    answer_type = "service_tool_result"
    tool_started_at = time.perf_counter()
    results, cache_meta = data_store.search_service_tool_with_meta(authorization, tool_name, request.input)
    results = results or []
    tool_ms = _elapsed_ms(tool_started_at)
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
    backend_failure = _service_tool_backend_failure(cache_meta) if not results else {}
    if backend_failure:
        tool_results = [{
            "type": "tool_execution_error",
            "status": backend_failure.get("status"),
            "reason": backend_failure.get("reason"),
            "statusCode": backend_failure.get("statusCode"),
            "message": "工具调用失败，当前结果不能用于判断是否存在业务数据。",
        }]
        result_status = "error"
    elif not results:
        tool_results = [{
            "type": "tool_empty_result",
            "status": "empty",
            "reason": "no_data",
            "message": "工具调用成功，但没有查询到匹配数据。",
        }]
        result_status = "empty"
    else:
        tool_results = results
        result_status = "ok"
    # 空结果和错误也作为工具结果返回，避免前端或 Langfuse 看到空数组而无法判断发生了什么。
    documents = [_tool_result_to_document(item, index) for index, item in enumerate(tool_results, start=1)]
    summary_results = tool_results
    summary_started_at = time.perf_counter()
    try:
        answer = leader_agent.summarize_tool_result(
            input_text=request.input,
            plan=leader_plan,
            tool_display_name=tool_display_name,
            tool_results=summary_results,
        )
    except Exception as exc:
        logger.warning("leader tool result summarization failed tool=%s error=%s", tool_name, exc)
        raise HTTPException(status_code=502, detail="模型未能生成工具结果回复，已禁止系统兜底回复。") from exc
    summary_ms = _elapsed_ms(summary_started_at)
    if not str(answer or "").strip():
        raise HTTPException(status_code=502, detail="模型返回空的工具结果回复，已禁止系统兜底回复。")
    summarized_by_model = True
    summary_mode = "model"
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
        "toolResultSummaryMode": summary_mode,
        "serviceToolBackendStatus": str(backend_failure.get("status") or "ok"),
        "serviceToolBackendFailure": bool(backend_failure),
        "toolResultStatus": result_status,
        "toolResultCount": len(tool_results),
        "toolResultEmpty": result_status == "empty",
        "toolToggles": _tool_toggles_from_request(request),
        **retrieval_meta,
    }
    metadata.update(_context_metadata_from_request(request))
    return _merge_response_performance(_decorate_output_response(RagQueryResponse(
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
                "toolMs": tool_ms,
                **retrieval_meta,
                "resultStatus": result_status,
                "resultCount": len(tool_results),
            }),
            RagTraceResponse(stage="tool_result_summary", detail={
                "agentName": "leader_agent",
                "toolName": tool_name,
                "toolDisplayName": tool_display_name,
                "summarizedByModel": summarized_by_model,
                "summaryMode": summary_mode,
                "summaryMs": summary_ms,
                "resultCount": len(results),
                "serviceToolBackendStatus": str(backend_failure.get("status") or "ok"),
                **({"backendFailure": backend_failure} if backend_failure else {}),
            }),
        ],
        metadata=metadata,
    )), toolMs=tool_ms, summaryMs=summary_ms)


def _decorate_output_response(response: RagQueryResponse) -> RagQueryResponse:
    if response.metadata is None:
        response.metadata = {}
    existing_attachments = response.attachments if isinstance(response.attachments, list) else []
    extracted_attachments = _extract_response_attachments(response.answer)
    requested_output_type = str(response.metadata.get("requestedOutputType") or "").strip().lower()
    concrete_export_types = {"docx", "word", "xlsx", "excel", "md", "markdown", "ppt", "pptx", "mmd", "zip"}
    planner_is_clarifying = (
        str(response.metadata.get("fileContentPlannerAction") or "").strip().lower() == "clarify"
        or str(response.metadata.get("intent") or "").strip().lower() == "file_source_clarification"
    )
    if requested_output_type in concrete_export_types and not planner_is_clarifying:
        export_result = export_generated_answer(response.answer, response.answerType, response.metadata)
        generated_attachments = export_result.attachments
        export_diagnostics = export_result.diagnostics
    else:
        generated_attachments = []
        export_diagnostics = {
            "skipped": True,
            "reason": "file_source_clarification" if planner_is_clarifying else "output_format_not_selected",
        }
    attachments = _merge_attachments(existing_attachments, extracted_attachments, generated_attachments)
    if not generated_attachments and isinstance(response.metadata.get("generatedExports"), dict):
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
            storage_key = str(item.get("storageKey") or "").strip()
            url = str(item.get("url") or "").strip()
            identity = ("storageKey", storage_key) if storage_key else (("url", url) if url else None)
            if identity is None or identity in seen:
                continue
            seen.add(identity)
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
    if normalized_answer_type == "image_generation" or agent == "image_agent":
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
    intent = str(metadata.get("intent") or "").strip().lower()
    if intent in {
        "smalltalk", "greeting", "leader_callable_catalog", "capability_inquiry",
        "file_source_clarification", "knowledge_source_clarification",
    }:
        return []
    hints = metadata.get("outputPreferenceHints") if isinstance(metadata.get("outputPreferenceHints"), dict) else {}
    preferred_format = str(hints.get("preferredFormat") or "").strip()
    confidence_level = str(hints.get("confidenceLevel") or "").strip()
    convertible_agents = {
        "leader_agent",
        "textbook_knowledge_agent",
        "meeting_summary_agent",
        "meeting_resource_recommendation_agent",
        "ppt_outline_agent",
        "diagram_mind_map_agent",
        "diagram_flowchart_agent",
        "diagram_activity_agent",
        "diagram_architecture_agent",
    }

    actions: List[Dict[str, Any]] = []
    file_actions = _file_format_follow_up_actions(answer_type, metadata, agent)
    if "document" in output_types:
        actions.append(_follow_up_action("再来图片版", "请在当前内容基础上，再生成图片形式或图解版。", "image", "secondary"))
    elif "image" in output_types:
        actions.extend(file_actions)
    elif agent in convertible_agents or str(answer_type or "").startswith("mermaid"):
        if preferred_format == "document" and confidence_level in {"high", "medium"}:
            actions.extend(file_actions)
        elif preferred_format == "image" and confidence_level in {"high", "medium"}:
            actions.append(_follow_up_action("生成图片版", "请把刚才的内容生成图片形式或图解版。", "image", "primary"))
            actions.extend(file_actions)
        else:
            actions.extend(file_actions)

    return actions[:4]


def _file_format_follow_up_actions(answer_type: str, metadata: Dict[str, Any], agent: str) -> List[Dict[str, Any]]:
    is_diagram = str(answer_type or "").startswith("mermaid") or agent.startswith("diagram_")
    is_question_bank = str(answer_type or "") == "question_bank" or agent.startswith("textbook_question_")
    if is_diagram:
        if not _metadata_tool_enabled(metadata, "generated_export_tools"):
            return []
        candidates = (
            ("Mermaid 源文件", "请把当前消息原内容生成 Mermaid 源文件。", "mmd", "diagram_source_export_tool"),
            ("Markdown 文件", "请把当前消息原内容生成 Markdown 文件。", "md", "markdown_export_tool"),
        )
    elif is_question_bank:
        if not _metadata_tool_enabled(metadata, "generated_export_tools"):
            return []
        candidates = (
            ("Excel 题库", "请把当前消息原内容生成 Excel 题库文件。", "xlsx", "excel_export_tool"),
            ("Word 题库", "请把当前消息原内容生成 Word 题库文件。", "docx", "docx_export_tool"),
            ("Markdown 题库", "请把当前消息原内容生成 Markdown 题库文件。", "md", "markdown_export_tool"),
        )
    else:
        # 文本转文件工具：按格式分别提供 Markdown / 纯文本 / Word。
        candidates = (
            ("Markdown 文件", "请把当前消息原内容生成 Markdown 文件。", "md", TEXT_TO_MARKDOWN_TOOL_NAME),
            ("纯文本文件", "请把当前消息原内容生成纯文本文件。", "txt", TEXT_TO_TXT_TOOL_NAME),
            ("Word 文件", "请把当前消息原内容生成 Word 文件。", "docx", TEXT_TO_DOCX_TOOL_NAME),
        )
    return [
        _follow_up_action(label, prompt, output_type, "primary")
        for label, prompt, output_type, tool_name in candidates
        if _metadata_tool_enabled(metadata, tool_name)
    ]


def _metadata_tool_enabled(metadata: Dict[str, Any], tool_name: str) -> bool:
    toggles = metadata.get("toolToggles") if isinstance(metadata, dict) else None
    if not isinstance(toggles, dict) or tool_name not in toggles:
        enabled = True
    else:
        enabled = _parse_agent_enabled_value(toggles.get(tool_name))
    if not enabled:
        return False
    return True


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
    file_actions = [
        str(item.get("label") or "").strip()
        for item in follow_up_actions
        if str(item.get("outputType") or "").strip().lower() in {"docx", "xlsx", "md", "pptx", "mmd", "zip"}
    ]
    if file_actions:
        return "请选择需要生成的文件格式：" + "、".join(file_actions) + "。"
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

    extensions = "|".join(re.escape(value) for value in get_detectable_extensions())
    markdown_pattern = re.compile(
        rf"!?\[([^\]]+)\]\(((?:https?://|/uploads/)[^\s\"'<>，。！？；、)]+?\.(?:{extensions})(?:\?[^\s\"'<>，。！？；、)]*)?)\)",
        re.IGNORECASE,
    )
    for match in markdown_pattern.finditer(content):
        attachments.append(_build_attachment(match.group(2), match.group(1), ""))

    plain_text = markdown_pattern.sub("", content)
    url_pattern = re.compile(
        rf"(?:https?://|/uploads/)[^\s\"'<>，。！？；、]+?\.(?:{extensions})(?:\?[^\s\"'<>，。！？；、]*)?",
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
    registered = resolve_file_format(ext, hinted)
    if registered:
        attachment_type = str(registered.get("type") or attachment_type)
    elif "image" in hinted:
        attachment_type = "image"
    elif "video" in hinted:
        attachment_type = "video"
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
        str(item.get("message") or "").strip(),
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
        "call_tool": "调用接口/工具",
    }
    return labels.get(action or "", action or "未知动作")


def _strategy_label(strategy_name: str) -> str:
    custom_labels = {
        "leader_direct_answer": "Leader 直接回答",
        TOOL_CAPABILITY_QUERY_NAME: "工具能力查询",
        "direct_agent": "直接处理",
        "java_schedule_api": "课表查询工具",
        "java_activity_api": "活动查询工具",
        "java_meeting_api": "会议查询工具",
        "java_canteen_api": "食堂餐饮查询工具",
        "java_facility_api": "设施位置查询工具",
        "java_secondhand_api": "旧物查询工具",
        "generated_export_tools": "内容导出工具",
        **TEXT_TO_FILE_TOOL_LABELS,
        "text_to_sql": "Text-to-SQL",
        IMAGE_RECOGNITION_TOOL_NAME: "图片识别工具",
        IMAGE_STITCHING_TOOL["name"]: "图片拼接工具",
        **{
            tool_name: config["zhName"]
            for tool_name, config in VISUAL_GENERATION_TOOL_CONFIG.items()
        },
    }
    if strategy_name in custom_labels:
        return custom_labels[strategy_name]
    return strategy_name or "直接处理"


def _tool_zh_name(tool_name: str) -> str:
    labels = {
        TOOL_CAPABILITY_QUERY_NAME: "工具能力查询",
        IMAGE_RECOGNITION_TOOL_NAME: "图片识别工具",
        IMAGE_STITCHING_TOOL["name"]: "图片拼接工具",
        "text_to_sql": "结构化查询工具",
        "java_schedule_api": "课表查询工具",
        "java_activity_api": "活动查询工具",
        "java_meeting_api": "会议查询工具",
        "java_canteen_api": "食堂餐饮查询工具",
        "java_facility_api": "设施位置查询工具",
        "java_secondhand_api": "旧物查询工具",
        "generated_export_tools": "内容整理工具",
        **TEXT_TO_FILE_TOOL_LABELS,
        "markdown_export_tool": "Markdown 导出工具",
        "docx_export_tool": "Word 导出工具",
        "excel_export_tool": "Excel 导出工具",
        "pptx_export_tool": "PPT 导出工具",
        "content_archive_tool": "附件打包工具",
        "diagram_source_export_tool": "图表源码导出工具",
        **{
            tool_name: config["zhName"]
            for tool_name, config in VISUAL_GENERATION_TOOL_CONFIG.items()
        },
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
        IMAGE_RECOGNITION_AGENT_NAME: "image_analysis",
        "mind_map_agent": "image_prompt",
        "diagram_mind_map_agent": "mermaid_mindmap",
        "diagram_flowchart_agent": "mermaid_flowchart",
        "diagram_activity_agent": "mermaid_activity_flowchart",
        "diagram_architecture_agent": "mermaid_architecture",
        "textbook_knowledge_agent": "markdown",
        "ppt_outline_agent": "ppt_outline",
        "ppt_structure_agent": "ppt_structure",
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


@router.post("/tools/file-content/test")
def test_file_content_tool(
    request: FileContentToolTestRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    try:
        content = base64.b64decode(request.contentBase64, validate=True)
    except (ValueError, base64.binascii.Error) as exc:
        raise HTTPException(status_code=400, detail="文件内容不是合法 Base64") from exc
    try:
        return extract_file_content(request.toolName, request.fileName, content)
    except FileContentExtractionError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


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
        result = convert_pdf(pdf_bytes, filename, request.targetFormat, request.convertMode)
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
        result = convert_ppt_to_docx(ppt_bytes, filename, request.convertMode)
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


@router.post("/ppt/to-pdf")
def convert_ppt_to_pdf_document(
    request: PptConvertRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    filename = request.fileName or "presentation.pptx"
    lower_name = filename.lower()
    if not lower_name.endswith(".ppt") and not lower_name.endswith(".pptx"):
        raise HTTPException(status_code=400, detail="仅支持上传 PPT/PPTX 文件")
    try:
        import base64
        ppt_bytes = base64.b64decode(request.contentBase64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="PPT Base64 内容无效") from exc
    logger.info("ppt to pdf request filename=%s size=%s", filename, len(ppt_bytes))
    try:
        result = convert_ppt_to_pdf(ppt_bytes, filename)
        logger.info(
            "ppt to pdf success filename=%s output=%s content_length=%s pages=%s",
            filename,
            result.get("fileName"),
            result.get("contentLength"),
            result.get("pageCount"),
        )
        return result
    except PptConversionError as exc:
        logger.warning("ppt to pdf failed filename=%s reason=%s", filename, exc)
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc


@router.post("/docx/to-pdf")
def convert_docx_to_pdf_document(
    request: DocxConvertRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    filename = request.fileName or "document.docx"
    if not filename.lower().endswith(".docx"):
        raise HTTPException(status_code=400, detail="仅支持上传 DOCX 文件")
    try:
        import base64
        docx_bytes = base64.b64decode(request.contentBase64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="DOCX Base64 内容无效") from exc
    logger.info("docx to pdf request filename=%s size=%s", filename, len(docx_bytes))
    try:
        result = convert_docx_to_pdf(docx_bytes, filename)
        logger.info(
            "docx to pdf success filename=%s output=%s content_length=%s pages=%s",
            filename,
            result.get("fileName"),
            result.get("contentLength"),
            result.get("pageCount"),
        )
        return result
    except DocxConversionError as exc:
        logger.warning("docx to pdf failed filename=%s reason=%s", filename, exc)
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc


@router.post("/docx/to-ppt")
def convert_docx_to_ppt_document(
    request: DocxConvertRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    _require_authorization(authorization)
    filename = request.fileName or "document.docx"
    if not filename.lower().endswith(".docx"):
        raise HTTPException(status_code=400, detail="仅支持上传 DOCX 文件")
    try:
        import base64
        docx_bytes = base64.b64decode(request.contentBase64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="DOCX Base64 内容无效") from exc
    logger.info("docx to ppt request filename=%s size=%s", filename, len(docx_bytes))
    try:
        result = convert_docx_to_ppt(docx_bytes, filename, request.convertMode)
        logger.info(
            "docx to ppt success filename=%s output=%s content_length=%s pages=%s",
            filename,
            result.get("fileName"),
            result.get("contentLength"),
            result.get("pageCount"),
        )
        return result
    except (DocxConversionError, PdfConversionError) as exc:
        logger.warning("docx to ppt failed filename=%s reason=%s", filename, exc)
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
    _validate_rag_input_length(request)
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


def _validate_rag_input_length(request: RagQueryRequest) -> None:
    input_length = len(request.input or "")
    purpose = str((request.metadata or {}).get("requestPurpose") or "").strip()
    maximum = (
        QUESTION_GENERATION_INPUT_MAX_LENGTH
        if purpose == "question_generation"
        else DEFAULT_RAG_INPUT_MAX_LENGTH
    )
    if input_length > maximum:
        raise HTTPException(
            status_code=422,
            detail=f"input exceeds the allowed {maximum} characters for this request",
        )


def _require_authorization(authorization: Optional[str]) -> None:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或Token无效")
