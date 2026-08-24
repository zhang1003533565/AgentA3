import base64
import copy
import json
import logging
import os
import re
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Callable, Dict, List, Mapping, Optional

from fastapi import HTTPException

from app.model_providers.factory import get_chat_model_provider
from app.model_providers.runtime_config import (
    reset_active_llm_config,
    reset_active_max_output_tokens,
    reset_active_llm_timeout,
    reset_active_reasoning_effort,
    set_active_llm_config,
    set_active_max_output_tokens,
    set_active_llm_timeout,
    set_active_reasoning_effort,
)
from app.multi_agents.image_agent.agent import image_agent
from app.multi_agents.ppt_structure_agent.agent import normalize_structure_answer
from app.multi_agents.runner import run_specialist_agent
from app.ppt_generation.consistency_validator import (
    build_qa_report,
    validate_presentation,
    write_qa_report,
)
from app.ppt_generation.content_quality_validator import (
    assess_content_quality,
    assess_outline_quality,
    build_source_trace,
    quality_warning_messages,
)
from app.ppt_generation.content_fitter import REWRITE_SYSTEM_PROMPT, build_rewrite_user_prompt
from app.ppt_generation.embedded_config import EmbeddedPptConfig
from app.ppt_generation.layout_validator import validate_slide
from app.ppt_generation.presenton_html_renderer import render_presenton_html
from app.ppt_generation.presenton_generation_prompts import (
    PRESENTON_CONTENT_RULES,
    PRESENTON_STRUCTURE_RULES,
)
from app.ppt_generation.pptx_export_qa import validate_exported_pptx
from app.ppt_generation.ppt_mapper import (
    template_for_settings,
)
from app.ppt_generation.repair_engine import RepairEngine
from app.ppt_generation.task_store import PptTaskStore
from app.ppt_generation.source_file_store import PptSourceFileStore
from app.ppt_generation.source_parser import PptSourceParseError, extract_source_text
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import (
    SlideLayoutModel,
    measure_text,
    parse_slide_layout,
    semantic_content_contract,
)
from app.ppt_generation.template_preview import (
    get_template_layout_preview,
    warm_up_previews,
)
from app.rag.document_conversion import generated_exporter


_PAGE_HEADING = re.compile(r"###\s*第\s*(\d+)\s*页", re.IGNORECASE)
_FIELD = re.compile(r"^-\s*([^：:]+)[：:]\s*(.*)$")
logger = logging.getLogger(__name__)
# 内容批次单次调用的超时上限：完整槽位输出（componentContent+speakerNote）
# 输出已设置硬 token 上限；默认 150s 覆盖正常长输出，同时避免异常代理无限等待。
PPT_PAGE_LLM_TIMEOUT_SECONDS = max(30, min(180, int(os.getenv("PPT_PAGE_LLM_TIMEOUT_SECONDS") or 150)))
PPT_OUTLINE_LLM_TIMEOUT_SECONDS = max(30, min(180, int(os.getenv("PPT_OUTLINE_LLM_TIMEOUT_SECONDS") or 180)))
PPT_TASK_TIMEOUT_SECONDS = max(180, min(30 * 60, int(os.getenv("PPT_TASK_TIMEOUT_SECONDS") or 15 * 60)))
PPT_OUTLINE_TASK_TIMEOUT_SECONDS = max(120, min(15 * 60, int(os.getenv("PPT_OUTLINE_TASK_TIMEOUT_SECONDS") or 8 * 60)))
# Qwen3.7 medium thinking uses an 8192-token budget; Bailian requires the
# completion cap to be greater than that budget. Keep enough room for the
# visible outline instead of silently forcing the provider to shrink reasoning.
PPT_OUTLINE_MAX_OUTPUT_TOKENS = max(1200, min(12000, int(os.getenv("PPT_OUTLINE_MAX_OUTPUT_TOKENS") or 9000)))
PPT_STRUCTURE_MAX_OUTPUT_TOKENS = max(500, min(4000, int(os.getenv("PPT_STRUCTURE_MAX_OUTPUT_TOKENS") or 1600)))
PPT_CONTENT_MAX_OUTPUT_TOKENS = max(1200, min(12000, int(os.getenv("PPT_CONTENT_MAX_OUTPUT_TOKENS") or 4200)))
PPT_REPAIR_MAX_OUTPUT_TOKENS = max(200, min(2000, int(os.getenv("PPT_REPAIR_MAX_OUTPUT_TOKENS") or 700)))
PPT_CONTENT_BATCH_SIZE = max(1, min(5, int(os.getenv("PPT_CONTENT_BATCH_SIZE") or 4)))
# 默认允许稳定供应商使用 2 个内容批次并行；曾验证会断连的
# opencode/deepseek 仍在 _content_worker_count 中强制串行。
PPT_CONTENT_MAX_WORKERS = max(1, min(4, int(os.getenv("PPT_CONTENT_MAX_WORKERS") or 2)))
# 大纲单次调用可携带的资料字符上限（资料通过 user_input 传递，绕过 evidence 的 1200 字符截断）。
# 长资料会先做保结构压缩，默认不再把 24k 原文整段塞给模型。
PPT_OUTLINE_SOURCE_MAX_CHARS = max(2_000, int(os.getenv("PPT_OUTLINE_SOURCE_MAX_CHARS") or 16_000))
# 内容批次资料切片字符上限：页面正文需要从原始资料展开，不能只靠大纲要点。
# 默认 24k，避免长资料的定义/公式/例证被位置切片直接丢掉；仍可由环境变量收紧。
PPT_BATCH_SOURCE_MAX_CHARS = max(2_000, int(os.getenv("PPT_BATCH_SOURCE_MAX_CHARS") or 24_000))
# 单次调用失败后的重试次数（不含首次）：默认不重试，失败交给任务级重试入口，
# 避免单批模型超时后再次占用唯一任务线程。
PPT_LLM_MAX_RETRIES = max(0, min(3, int(os.getenv("PPT_LLM_MAX_RETRIES") or 0)))
PPT_LLM_RETRY_BASE_DELAY = max(0.5, float(os.getenv("PPT_LLM_RETRY_BASE_DELAY") or 1.0))
# 大纲是首个用户可见 AI 结果：默认使用低强度推理，质量由结构校验和局部修复兜底；
# medium 只应由部署配置显式开启，避免正常请求把时间耗在长思考上。
PPT_OUTLINE_REASONING_EFFORT = os.getenv("PPT_OUTLINE_REASONING_EFFORT", "low").strip().lower()
if PPT_OUTLINE_REASONING_EFFORT not in {"none", "low", "medium", "high"}:
    PPT_OUTLINE_REASONING_EFFORT = "low"
# Runtime fallback already tries the configured model chain. Re-running that
# entire chain after it is exhausted multiplies latency without adding a new
# recovery path; callers may opt in explicitly for unusually unstable networks.
PPT_OUTLINE_LLM_MAX_RETRIES = max(0, min(2, int(os.getenv("PPT_OUTLINE_LLM_MAX_RETRIES") or 0)))
# 内容批次保留 low 推理：reasoning_effort=none 时模型对长输出任务返回空内容
# （实测），而默认推理每批 100s+；low 实测 ~75s 且输出完整
PPT_CONTENT_REASONING_EFFORT = os.getenv("PPT_CONTENT_REASONING_EFFORT", "low").strip().lower()
PPT_CONTENT_QUALITY_REPAIR = str(os.getenv("PPT_CONTENT_QUALITY_REPAIR") or "true").strip().lower() in {"1", "true", "yes", "on"}
# 大纲页数区间保护：AI 根据内容自主决定页数，系统只保下限防"只有一页"、
# 尊重用户档位作上限；重试后仍不足则把单页要点拆成逐页（数据驱动，不虚构）
PPT_OUTLINE_MAX_RETRIES = max(0, min(2, int(os.getenv("PPT_OUTLINE_MAX_RETRIES") or 1)))
PPT_OUTLINE_MIN_PAGES = max(3, int(os.getenv("PPT_OUTLINE_MIN_PAGES") or 5))
# 默认允许 AI 在更完整的主题拆解中规划到 30 页；请求显式传入的 pageCount
# 仍由接口层的 50 页绝对上限保护，避免把一次任务放大到不可控规模。
PPT_OUTLINE_MAX_PAGES = max(PPT_OUTLINE_MIN_PAGES, int(os.getenv("PPT_OUTLINE_MAX_PAGES") or 30))
# 没有上传资料、只有一句主题时，模型需要按主题策划内容，不能继续把主题本身当成完整资料。
PPT_OUTLINE_TOPIC_ONLY_MAX_CHARS = max(24, int(os.getenv("PPT_OUTLINE_TOPIC_ONLY_MAX_CHARS") or 96))
PPT_OUTLINE_GENERIC_TOPICS = frozenset({
    "PPT生成", "AIPPT", "PPT大纲", "演示文稿", "复习资料", "手动输入资料",
})
PPT_ENABLE_CONTENT_REPAIR_LLM = str(os.getenv("PPT_ENABLE_CONTENT_REPAIR_LLM") or "false").strip().lower() in {"1", "true", "yes", "on"}

_PARALLEL_CONTENT_PROVIDER_ALIASES = {
    "qwen", "dashscope", "aliyun", "aliyun_qwen", "aliyun-qwen",
    "alibaba_qwen", "alibaba-qwen", "qwen_openai", "qwen-openai",
}
_SERIAL_CONTENT_PROVIDER_ALIASES = {
    "deepseek", "openai_compatible", "openai-compatible", "opencode",
}


def _content_worker_count(llm_config: Any, batch_count: int) -> int:
    """Choose bounded concurrency without reintroducing known provider disconnects."""
    provider = str(getattr(llm_config, "provider", "") or "").strip().lower()
    if provider in _SERIAL_CONTENT_PROVIDER_ALIASES:
        return 1
    if provider not in _PARALLEL_CONTENT_PROVIDER_ALIASES:
        return 1
    return min(PPT_CONTENT_MAX_WORKERS, max(1, batch_count))

_TERMINAL_TASK_STATUSES = {"completed", "failed", "cancelled", "timed_out"}


class PptTaskStopped(RuntimeError):
    """内部异常：任务已取消或超过截止时间，停止后续批次。"""


class PptGenerationService:
    def __init__(self) -> None:
        self._tasks: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.RLock()
        self._task_store = PptTaskStore()
        self._deadline_timers: Dict[str, threading.Timer] = {}
        self._source_files = PptSourceFileStore()
        # 任务级也串行：opencode 对同一 API key 的并发请求会断开连接（实测），
        # 多任务并行时各自的内容批次互相挤占，全部卡死；串行执行任务会让
        # 后续任务排队等待（状态可见），而不是并发失败。
        self._executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="ai-ppt")
        self._embedded_config = EmbeddedPptConfig.from_env()
        self._template_catalog = EmbeddedTemplateCatalog()
        self._embedded_config.source_root.mkdir(parents=True, exist_ok=True)
        self._repair_engine = RepairEngine()
        self._layout_models: Dict[str, SlideLayoutModel] = {}
        self._recover_interrupted_tasks()
        self._start_template_preview_warmup()

    def _recover_interrupted_tasks(self) -> None:
        """服务重启后，不能让持久化的排队任务伪装成仍在运行。"""
        try:
            tasks = self._task_store.list_tasks()
        except Exception:
            logger.exception("failed to inspect persisted PPT tasks during startup")
            return
        for task in tasks:
            if str(task.get("status") or "") not in {"queued", "running"}:
                continue
            task_id = str(task.get("taskId") or "")
            if not task_id:
                continue
            task.update({
                "status": "timed_out",
                "stage": "recovered",
                "progress": min(99, int(task.get("progress") or 0)),
                "message": "PPT 服务重启，原生成任务已结束，请重新提交",
                "error": {
                    "type": "PptServiceRestarted",
                    "message": "生成服务在任务执行期间重启",
                },
                "updatedAt": int(time.time() * 1000),
            })
            self._tasks[task_id] = task
            self._task_store.put(task)

    def _register_task(self, task: Dict[str, Any]) -> None:
        task_id = str(task.get("taskId") or "")
        with self._lock:
            self._tasks[task_id] = task
            self._task_store.put(task)
        deadline_at = int(task.get("deadlineAt") or 0)
        if deadline_at > 0:
            delay = max(0.1, (deadline_at - int(time.time() * 1000)) / 1000.0)
            timer = threading.Timer(delay, self._expire_task, args=(task_id,))
            timer.daemon = True
            with self._lock:
                old_timer = self._deadline_timers.get(task_id)
                if old_timer is not None:
                    old_timer.cancel()
                self._deadline_timers[task_id] = timer
            timer.start()

    def _expire_task(self, task_id: str) -> None:
        with self._lock:
            task = self._task_store.get(task_id) or self._tasks.get(task_id)
            if task is None or str(task.get("status") or "") in _TERMINAL_TASK_STATUSES:
                return
        self._update(
            task_id,
            status="timed_out",
            stage="timeout",
            progress=min(99, int(task.get("progress") or 0)),
            message="PPT 生成超过最大等待时间，已停止等待，请重试",
            error={
                "type": "PptTaskTimeout",
                "message": "任务超过系统允许的最大执行时间",
            },
        )

    def _task_is_stopped(self, task_id: Optional[str]) -> bool:
        if not task_id:
            return False
        with self._lock:
            task = self._task_store.get(task_id) or self._tasks.get(task_id)
            if task is None:
                return True
            if str(task.get("status") or "") in {"cancelled", "timed_out"}:
                return True
            deadline_at = int(task.get("deadlineAt") or 0)
        if deadline_at and int(time.time() * 1000) >= deadline_at:
            self._expire_task(task_id)
            return True
        return False

    @staticmethod
    def _deadline_ms(seconds: int) -> int:
        return int((time.time() + max(1, seconds)) * 1000)

    def _layout_model(self, template_id: str, layout_id: str, layout_json: Mapping[str, Any]) -> Optional[SlideLayoutModel]:
        """按模板+版式缓存解析后的元素模型（只解析一次，校验/修复/QA 共用）。"""
        if not layout_json:
            return None
        cache_key = f"{template_id}:{layout_id}"
        cached = self._layout_models.get(cache_key)
        if cached is not None:
            return cached
        try:
            model = parse_slide_layout(layout_json)
        except Exception as exc:  # 模板解析失败不阻断生成，仅失去校验能力
            logger.warning("PPT layout model parse failed template=%s layout=%s: %s", template_id, layout_id, exc)
            return None
        self._layout_models[cache_key] = model
        return model

    def _layout_is_generation_safe(
        self, template_id: str, layout_id: str, layout_json: Mapping[str, Any]
    ) -> bool:
        """过滤模板中无法承载标题的异常版式，避免生成阶段硬塞后再失败。"""
        model = self._layout_model(template_id, layout_id, layout_json)
        if model is None:
            return False
        for elements in model.elements.values():
            for element in elements:
                if (
                    element.element_type == "text"
                    and element.mutable_text
                    and element.role in {"title", "subtitle"}
                    and element.font_size > 0
                    and element.height < element.font_size * 0.5
                ):
                    return False
        return True

    def _overflow_fallback_layouts(
        self,
        template_id: str,
        current_layout_id: str,
        layouts_by_id: Mapping[str, Mapping[str, Any]],
    ) -> List[tuple[str, Mapping[str, Any]]]:
        """Return same-template layouts that can safely absorb body copy.

        This is deliberately deterministic and does not invent data.  A
        layout with a larger, real body area is a safer recovery than asking
        the model to rewrite a page repeatedly, and numeric layouts are
        excluded because a fallback must not fabricate a metric value.
        """
        candidates: List[tuple[tuple[int, int, int], str, Mapping[str, Any]]] = []
        for layout_id, layout_json in layouts_by_id.items():
            if str(layout_id) == str(current_layout_id) or not isinstance(layout_json, Mapping):
                continue
            if not self._layout_is_generation_safe(template_id, str(layout_id), layout_json):
                continue
            model = self._layout_model(template_id, str(layout_id), layout_json)
            if model is None:
                continue
            mutable = [
                element
                for elements in model.elements.values()
                for element in elements
                if element.element_type in {"text", "text-list"} and element.mutable_text
            ]
            if not mutable or any(
                element.semantic_role in {"metric_value", "card_value"}
                for element in mutable
            ):
                continue
            body = [
                element for element in mutable
                if element.semantic_role in {"body", "card_body", "bullet_body", "metric_description"}
            ]
            titles = [
                element for element in mutable
                if element.semantic_role in {"page_title", "section_title", "page_subtitle"}
            ]
            if not body or not titles:
                continue
            total_capacity = sum(int(element.constraint.hard_max_chars) for element in body if element.constraint)
            max_capacity = max(
                (int(element.constraint.hard_max_chars) for element in body if element.constraint),
                default=0,
            )
            # Prefer layouts with both more total capacity and a larger
            # individual slot; the final tie-break keeps catalog order stable.
            score = (total_capacity, max_capacity, len(body))
            candidates.append((score, str(layout_id), layout_json))
        candidates.sort(key=lambda item: item[0], reverse=True)
        return [(layout_id, layout_json) for _, layout_id, layout_json in candidates]

    def _try_overflow_layout_fallback(
        self,
        slide: Mapping[str, Any],
        template_id: str,
        current_layout_id: str,
        layouts_by_id: Mapping[str, Mapping[str, Any]],
        llm_config: Any,
        slide_index: int,
    ) -> Optional[Dict[str, Any]]:
        """Try bounded same-template recovery for a geometry overflow page."""
        qa = slide.get("_qa") if isinstance(slide.get("_qa"), Mapping) else {}
        errors = {str(error) for error in (qa.get("validationErrors") or [])}
        if "TEXT_OVERFLOW" not in errors:
            return None
        for candidate_id, candidate_layout in self._overflow_fallback_layouts(
            template_id, current_layout_id, layouts_by_id
        )[:4]:
            candidate = copy.deepcopy(dict(slide))
            candidate["layout"] = candidate_id
            candidate["templateLayoutId"] = candidate_id
            # The editor serializes its current visible text back into
            # ``title``/``content`` before export.  Use that source of truth
            # rather than copying a partially repaired UI tree into a layout
            # with a different slot structure.
            candidate["ui"] = _fill_layout_with_slide_text(
                candidate_layout,
                candidate,
                candidate,
            )
            candidate["_layoutFallbackFrom"] = current_layout_id
            candidate["_layoutFallbackReason"] = "TEXT_OVERFLOW"
            checked = self._enforce_slide_contract(
                candidate,
                template_id,
                candidate_id,
                candidate_layout,
                llm_config,
                slide_index,
            )
            checked_qa = checked.get("_qa") if isinstance(checked.get("_qa"), Mapping) else {}
            if str(checked_qa.get("finalStatus") or "") in {"clean", "repaired"}:
                checked_qa = dict(checked_qa)
                checked_qa["layoutFallback"] = {
                    "from": current_layout_id,
                    "to": candidate_id,
                    "reason": "TEXT_OVERFLOW",
                }
                checked["_qa"] = checked_qa
                logger.warning(
                    "PPT slide recovered with fallback layout index=%s from=%s to=%s",
                    slide_index,
                    current_layout_id,
                    candidate_id,
                )
                return checked
        return None

    def _enforce_slide_contract(
        self,
        item: Dict[str, Any],
        template_id: str,
        layout_id: str,
        layout_json: Mapping[str, Any],
        llm_config: Any,
        slide_index: int,
    ) -> Dict[str, Any]:
        """对合并后的页面执行 校验 → 修复 闭环，并附加 QA 元数据。

        修复只改内容/限幅缩字/还原模板几何，绝不重新布局；修复失败时
        保留已填充页面并在 _qa 中如实记录（不静默丢弃 AI 内容）。
        """
        ui = item.get("ui")
        if not isinstance(ui, Mapping):
            item["_qa"] = {
                "layoutId": layout_id,
                "semanticType": str(item.get("type") or ""),
                "validationErrors": ["MISSING_UI"],
                "repairCount": 0,
                "finalStatus": "unknown",
                "repairHistory": [],
                "densityLevel": "UNKNOWN",
                "fillRatio": 0.0,
                "enforcementError": "页面没有可渲染的 Presenton UI 树",
            }
            return item
        model = self._layout_model(template_id, layout_id, layout_json)
        if model is None:
            item["_qa"] = {
                "layoutId": layout_id,
                "semanticType": str(item.get("type") or ""),
                "validationErrors": ["MISSING_LAYOUT_MODEL"],
                "repairCount": 0,
                "finalStatus": "unknown",
                "repairHistory": [],
                "densityLevel": "UNKNOWN",
                "fillRatio": 0.0,
                "enforcementError": "无法解析页面版式模型",
            }
            return item
        try:
            llm_rewrite = None
            if llm_config is not None:
                def llm_rewrite(text, constraint, mode):
                    timeout_token = set_active_llm_timeout(PPT_PAGE_LLM_TIMEOUT_SECONDS)
                    output_token = set_active_max_output_tokens(PPT_REPAIR_MAX_OUTPUT_TOKENS)
                    config_token = set_active_llm_config(llm_config)
                    try:
                        return get_chat_model_provider().complete(
                            system_prompt=REWRITE_SYSTEM_PROMPT,
                            user_prompt=build_rewrite_user_prompt(text, constraint, mode),
                        )
                    finally:
                        reset_active_llm_config(config_token)
                        reset_active_llm_timeout(timeout_token)
                        reset_active_max_output_tokens(output_token)

            outcome = self._repair_engine.repair(ui, model, llm_rewrite)
            item["ui"] = outcome.ui
            validation_result = outcome.last_result
            validation_errors = list(dict.fromkeys(
                issue.error_type for issue in outcome.final_issues
                if issue.severity == "error"
            ))
            validation_warnings = list(dict.fromkeys(
                issue.error_type for issue in outcome.final_issues
                if issue.severity == "warning"
            ))
            validation_details = [
                {
                    "errorType": issue.error_type,
                    "elementId": issue.element_id,
                    "detail": issue.detail,
                    "severity": issue.severity,
                }
                for issue in outcome.final_issues
            ]
            cardinality_issues = list(item.get("_contentCardinalityIssues") or [])
            if cardinality_issues:
                validation_errors.append("CONTENT_CARDINALITY")
                validation_details.extend(
                    {
                        "errorType": "CONTENT_CARDINALITY",
                        "elementId": issue.get("elementId"),
                        "detail": (
                            f"内容槽位要求 {issue.get('expected')} 项，"
                            + (
                                f"AI 返回 {issue.get('provided')} 项，"
                                if issue.get("provided") is not None
                                else ""
                            )
                            + f"实际只渲染 {issue.get('rendered')} 项"
                        ),
                        "severity": "error",
                    }
                    for issue in cardinality_issues
                )
            final_status = outcome.status
            if cardinality_issues and final_status in {"clean", "repaired"}:
                final_status = "partial"
            item["_qa"] = {
                "layoutId": layout_id,
                "semanticType": str(item.get("type") or ""),
                "contentLength": sum(
                    len(str(point)) for point in (item.get("content") or []) if point
                ),
                "validationErrors": validation_errors,
                "validationWarnings": validation_warnings,
                "validationDetails": validation_details,
                "repairCount": outcome.repair_count,
                "finalStatus": final_status,
                "repairHistory": outcome.history,
                "densityLevel": validation_result.density_level if validation_result else "NORMAL",
                "fillRatio": validation_result.fill_ratio if validation_result else 0.0,
                "missingSlots": list(item.get("_missingSlots") or []),
                "unknownElementIds": list(item.get("_unknownElementIds") or []),
                "contentCardinalityIssues": cardinality_issues,
            }
            logger.info(
                "PPT slide QA index=%s layout=%s status=%s errors=%s repairs=%s density=%s",
                slide_index,
                layout_id,
                outcome.status,
                validation_errors,
                outcome.repair_count,
                item["_qa"]["densityLevel"],
            )
        except Exception as exc:
            logger.exception("PPT slide contract enforcement failed index=%s", slide_index)
            item["_qa"] = {
                "layoutId": layout_id,
                "semanticType": str(item.get("type") or ""),
                "validationErrors": [],
                "repairCount": 0,
                "finalStatus": "unknown",
                "repairHistory": [],
                "densityLevel": "NORMAL",
                "fillRatio": 0.0,
                "enforcementError": _safe_error_message(exc),
            }
        return item

    def render_preview(self, request: Mapping[str, Any], user_id: str = "") -> Dict[str, Any]:
        """Render one edited slide through the same Presenton path as final export.

        Preview requests deliberately skip model rewriting.  The deterministic
        content fitter can still prevent obvious overflow, while the actual
        HTML/CSS/SVG/image renderer remains identical to the export path.
        """
        del user_id
        raw_slide = request.get("slide") if isinstance(request, Mapping) else None
        if not isinstance(raw_slide, Mapping):
            raise HTTPException(status_code=422, detail="预览页面数据无效")
        slide = copy.deepcopy(dict(raw_slide))
        if not isinstance(slide.get("ui"), Mapping):
            raise HTTPException(status_code=422, detail="预览页面缺少可渲染的 UI 数据")

        settings = dict(request.get("settings") or {}) if isinstance(request, Mapping) else {}
        requested_template = request.get("templateId") if isinstance(request, Mapping) else None
        settings["templateId"] = str(requested_template or settings.get("templateId") or "general")
        template_id = template_for_settings(settings, default_template="general")
        template_payload = self._template_catalog.load(template_id)
        layouts_by_id = {
            str(layout.get("id")): layout
            for layout in template_payload.get("layouts") or []
            if isinstance(layout, Mapping) and str(layout.get("id") or "").strip()
        }
        layout_id = str(slide.get("templateLayoutId") or slide.get("layout") or "")
        layout = layouts_by_id.get(layout_id) or {}
        slide = self._enforce_slide_contract(
            slide,
            template_id,
            layout_id,
            layout,
            None,
            int(slide.get("index") or 1),
        )

        try:
            _, _, previews, _ = render_presenton_html(
                [slide],
                str(request.get("title") or "演示文稿"),
                {"templateId": template_id, "previewOnly": True},
            )
            if not previews:
                raise HTTPException(status_code=502, detail="PPT 预览渲染未返回图片")
            preview = previews[0]
            exported = generated_exporter.open_generated_export(
                str(preview.get("storageKey") or ""),
                str(preview.get("internalCapability") or ""),
            )
            try:
                image_bytes = exported.stream.read()
                mime_type = exported.mime_type or "image/png"
            finally:
                exported.stream.close()
            return {
                "imageBase64": base64.b64encode(image_bytes).decode("ascii"),
                "mimeType": mime_type,
                "slideIndex": int(slide.get("index") or 1),
                "qa": copy.deepcopy(slide.get("_qa") or {}),
            }
        except HTTPException:
            raise
        except Exception as exc:
            logger.exception("failed to render edited PPT preview")
            raise HTTPException(
                status_code=502,
                detail=f"PPT 编辑预览生成失败：{_safe_error_message(exc)}",
            ) from exc
        finally:
            # Preview images are returned inline and must not accumulate in the
            # protected export store after every keystroke.
            try:
                if "preview" in locals():
                    generated_exporter._delete_export_pair(
                        generated_exporter._current_export_root(),
                        str(preview.get("storageKey") or ""),
                    )
            except Exception:
                logger.debug("failed to remove temporary PPT preview", exc_info=True)

    def _prepare_final_slides(
        self,
        slides: List[Dict[str, Any]],
        template_id: str,
        llm_config: Any,
        task_id: str,
    ) -> tuple[List[Dict[str, Any]], Dict[str, Any], str, List[Dict[str, Any]]]:
        """对用户编辑后的页面再执行一次校验、修复和质量门禁。"""
        template_payload = self._template_catalog.load(template_id)
        layouts_by_id = {
            str(layout.get("id")): layout
            for layout in template_payload.get("layouts") or []
            if isinstance(layout, Mapping) and str(layout.get("id") or "").strip()
        }
        final_slides: List[Dict[str, Any]] = []
        for index, raw_slide in enumerate(slides, start=1):
            slide = copy.deepcopy(raw_slide) if isinstance(raw_slide, Mapping) else {}
            layout_id = str(slide.get("templateLayoutId") or slide.get("layout") or "")
            layout = layouts_by_id.get(layout_id) or {}
            layout_locked = bool(slide.get("layoutLocked"))
            enforced = self._enforce_slide_contract(
                slide,
                template_id,
                layout_id,
                layout,
                # The editor preview deliberately uses deterministic fitting
                # only. A locked page must produce the same layout decision at
                # final generation instead of letting a repair LLM rewrite it.
                None if layout_locked else llm_config,
                index,
            )
            recovered = None if layout_locked else self._try_overflow_layout_fallback(
                enforced,
                template_id,
                layout_id,
                layouts_by_id,
                llm_config,
                index,
            )
            final_slides.append(recovered or enforced)

        models_by_layout: Dict[str, SlideLayoutModel] = {}
        for layout_id, layout_json in layouts_by_id.items():
            model = self._layout_model(template_id, layout_id, layout_json)
            if model is not None:
                models_by_layout[layout_id] = model
        consistency_issues = validate_presentation(final_slides, models_by_layout)
        quality_errors: List[Dict[str, Any]] = []
        repair_warnings: List[Dict[str, Any]] = []
        for index, slide in enumerate(final_slides, start=1):
            qa = slide.get("_qa") if isinstance(slide.get("_qa"), Mapping) else {}
            status = str(qa.get("finalStatus") or "unknown")
            repair_count = int(qa.get("repairCount") or 0)
            if repair_count > 0:
                repair_warnings.append({
                    "slide": index,
                    "repairCount": repair_count,
                    "history": list(qa.get("repairHistory") or [])[-3:],
                })
            if status in {"partial", "unknown"}:
                quality_errors.append({
                    "slide": index,
                    "status": status,
                    "errors": list(qa.get("validationErrors") or []),
                    "details": list(qa.get("validationDetails") or []),
                    "detail": str(qa.get("enforcementError") or "页面质量校验未通过"),
                })
        quality_warnings = [
            {
                "slide": index,
                "warnings": list((slide.get("_qa") or {}).get("validationWarnings") or []),
            }
            for index, slide in enumerate(final_slides, start=1)
            if isinstance(slide.get("_qa"), Mapping)
            and (slide.get("_qa") or {}).get("validationWarnings")
        ]
        qa_report = build_qa_report(final_slides, consistency_issues, models_by_layout, template_id)
        report_path = write_qa_report(qa_report, template_id, task_id)
        qa = {
            "status": "blocked" if quality_errors else "pass",
            "consistencyIssues": consistency_issues,
            "qualityErrors": quality_errors,
            "qualityWarnings": quality_warnings,
            "repairWarnings": repair_warnings,
            "reportPath": report_path or "",
        }
        return final_slides, qa, qa_report, quality_errors

    def _start_template_preview_warmup(self) -> None:
        if os.getenv("PPT_TEMPLATE_PREVIEW_WARMUP", "1").strip().lower() in ("0", "false", "no"):
            return
        template_ids = [str(item["id"]) for item in self._template_catalog.list_templates()]
        if not template_ids:
            return
        thread = threading.Thread(
            target=warm_up_previews,
            args=(template_ids,),
            name="ai-ppt-preview-warmup",
            daemon=True,
        )
        thread.start()

    def get_template_layout_preview(self, template_id: str, slide_index: int):
        try:
            return get_template_layout_preview(template_id, slide_index)
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc
        except IndexError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc
        except Exception as exc:
            logger.exception("failed to render Presenton template layout preview")
            raise HTTPException(
                status_code=502,
                detail=f"PPT 模板版式预览生成失败：{_safe_error_message(exc)}",
            ) from exc

    def get_options(self) -> Dict[str, Any]:
        templates: List[Dict[str, Any]] = []
        for item in self._template_catalog.list_templates():
            template_id = str(item["id"])
            templates.append({
                "id": template_id,
                "name": str(item["name"]),
                "description": str(item["description"]),
                "thumbnailUrl": f"/api/app/ai/ppt/templates/{template_id}/thumbnail",
                "layoutCount": int(item.get("layout_count") or 0),
                "layouts": [
                    {
                        "id": str(layout.get("id") or ""),
                        "description": str(layout.get("description") or ""),
                        "elementTypes": list(layout.get("elementTypes") or [])[:8],
                        "slots": list(layout.get("slots") or [])[:8],
                        "previewTexts": list(layout.get("previewTexts") or [])[:8],
                    }
                    for layout in self._template_catalog.layout_summaries(template_id)
                ],
                "default": bool(item.get("is_default")),
            })
        return {
            "engine": "presenton-embedded",
            "enhancedEngineAvailable": True,
            "editorEnabled": False,
            "templateCatalogAvailable": True,
            "audiences": [
                {"value": "通用受众", "label": "通用受众", "default": True},
                {"value": "学生", "label": "学生", "default": False},
                {"value": "老师", "label": "老师", "default": False},
                {"value": "领导", "label": "领导", "default": False},
                {"value": "客户", "label": "客户", "default": False},
                {"value": "媒体", "label": "媒体", "default": False},
            ],
            "tones": [
                {"value": "简洁清晰", "label": "简洁清晰", "default": True},
                {"value": "专业严谨", "label": "专业严谨", "default": False},
                {"value": "生动活泼", "label": "生动活泼", "default": False},
                {"value": "数据说服", "label": "数据说服", "default": False},
                {"value": "亲和自然", "label": "亲和自然", "default": False},
            ],
            "templates": templates,
            "cacheTtlSeconds": 86400,
        }

    def get_template_thumbnail(self, template_id: str):
        try:
            return self._template_catalog.thumbnail(template_id)
        except Exception as exc:
            logger.exception("failed to load Presenton template thumbnail")
            raise HTTPException(
                status_code=502,
                detail=f"PPT 模板缩略图读取失败：{_safe_error_message(exc)}",
            ) from exc

    def upload_source_file(
        self,
        user_id: str,
        filename: str,
        content_type: str,
        content: bytes,
    ) -> Dict[str, Any]:
        safe_name = Path(filename).name
        extension = Path(safe_name).suffix.lower()
        allowed = {".txt", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx"}
        if extension not in allowed:
            raise HTTPException(status_code=415, detail="不支持的 PPT 资料文件格式")
        if not content:
            raise HTTPException(status_code=422, detail="上传文件不能为空")
        if len(content) > 25 * 1024 * 1024:
            raise HTTPException(status_code=413, detail="PPT 资料文件不能超过 25MB")
        file_id = f"ppt_file_{uuid.uuid4().hex}"
        _cleanup_source_files(self._embedded_config.source_root)
        storage_name = f"{uuid.uuid4().hex}{extension}"
        local_path = (self._embedded_config.source_root / storage_name).resolve()
        source_root = self._embedded_config.source_root.resolve()
        if not local_path.is_relative_to(source_root):
            raise HTTPException(status_code=400, detail="PPT 资料文件名无效")
        temporary_path = local_path.with_suffix(f"{local_path.suffix}.tmp")
        try:
            temporary_path.write_bytes(content)
            os.replace(temporary_path, local_path)
        finally:
            if temporary_path.exists():
                temporary_path.unlink(missing_ok=True)
        metadata = {
            "fileId": file_id,
            "userId": str(user_id),
            "name": safe_name,
            "size": len(content),
            "contentType": content_type,
            "localPath": str(local_path),
        }
        self._source_files.put(file_id, metadata)
        return {key: value for key, value in metadata.items() if key not in {"userId", "localPath"}}

    def generate_outline(
        self,
        request: Mapping[str, Any],
        llm_config: Any,
        user_id: str = "",
        progress_callback: Optional[Callable[[Mapping[str, Any]], None]] = None,
    ) -> Dict[str, Any]:
        source = self._resolve_source_text(request, user_id)
        topic = str(request.get("topic") or request.get("sourceName") or "演示文稿").strip()
        if not source:
            raise HTTPException(status_code=422, detail="sourceContent 和 sourceFileId 不能同时为空")

        reported_progress = 0

        def report_progress(stage: str, progress: int, message: str, detail: str = "") -> None:
            nonlocal reported_progress
            if progress_callback is None:
                return
            reported_progress = max(reported_progress, min(99, int(progress)))
            progress_callback(
                {
                    "stage": stage,
                    "progress": reported_progress,
                    "message": message,
                    "detail": detail,
                }
            )

        report_progress(
            "preparing",
            12,
            "资料读取完成，正在整理生成参数",
            "已准备主题、资料和页面数量约束",
        )
        outline_mode = _normalize_outline_mode(request.get("outlineMode"))
        topic_only = _is_topic_only_outline_request(request, source, topic, outline_mode)
        topic = _resolve_outline_topic(request, source, topic, topic_only)
        if not topic_only:
            source_title = _source_document_title(source)
            source_name_title = _source_name_title(request.get("sourceName"))
            if _is_generic_outline_topic(topic) or Path(topic).suffix:
                topic = source_name_title or source_title or topic
        # 有资料时由 AI 根据资料内容判断页数；只有主题时则必须按主题展开成可编辑的内容计划。
        # 两种模式都尊重用户档位的上限，但主题模式不能把一句话压缩成一页。
        max_pages = max(0, int(request.get("pageCount") or 0))
        if max_pages <= 0:
            max_pages = PPT_OUTLINE_MAX_PAGES
        min_pages = min(PPT_OUTLINE_MIN_PAGES, max_pages)
        if topic_only:
            constraints = (
                "用户只提供了一个 PPT 主题，没有上传资料。请基于通用知识和该主题设计完整的内容计划，"
                f"必须展开到 {min_pages} 至 {max_pages} 页；可以补充通用概念、知识结构、方法、应用和总结，"
                "但不得虚构具体学校、人物、日期、统计数字、实验结果或未经用户提供的事实。"
            )
            source_mode = "topic_only"
            material = f"以下内容仅是用户给出的主题，不是需要逐字摘要的资料：{source}"
        elif outline_mode == "ai_outline":
            constraints = (
                "这是非大纲模式。上传资料只作为主题、事实和方向参考，不能把原文目录直接当成最终大纲；"
                f"必须先自主拆解主题、补齐知识链路并重新搭建 {min_pages} 至 {max_pages} 页的叙事框架。"
                "允许补充与主题直接相关的通用概念、定义、方法、应用场景和解释性例子，"
                "但不得虚构具体人物、机构、日期、统计数字、实验结果或无法由资料支持的事实。"
            )
            source_mode = "non_outline"
            material = _compact_outline_source(source, PPT_OUTLINE_SOURCE_MAX_CHARS)
        else:
            constraints = (
                "这是大纲模式。严格保留上传大纲的章节顺序和结构，只对标题和要点做必要的可编辑化整理；"
                "不得自行改造为另一套框架，也不得补造资料未提供的事实。"
            )
            source_mode = "outline_grounded"
            material = _compact_outline_source(source, PPT_OUTLINE_SOURCE_MAX_CHARS)
        prompt_payload = {
            "topic": topic,
            "audience": str(request.get("audience") or "通用受众"),
            "tone": str(request.get("tone") or "简洁清晰"),
            "source_mode": source_mode,
            "outline_mode": outline_mode,
            "min_pages": min_pages,
            "max_pages": max_pages,
            "constraints": constraints,
            # 资料放 user_input 而非 evidence：normalize_evidence 会把
            # evidence content 截断到 1200 字符，导致大纲只能看到资料开头。
            "material": material,
            "detail_level": "deep",
            "planning_requirements": {
                "internal_planning": "先完成主题拆解、受众适配、叙事顺序和页间递进，再只输出最终大纲，不输出思考过程。",
                "key_points_per_page": "3-5 条，必须是可直接转成页面正文的具体信息，不写空泛方向。",
                "nodes_per_page": "2-4 个页面节点；每个节点包含节点标题和面向观众的具体说明。",
                "storyline": "叙事阶段由主题类型决定；只覆盖与主题直接相关的背景、问题、事实、选择、方法、应用、总结或下一步，不强行补齐不适用的阶段。",
                "page_roles": "每页只承担一个核心结论，明确与前后页的关系；封面和目录不得吞掉正文内容。",
            },
            # 只保留主题路由这一项语义增强；不把模板字段、布局或生产指令
            # 继续塞进大纲提示词，避免主题规划与模板生成互相约束。
            "topic_interpretation": _outline_topic_guidance(topic, audience=str(request.get("audience") or "通用受众"), source_mode=source_mode),
        }
        report_progress(
            "planning",
            22,
            "正在拆解主题并组织大纲结构",
            "正在准备模型生成所需的叙事顺序和页面职责",
        )
        evidence = []
        timeout_token = set_active_llm_timeout(PPT_OUTLINE_LLM_TIMEOUT_SECONDS)
        output_token = set_active_max_output_tokens(
            _outline_output_token_budget(min_pages, max_pages)
        )
        effort_token = set_active_reasoning_effort(PPT_OUTLINE_REASONING_EFFORT)
        token = set_active_llm_config(llm_config)
        model_name = str(getattr(llm_config, "model", "") or "")
        recovery_reason = ""
        try:
            markdown = ""
            items: List[Dict[str, Any]] = []
            min_acceptable = min_pages
            attempt = 0
            topic_page_completion = False
            while True:
                report_progress(
                    "model_generation",
                    30 + min(attempt * 30, 60),
                    "正在请求模型生成大纲" if attempt == 0 else f"正在进行第 {attempt + 1} 次大纲补充生成",
                    "模型单次调用完成后才会更新到下一阶段",
                )
                try:
                    markdown = _retry_llm_call(
                        lambda: run_specialist_agent(
                            "ppt_outline_agent",
                            json.dumps(prompt_payload, ensure_ascii=False),
                            evidence,
                        ),
                        max_retries=PPT_OUTLINE_LLM_MAX_RETRIES,
                    )
                except HTTPException as exc:
                    if not _is_recoverable_outline_error(exc):
                        raise
                    recovery_reason = _safe_error_message(exc)
                    logger.warning(
                        "PPT outline model unavailable provider=%s model=%s; recovering from source material: %s",
                        getattr(llm_config, "provider", ""),
                        model_name,
                        recovery_reason,
                    )
                    markdown = ""
                    items = []
                    break
                except Exception as exc:
                    # 连接类故障可以依据原始资料恢复；普通运行时错误应明确返回
                    # 网关错误，不能静默生成一份看似成功但质量不可控的大纲。
                    if not _is_outline_transport_error(exc):
                        raise HTTPException(
                            status_code=502,
                            detail=f"PPT 大纲无法从模型或原始资料中恢复：{_safe_error_message(exc)}",
                        ) from exc
                    recovery_reason = _safe_error_message(exc)
                    logger.warning(
                        "PPT outline model call failed provider=%s model=%s; recovering from source material: %s",
                        getattr(llm_config, "provider", ""),
                        model_name,
                        recovery_reason,
                    )
                    markdown = ""
                    items = []
                    break
                items = _normalize_outline_topic_items(_outline_items(markdown), topic)
                report_progress(
                    "parsing",
                    48 + min(attempt * 24, 48),
                    "模型已返回，正在解析大纲结构",
                    "正在整理页面标题、要点和层级信息",
                )
                if _is_generic_topic_scaffold(items, topic, source_mode):
                    recovery_reason = "模型返回了与主题无关的通用大纲骨架，已按主题语义重排"
                    logger.warning(
                        "PPT outline returned generic scaffold for topic=%s; replacing with semantic scaffold",
                        topic,
                    )
                    items = _topic_outline_items(
                        topic,
                        min_pages,
                        max_pages,
                        audience=str(request.get("audience") or "通用受众"),
                        tone=str(request.get("tone") or "简洁清晰"),
                        seed_items=items,
                    )
                    markdown = _outline_markdown_from_items(items, topic)
                items, coverage_repaired = _repair_material_outline_coverage(
                    items, source, topic, max_pages
                )
                if coverage_repaired:
                    markdown = _outline_markdown_from_items(items, topic)
                depth_deficits = (
                    _outline_depth_deficits(items)
                    if source_mode != "outline_grounded"
                    else []
                )
                report_progress(
                    "quality_check",
                    56 + min(attempt * 20, 40),
                    "正在检查大纲质量",
                    f"正在检查页数、内容覆盖和结构完整性（当前 {len(items)} 页）",
                )
                # 主题模式只要已经返回至少一页可解析内容，就不再因为页数
                # 短缺而重新请求模型。后面用主题语义骨架补齐缺页，避免一次
                # 可用的 1/5 或 3/5 页结果被第二次不稳定请求拖入连接错误和兜底链。
                # 真正的空响应会在模型调用异常分支中直接进入恢复链。
                if topic_only and items and len(items) < min_acceptable:
                    topic_page_completion = True
                    logger.info(
                        "PPT outline accepted usable topic outline pages=%d/%d; completing missing pages locally",
                        len(items),
                        min_acceptable,
                    )
                    break
                # 其他模式仍保留页数质量重试；内容稀疏改为后面的定向页面修复，
                # 避免一页缺两个要点时再次等待整份大纲。
                if len(items) >= min_acceptable or attempt >= PPT_OUTLINE_MAX_RETRIES:
                    break
                attempt += 1
                report_progress(
                    "model_retry",
                    60 + min((attempt - 1) * 30, 30),
                    f"大纲页数不足，正在进行第 {attempt + 1} 次补充生成",
                    f"当前已整理 {len(items)} 页，目标至少 {min_acceptable} 页",
                )
                logger.warning(
                    "PPT outline 质量不足（页数=%d/%d，薄弱页面=%s），带纠正提示重试（第 %d/%d 次）",
                    len(items),
                    min_acceptable,
                    ",".join(depth_deficits[:6]) or "无",
                    attempt,
                    PPT_OUTLINE_MAX_RETRIES,
                )
                corrections = []
                if len(items) < min_acceptable:
                    corrections.append(
                        f"上次输出只有 {len(items)} 页，少于下限 {min_acceptable} 页；请展开到 {min_acceptable}-{max_pages} 页。"
                    )
                prompt_payload["correction"] = "".join(corrections) + (
                    "逐页使用 ### 第N页 编号，不得合并页面，不得只输出章节标题或一句概括。"
                )
            # 重试后仍不足两条：先把单页的要点拆成逐页；主题模式再用通用结构补齐，
            # 避免把“只有一个主题”误判成模型不可恢复。该结构只补充内容组织方式，
            # 不添加具体事实。
            if len(items) < 2:
                items = _expand_single_page_outline(items, topic)
                if len(items) >= 2:
                    markdown = _outline_markdown_from_items(items, topic)
            if topic_only and len(items) < min_acceptable:
                if topic_page_completion and not recovery_reason:
                    logger.info(
                        "PPT outline topic completion pages=%d/%d uses semantic scaffold without fallback",
                        len(items),
                        min_acceptable,
                    )
                else:
                    recovery_reason = recovery_reason or f"主题模式模型仅返回 {len(items)} 页，已按主题扩展为可编辑结构"
                items = _topic_outline_items(
                    topic,
                    min_acceptable,
                    max_pages,
                    audience=str(request.get("audience") or "通用受众"),
                    tone=str(request.get("tone") or "简洁清晰"),
                    seed_items=items,
                )
                markdown = _outline_markdown_from_items(items, topic)
            if len(items) < 2:
                recovery_reason = recovery_reason or f"模型仅返回 {len(items)} 页，无法形成可编辑大纲"
                items = _source_outline_items(source, topic, max_pages)
                if len(items) < 2:
                    raise HTTPException(
                        status_code=502,
                        detail=f"PPT 大纲无法从模型或原始资料中恢复：{recovery_reason}",
                    )
                markdown = _outline_markdown_from_items(items, topic)
            if not topic_only:
                items, coverage_repaired = _repair_material_outline_coverage(
                    items, source, topic, max_pages
                )
                if coverage_repaired:
                    markdown = _outline_markdown_from_items(items, topic)
            if source_mode != "outline_grounded" and not (
                topic_only and topic_page_completion
            ):
                repaired_items, repaired = _repair_outline_sparse_pages(
                    items,
                    prompt_payload,
                    topic,
                    evidence,
                )
                if repaired:
                    items = repaired_items
                    markdown = _outline_markdown_from_items(items, topic)
            report_progress(
                "finalizing",
                96,
                "正在完成大纲质量校验",
                "正在整理为可编辑的大纲数据",
            )
        finally:
            reset_active_llm_config(token)
            reset_active_reasoning_effort(effort_token)
            reset_active_llm_timeout(timeout_token)
            reset_active_max_output_tokens(output_token)
        result = {
            "outlineId": f"outline_{uuid.uuid4().hex}",
            "title": topic,
            "items": items,
            "outlineMarkdown": markdown,
        }
        source_trace = build_source_trace(
            str(request.get("sourceName") or topic),
            source,
            str(request.get("sourceFileId") or ""),
        )
        outline_quality = assess_outline_quality(
            source,
            {"title": topic, "items": items},
        )
        result["sourceTrace"] = source_trace
        result["contentQuality"] = {"outline": outline_quality}
        if recovery_reason:
            if topic_only:
                result["generationMode"] = "topic_recovery"
                result["warnings"] = ["模型未按目标页数展开，已依据主题补齐可编辑大纲结构；未添加具体未提供事实。"]
            else:
                result["generationMode"] = "source_recovery"
                result["warnings"] = ["模型服务暂时不可用，已依据上传资料生成可编辑大纲；内容未补造事实。"]
        else:
            result["generationMode"] = "ai"
        result.setdefault("warnings", []).extend(
            quality_warning_messages(outline_quality)
        )
        return result

    def generate_slides(
        self,
        request: Mapping[str, Any],
        llm_config: Any,
        user_id: str = "",
        progress_callback: Optional[Callable[[Mapping[str, Any]], None]] = None,
        task_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        outline = request.get("outline")
        if not isinstance(outline, Mapping):
            raise HTTPException(status_code=422, detail="outline 必须是对象")
        items = outline.get("items")
        if not isinstance(items, list) or not items:
            markdown = str(outline.get("outlineMarkdown") or "").strip()
            items = _outline_items(markdown)
        if not items:
            raise HTTPException(status_code=422, detail="outline.items 不能为空")
        settings = request.get("settings") if isinstance(request.get("settings"), Mapping) else {}
        # 受众/语气参数化：注入 settings，随内容提示词传给模型
        content_settings = dict(settings)
        if request.get("audience"):
            content_settings["audience"] = str(request.get("audience"))
        if request.get("tone"):
            content_settings["tone"] = str(request.get("tone"))
        template_id = template_for_settings(settings, self._embedded_config.default_template)
        if not self._template_catalog.contains(template_id):
            template_id = self._embedded_config.default_template
        layout_catalog = self._template_catalog.layout_summaries(template_id)
        template_payload = self._template_catalog.load(template_id)
        layouts_by_id = {
            str(layout.get("id")): layout
            for layout in template_payload.get("layouts") or []
            if isinstance(layout, Mapping) and str(layout.get("id") or "").strip()
        }
        safe_layout_catalog = [
            summary
            for summary in layout_catalog
            if self._layout_is_generation_safe(
                template_id,
                str(summary.get("id") or ""),
                layouts_by_id.get(str(summary.get("id") or "")) or {},
            )
        ]
        if safe_layout_catalog:
            excluded_layouts = [
                str(summary.get("id") or "")
                for summary in layout_catalog
                if summary not in safe_layout_catalog
            ]
            if excluded_layouts:
                logger.warning(
                    "PPT generation excluded layouts with unusable title geometry: %s",
                    excluded_layouts,
                )
            layout_catalog = safe_layout_catalog
        # When visuals are explicitly disabled, exclude layouts whose
        # composition depends on a non-decorative image frame. Otherwise the
        # text remains valid but the empty visual area creates a large blank
        # region in the rendered slide.
        if not _visuals_enabled(settings):
            text_only_catalog = [
                summary
                for summary in layout_catalog
                if not _layout_has_required_visual(
                    layouts_by_id.get(str(summary.get("id") or "")) or {}
                )
            ]
            if text_only_catalog:
                excluded_visual_layouts = [
                    str(summary.get("id") or "")
                    for summary in layout_catalog
                    if summary not in text_only_catalog
                ]
                if excluded_visual_layouts:
                    logger.info(
                        "PPT generation excluded visual-dependent layouts because visuals are disabled: %s",
                        excluded_visual_layouts,
                    )
                layout_catalog = text_only_catalog
        # 容量感知（第 24 节）：给版式匹配模型注入每版式容量提示，
        # 让"几卡片内容"匹配"几卡片版式"，而不是硬塞
        for summary in layout_catalog:
            summary_id = str(summary.get("id") or "")
            layout_model = self._layout_model(template_id, summary_id, layouts_by_id.get(summary_id) or {})
            if layout_model is None:
                continue
            body_chars = [
                element.constraint.hard_max_chars
                for elements in layout_model.elements.values()
                for element in elements
                if element.constraint and element.role in {"body", "card"}
            ]
            body_slot_capacities = sorted(
                (
                    int(element.constraint.hard_max_chars)
                    for elements in layout_model.elements.values()
                    for element in elements
                    if element.constraint
                    and element.semantic_role in {"body", "card_body", "bullet_body", "metric_description"}
                ),
                reverse=True,
            )
            summary["capacityHints"] = {
                # card_groups is split by semantic role (title/body/value),
                # so its dictionary length is not the number of visual cards.
                # Use the largest repeated semantic group as the actual card
                # capacity consumed by the structure selector.
                "cards": max(
                    (len(members) for members in layout_model.card_groups.values()),
                    default=0,
                ),
                "maxBodyChars": max(body_chars) if body_chars else 0,
                "minBodyChars": min(body_slot_capacities) if body_slot_capacities else 0,
                "bodySlotCapacities": body_slot_capacities[:12],
                "slotCount": sum(len(values) for values in layout_model.elements.values()),
                "semanticSlotCount": sum(
                    1
                    for elements in layout_model.elements.values()
                    for element in elements
                    if element.element_type in {"text", "text-list"} and element.mutable_text
                ),
                "requiresNumericData": any(
                    element.semantic_role in {"metric_value", "card_value"}
                    for elements in layout_model.elements.values()
                    for element in elements
                ),
            }
        structure_prompt = json.dumps({
            "presentonStructureRules": PRESENTON_STRUCTURE_RULES,
            "templateId": template_id,
            "slideCount": len(items),
            "layouts": layout_catalog,
            "outline": items,
            "settings": content_settings,
            "userInstructions": str(request.get("sharedPrompt") or request.get("instructions") or ""),
            "slideInstructions": [
                {
                    "index": index + 1,
                    "privatePrompt": str(item.get("privatePrompt") or ""),
                }
                for index, item in enumerate(items)
                if isinstance(item, Mapping) and str(item.get("privatePrompt") or "").strip()
            ],
        }, ensure_ascii=False)
        timeout_token = set_active_llm_timeout(PPT_OUTLINE_LLM_TIMEOUT_SECONDS)
        output_token = set_active_max_output_tokens(PPT_STRUCTURE_MAX_OUTPUT_TOKENS)
        token = set_active_llm_config(llm_config)
        try:
            try:
                if progress_callback:
                    # 版式匹配是一次较慢的 LLM 调用，先回报阶段避免前端长时间无反馈
                    progress_callback({
                        "stage": "structuring",
                        "progress": 6,
                        "totalSlides": len(items),
                        "completedSlides": 0,
                        "remainingSlides": len(items),
                        "processingSlides": [],
                        "message": "正在为每页匹配模板版式",
                    })
                structure_answer = _retry_llm_call(
                    lambda: run_specialist_agent("ppt_structure_agent", structure_prompt, [])
                )
                structure_payload = normalize_structure_answer(structure_answer)
                selected_by_index = {
                    int(value["slideIndex"]): str(value["layoutId"])
                    for value in structure_payload["layouts"]
                }
                selected_layouts = [
                    selected_by_index.get(index, "")
                    for index in range(1, len(items) + 1)
                ]
            except Exception as exc:
                logger.warning(
                    "Presenton structure selection failed; using deterministic layout fallback: %s",
                    _safe_error_message(exc),
                )
                selected_layouts = ["" for _ in items]
        finally:
            reset_active_llm_config(token)
            reset_active_llm_timeout(timeout_token)
            reset_active_max_output_tokens(output_token)
        enriched_slides: List[Dict[str, Any]] = []
        generation_warnings: List[str] = []
        valid_layout_ids = {str(item["id"]) for item in layout_catalog}
        selected_layouts = [
            value if value in valid_layout_ids else ""
            for value in selected_layouts
        ]
        # LLM 版式选择是首选，但不能让一次重复/漏页输出把整份 PPT
        # 退化成同一张模板。这里仅在缺失、非法或相邻重复时做确定性
        # 修正，保留强语义页（表格、图表、封面、总结）的模型选择。
        selected_layouts = _rebalance_layout_choices(
            selected_layouts,
            layout_catalog,
            items,
        )
        if progress_callback:
            progress_callback({
                "stage": "writing",
                "progress": 10,
                "currentSlide": 1,
                "totalSlides": len(items),
                "completedSlides": 0,
                "remainingSlides": len(items),
                "processingSlides": [],
                "message": "布局已确定，准备逐页生成内容",
            })
        source = self._resolve_source_text(request, user_id)
        if self._embedded_config.enabled:
            try:
                enriched_slides = self._generate_presenton_ui_slides(
                    outline=outline,
                    items=items,
                    settings=content_settings,
                    template_id=template_id,
                    selected_layouts=selected_layouts,
                    layouts_by_id=layouts_by_id,
                    shared_prompt=str(request.get("sharedPrompt") or ""),
                    source=source,
                    llm_config=llm_config,
                    task_id=task_id,
                    progress_callback=progress_callback,
                )
            except Exception as exc:
                logger.exception("ppt content UI generation failed")
                raise HTTPException(
                    status_code=502,
                    detail=(
                        "PPT 内容模型未返回符合 Presenton UI Schema 的完整页面："
                        + _safe_error_message(exc)
                    ),
                ) from exc
        slides = []
        fallback_count = sum(
            1
            for enriched in enriched_slides
            if isinstance(enriched, Mapping) and (enriched.get("_contentFallback") or enriched.get("_generationError"))
        )
        if fallback_count:
            generation_warnings.append(f"{fallback_count} 页未获得 AI 组件内容，已用大纲要点填充版式")
        for index, item in enumerate(items, start=1):
            if not isinstance(item, Mapping):
                continue
            enriched = enriched_slides[index - 1] if index <= len(enriched_slides) else {}
            image_mode = str(settings.get("imageMode") or "placeholder").strip().lower()
            wants_ai_image = (
                image_mode == "ai"
                and bool(settings.get("includeVisuals"))
                and bool(str(enriched.get("visualPrompt") or "").strip())
            )
            # 页面正文只接受内容智能体或用户明确输入的 content。
            # 不能再把 outline.keyPoints 静默降级成最终页面正文，否则生成失败时
            # 页面会看起来像“把大纲直接做成了 PPT”。
            points = enriched.get("content") or item.get("content") or []
            if isinstance(points, str):
                points = [line.strip(" -") for line in points.splitlines() if line.strip()]
            slides.append({
                "index": index,
                "type": enriched.get("type") or item.get("type") or item.get("pageType") or "content",
                "title": str(enriched.get("title") or item.get("title") or f"第 {index} 页"),
                "content": list(points) if isinstance(points, list) else [],
                "objective": str(enriched.get("objective") or item.get("objective") or ""),
                # Per-slide source evidence is trace metadata used by the
                # content-quality audit. Keep it alongside the rendered UI;
                # dropping it here made a grounded page look ungrounded even
                # though the content agent had received the evidence.
                "sourceMaterial": str(
                    enriched.get("sourceMaterial")
                    or enriched.get("sourceExcerpt")
                    or item.get("sourceMaterial")
                    or item.get("sourceExcerpt")
                    or ""
                ),
                "layout": selected_layouts[index - 1],
                "templateLayoutId": selected_layouts[index - 1],
                "privatePrompt": str(item.get("privatePrompt") or ""),
                "speakerNote": str(
                    enriched.get("speakerNote")
                    or _fallback_speaker_note(enriched, item)
                ),
                "visualPrompt": str(enriched.get("visualPrompt") or ""),
                "imagePath": str(enriched.get("imagePath") or ""),
                "imageMode": image_mode,
                "imageStatus": (
                    "generated" if enriched.get("imagePath")
                    else "pending" if wants_ai_image
                    else "placeholder"
                ),
                # The renderer receives the complete Presenton UI tree. No
                # application-side slot or coordinate hydration is performed.
                "ui": copy.deepcopy(enriched.get("ui") if isinstance(enriched.get("ui"), Mapping) else {}),
                "_qa": copy.deepcopy(enriched.get("_qa")) if isinstance(enriched.get("_qa"), Mapping) else {},
                "_unknownElementIds": list(enriched.get("_unknownElementIds") or []),
            })
        # 跨页一致性 + QA 报告（第 62-64、78-79 节）。
        # 未走修复闭环的页面（大纲兜底填充）也补一次只读校验，保证报告完整。
        models_by_layout: Dict[str, SlideLayoutModel] = {}
        for lid, layout_json in layouts_by_id.items():
            model = self._layout_model(template_id, lid, layout_json)
            if model is not None:
                models_by_layout[lid] = model
        for index, slide in enumerate(slides, start=1):
            if slide.get("_qa") or not isinstance(slide.get("ui"), Mapping):
                continue
            model = models_by_layout.get(str(slide.get("templateLayoutId") or ""))
            if model is None:
                continue
            try:
                validation = validate_slide(slide["ui"], model)
                slide["_qa"] = {
                    "layoutId": str(slide.get("templateLayoutId") or ""),
                    "semanticType": str(slide.get("type") or ""),
                    "contentLength": sum(len(str(p)) for p in (slide.get("content") or []) if p),
                    "validationErrors": [
                        issue.error_type for issue in validation.issues
                        if issue.severity == "error"
                    ],
                    "validationWarnings": [
                        issue.error_type for issue in validation.issues
                        if issue.severity == "warning"
                    ],
                    "repairCount": 0,
                    "finalStatus": "clean" if not validation.has_errors else "partial",
                    "repairHistory": [],
                    "densityLevel": validation.density_level,
                    "fillRatio": validation.fill_ratio,
                }
            except Exception as exc:
                logger.warning("PPT slide %d QA validation failed: %s", index, _safe_error_message(exc))
        source_trace = build_source_trace(
            str(request.get("sourceName") or outline.get("title") or "演示文稿"),
            source,
            str(request.get("sourceFileId") or ""),
        )
        content_quality = assess_content_quality(
            source,
            {"title": outline.get("title") or "", "items": items},
            slides,
            source_trace=source_trace,
        )
        generation_warnings.extend(quality_warning_messages(content_quality))
        for index, slide in enumerate(slides):
            if not isinstance(slide.get("_qa"), Mapping):
                slide["_qa"] = {}
            slide["_qa"]["contentQuality"] = (
                content_quality.get("slides") or [{}]
            )[index] if index < len(content_quality.get("slides") or []) else {}
        consistency_issues = validate_presentation(slides, models_by_layout)
        if consistency_issues:
            logger.warning("PPT presentation consistency issues: %s", consistency_issues)
        qa_report = build_qa_report(
            slides,
            consistency_issues,
            models_by_layout,
            template_id,
            content_quality=content_quality,
        )
        report_path = write_qa_report(qa_report, template_id)
        logger.debug("PPT QA report:\n%s", qa_report)
        return {
            "slides": slides,
            "sharedPrompt": str(request.get("sharedPrompt") or "简洁、清晰"),
            "warnings": generation_warnings,
            "sourceTrace": source_trace,
            "contentQuality": content_quality,
            "qa": {
                "consistencyIssues": consistency_issues,
                "reportPath": report_path or "",
            },
            # Keep the historical response field for existing clients. The
            # actual decision now comes from Presenton structure JSON above.
            "layoutMarkdown": _selected_layouts_markdown(selected_layouts, items),
        }

    def create_slides_task(
        self,
        user_id: str,
        request: Mapping[str, Any],
        llm_config: Any,
    ) -> Dict[str, Any]:
        """Queue per-slide AI generation and return immediately.

        This task ends after complete Presenton UI JSON is available. The later
        `/tasks` endpoint still owns HTML/PPTX rendering, so the client can
        edit the generated pages between these two phases.
        """
        outline = request.get("outline")
        if not isinstance(outline, Mapping):
            raise HTTPException(status_code=422, detail="outline 必须是对象")
        items = outline.get("items")
        if not isinstance(items, list) or len(items) < 2:
            raise HTTPException(status_code=422, detail="outline.items 至少需要两页")
        task_id = f"ppt_task_{uuid.uuid4().hex}"
        now = int(time.time() * 1000)
        task = {
            "taskId": task_id,
            "kind": "slide_generation",
            "userId": user_id,
            "status": "queued",
            "progress": 0,
            "stage": "queued",
            "message": "逐页内容生成已进入队列",
            "currentSlide": 0,
            "totalSlides": len(items),
            "completedSlides": 0,
            "remainingSlides": len(items),
            "processingSlides": [],
            "createdAt": now,
            "updatedAt": now,
            "deadlineAt": self._deadline_ms(PPT_TASK_TIMEOUT_SECONDS),
            "outline": copy.deepcopy(dict(outline)),
            "slides": [],
            "sourceName": str(request.get("sourceName") or "演示文稿"),
            "sharedPrompt": str(request.get("sharedPrompt") or ""),
            "settings": copy.deepcopy(request.get("settings") or {}),
            "error": None,
        }
        self._register_task(task)
        self._executor.submit(
            self._execute_slides_task,
            task_id,
            copy.deepcopy(dict(request)),
            llm_config,
            user_id,
        )
        return {
            "taskId": task_id,
            "status": "queued",
            "totalSlides": len(items),
            "completedSlides": 0,
            "remainingSlides": len(items),
        }

    def create_outline_task(
        self,
        user_id: str,
        request: Mapping[str, Any],
        llm_config: Any,
    ) -> Dict[str, Any]:
        """Queue outline generation so the HTTP request never carries an LLM wait."""
        if not str(request.get("sourceContent") or request.get("sourceFileId") or "").strip():
            raise HTTPException(status_code=422, detail="sourceContent 和 sourceFileId 不能同时为空")
        task_id = f"ppt_task_{uuid.uuid4().hex}"
        now = int(time.time() * 1000)
        task = {
            "taskId": task_id,
            "kind": "outline_generation",
            "userId": user_id,
            "status": "queued",
            "progress": 0,
            "stage": "queued",
            "message": "大纲生成已进入队列",
            "createdAt": now,
            "updatedAt": now,
            "deadlineAt": self._deadline_ms(PPT_OUTLINE_TASK_TIMEOUT_SECONDS),
            "sourceName": str(request.get("sourceName") or "演示文稿"),
            "outline": {},
            "items": [],
            "outlineMarkdown": "",
            "error": None,
        }
        self._register_task(task)
        self._executor.submit(
            self._execute_outline_task,
            task_id,
            copy.deepcopy(dict(request)),
            llm_config,
            user_id,
        )
        return {"taskId": task_id, "status": "queued", "progress": 0}

    def _execute_outline_task(
        self,
        task_id: str,
        request: Dict[str, Any],
        llm_config: Any,
        user_id: str,
    ) -> None:
        last_progress = 8

        def report(event: Mapping[str, Any]) -> None:
            nonlocal last_progress
            if self._task_is_stopped(task_id):
                return
            payload = dict(event)
            try:
                progress = int(payload.get("progress", last_progress))
            except (TypeError, ValueError):
                progress = last_progress
            last_progress = max(last_progress, min(99, progress))
            payload["progress"] = last_progress
            self._update(task_id, **payload)

        try:
            if self._task_is_stopped(task_id):
                return
            self._update(
                task_id,
                status="running",
                stage="outline",
                progress=8,
                message="正在分析资料并生成大纲",
            )
            result = self.generate_outline(
                request,
                llm_config,
                user_id,
                progress_callback=report,
            )
            if self._task_is_stopped(task_id):
                return
            items = result.get("items") or []
            self._update(
                task_id,
                status="completed",
                stage="completed",
                progress=100,
                message=(
                    "大纲生成完成（已依据上传资料恢复）"
                    if result.get("generationMode") == "source_recovery"
                    else "大纲生成完成"
                ),
                title=result.get("title") or "演示文稿",
                items=items,
                outline=result,
                outlineMarkdown=result.get("outlineMarkdown") or "",
                generationMode=result.get("generationMode") or "ai",
                warnings=result.get("warnings") or [],
                sourceTrace=result.get("sourceTrace") or {},
                contentQuality=result.get("contentQuality") or {},
            )
        except PptTaskStopped:
            return
        except Exception as exc:
            if self._task_is_stopped(task_id):
                return
            logger.exception("async PPT outline generation failed")
            self._update(
                task_id,
                status="failed",
                stage="failed",
                progress=last_progress,
                message="大纲生成失败",
                error={"type": exc.__class__.__name__, "message": _safe_error_message(exc)},
            )

    def _execute_slides_task(
        self,
        task_id: str,
        request: Dict[str, Any],
        llm_config: Any,
        user_id: str,
    ) -> None:
        try:
            self._update(
                task_id,
                status="running",
                stage="structuring",
                progress=3,
                message="正在分析页面结构和模板布局",
            )

            def report(event: Mapping[str, Any]) -> None:
                if self._is_cancelled(task_id):
                    return
                self._update(task_id, **dict(event))

            result = self.generate_slides(
                request,
                llm_config,
                user_id,
                progress_callback=report,
                task_id=task_id,
            )
            if self._is_cancelled(task_id):
                return
            total = len(result.get("slides") or [])
            self._update(
                task_id,
                status="completed",
                stage="completed",
                progress=100,
                message="逐页内容生成完成",
                slides=result.get("slides") or [],
                sharedPrompt=result.get("sharedPrompt") or "",
                layoutMarkdown=result.get("layoutMarkdown") or "",
                warnings=result.get("warnings") or [],
                sourceTrace=result.get("sourceTrace") or {},
                contentQuality=result.get("contentQuality") or {},
                currentSlide=total,
                totalSlides=total,
                completedSlides=total,
                remainingSlides=0,
                processingSlides=[],
            )
        except Exception as exc:
            if self._task_is_stopped(task_id):
                return
            logger.exception("async PPT slide generation failed")
            self._update(
                task_id,
                status="failed",
                stage="failed",
                progress=100,
                message="逐页内容生成失败",
                error={"type": exc.__class__.__name__, "message": _safe_error_message(exc)},
            )

    def _generate_presenton_ui_slides(
        self,
        *,
        outline: Mapping[str, Any],
        items: List[Mapping[str, Any]],
        settings: Mapping[str, Any],
        template_id: str,
        selected_layouts: List[str],
        layouts_by_id: Mapping[str, Mapping[str, Any]],
        shared_prompt: str,
        source: str,
        llm_config: Any,
        task_id: Optional[str] = None,
        progress_callback: Optional[Callable[[Mapping[str, Any]], None]] = None,
    ) -> List[Dict[str, Any]]:
        """Generate Presenton UI trees in batches of PPT_CONTENT_BATCH_SIZE pages.

        Each batch is one LLM call. Per-batch error isolation means a single
        failed batch doesn't discard successfully generated pages — it falls
        back to outline content with a minimal placeholder UI.
        """
        total = len(items)
        batch_size = PPT_CONTENT_BATCH_SIZE
        batches = [
            list(range(i, min(i + batch_size, total)))
            for i in range(0, total, batch_size)
        ]

        def _generate_batch(indices: List[int]) -> List[Dict[str, Any]]:
            if self._task_is_stopped(task_id):
                raise PptTaskStopped()
            batch_layout_ids = [selected_layouts[i] for i in indices]
            unique_layouts: List[Dict[str, Any]] = []
            seen: set[str] = set()
            batch_layouts_by_id: Dict[str, Dict[str, Any]] = {}
            for i in indices:
                lid = selected_layouts[i]
                if lid not in seen:
                    seen.add(lid)
                    batch_layouts_by_id[lid] = copy.deepcopy(layouts_by_id.get(lid) or {})
                schema = self._template_catalog.component_schema(
                    batch_layouts_by_id[lid].get("components") or []
                )
                # 容量注入：把每槽位的建议/硬上限/行数告诉模型（第 57 节），
                # 让 AI 先按容量写内容，而不是写完再塞；role 告诉模型
                # 每个槽位的排布语义（标题/正文/卡片/标签），内容按位就座
                layout_model = self._layout_model(template_id, lid, batch_layouts_by_id[lid])
                if layout_model is not None:
                    for entry in schema:
                        element = layout_model.element(
                            str(entry.get("name") or ""),
                            int(entry.get("occurrence") or 0),
                        )
                        if element is None:
                            continue
                        entry["role"] = element.role
                        entry["semanticRole"] = element.semantic_role
                        entry["contentContract"] = semantic_content_contract(
                            element.semantic_role,
                            element.constraint,
                        )
                        constraint = element.constraint
                        if constraint is not None:
                            entry["capacity"] = {
                                "recommendedChars": constraint.recommended_chars,
                                "hardMaxChars": constraint.hard_max_chars,
                                "maxLines": constraint.max_lines,
                                "charsPerLine": round(constraint.chars_per_line),
                            }
                unique_layouts.append({
                    "slideIndex": i + 1,
                    "layoutId": lid,
                    "componentSchema": schema,
                    # 把模板的重复区域转换成内容契约的一部分。模型只需
                    # 返回 fields 中的同名数组，不再自行猜测 group 是否
                    # 应该作为 componentContent 的键。
                    "repeatRegions": [
                        {
                            "groupName": descriptor["groupName"],
                            "fields": descriptor["textNames"],
                            "minItems": 1,
                            "maxItems": descriptor["maxChildren"],
                            "valueShape": "array_by_occurrence",
                        }
                        for descriptor in _repeat_group_descriptors(batch_layouts_by_id[lid])
                    ],
                })
            first, last = indices[0] + 1, indices[-1] + 1
            count = len(indices)
            batch_source = _source_segment(
                source, indices[0], total, max_chars=PPT_BATCH_SOURCE_MAX_CHARS
            )
            current_slides = []
            for i in indices:
                slide = dict(items[i]) if isinstance(items[i], Mapping) else {}
                slide["sourceMaterial"] = _source_for_outline_item(
                    source,
                    slide,
                    i,
                    total,
                    max_chars=min(PPT_BATCH_SOURCE_MAX_CHARS, 6_000),
                )
                current_slides.append(slide)
            payload = {
                "title": str(outline.get("title") or "演示文稿"),
                # 全量大纲瘦身成标题行：批次上下文只需脉络，细节在 currentSlides
                "outline": [
                    {"index": i + 1, "title": str(items[i].get("title") or ""), "type": str(items[i].get("type") or "")}
                    for i in range(total) if isinstance(items[i], Mapping)
                ],
                "currentSlides": current_slides,
                "settings": dict(settings),
                "templateId": template_id,
                "selectedLayouts": unique_layouts,
                "presentonContentRules": PRESENTON_CONTENT_RULES,
                "uiOutputInstruction": (
                    f"本次返回第{first}到第{last}页，共{count}页。"
                    "不要返回完整的 layout JSON，只返回 componentContent：每页一个扁平映射，"
                    "键为组件 name，值为文本字符串（表格用 {columns:[...], rows:[...]}，"
                    "图表用 {categories:[...], series:[...]}）。"
                    "同名组件按 occurrence 顺序返回字符串数组，例如 card_title:[\"结论一\",\"结论二\"]；"
                    "repeatRegions 中的 groupName 仅用于说明区域，不要把 groupName 作为 componentContent 键；"
                    "必须按 fields 中的文本组件名称返回同名数组，数组下标对应同一个视觉实例。"
                    "数组缺少的 occurrence 由系统按大纲回填，不能把第一个卡片内容复制到所有卡片。"
                    "componentContent 的键必须覆盖 schema 中所有可编辑语义槽位；"
                    "固定重复容器若 schema 声明 fixed_children=N，所有重复文本槽位必须返回 N 项；"
                    "只有未声明 fixed_children 的动态容器才可在 min_children/max_children 范围内调整数量。"
                    "metric_value 只能填单行可信数值，metric_label 只能填短名词，"
                    "metric_description 只能填短句，card_title 只能填短标题，"
                    "label/duration_label/phase_label/stage_label 只能填单行短标签，"
                    "body/card_body/bullet_body 才能填正文或要点。"
                    "不要把普通正文塞进 metric_value、metric_label、badge、author 或 date；"
                    "漏填指标槽位时不要编造数字，系统会记录缺失并由版式选择处理。"
                    "每个槽位按 componentSchema 里的 capacity 控制字数："
                    "正文/卡片正文建议 recommendedChars 字、绝对不超过 hardMaxChars 字、最多 maxLines 行；"
                    "内容放不下时先精简/合并要点，严禁超出硬上限。"
                    "每页还需附带 speakerNote 字段：面向演讲者的 1-2 句口语化讲解提示"
                    "（讲这一页时口头说什么、强调什么），不是页面展示文字。"
                    "内容表达要贴合 settings 里的受众（audience）与语气（tone）："
                    "面向领导/客户要结论前置、突出价值；面向学生要解释清晰、循序渐进。"
                    "标题不得使用第N页、本页内容等空标题；正文优先3-5条互不重复、"
                    "平行结构的要点，每条只表达一个信息，不要把演讲稿整段复制到页面。"
                    "visualPrompt 只能描述与本页结论直接相关的主体或场景，禁止图片内文字、"
                    "数字、Logo 和 UI 截图。"
                    "若 slideInstructions 中存在当前页 privatePrompt，必须将其作为该页的内容、配图和重点约束；"
                    "privatePrompt 只影响对应页，不能覆盖资料事实和模板几何。"
                ),
                "sharedPrompt": shared_prompt,
                "slideInstructions": [
                    {
                        "index": i + 1,
                        "privatePrompt": str(items[i].get("privatePrompt") or ""),
                    }
                    for i in indices
                    if str(items[i].get("privatePrompt") or "").strip()
                ],
                "sourceMaterial": batch_source,
            }

            def _call(request_payload: Mapping[str, Any] = payload) -> str:
                if self._task_is_stopped(task_id):
                    raise PptTaskStopped()
                started_at = time.perf_counter()
                timeout_token = set_active_llm_timeout(PPT_PAGE_LLM_TIMEOUT_SECONDS)
                output_token = set_active_max_output_tokens(PPT_CONTENT_MAX_OUTPUT_TOKENS)
                content_token = set_active_llm_config(llm_config)
                effort_token = set_active_reasoning_effort(PPT_CONTENT_REASONING_EFFORT)
                try:
                    # 资料已通过 payload.sourceMaterial 全量进入 user_input；
                    # 不再放 evidence（会被截断到 1200 字符，纯浪费 token）
                    answer = run_specialist_agent(
                        "ppt_content_agent",
                        json.dumps(request_payload, ensure_ascii=False),
                        [],
                    )
                    logger.info(
                        "PPT content LLM completed task=%s slides=%s-%s model=%s elapsed_ms=%s output_chars=%s",
                        task_id or "",
                        first,
                        last,
                        str(getattr(llm_config, "model", "") or ""),
                        int((time.perf_counter() - started_at) * 1000),
                        len(str(answer or "")),
                    )
                    return answer
                finally:
                    logger.info(
                        "PPT content LLM finished task=%s slides=%s-%s model=%s elapsed_ms=%s",
                        task_id or "",
                        first,
                        last,
                        str(getattr(llm_config, "model", "") or ""),
                        int((time.perf_counter() - started_at) * 1000),
                    )
                    reset_active_reasoning_effort(effort_token)
                    reset_active_llm_config(content_token)
                    reset_active_llm_timeout(timeout_token)
                    reset_active_max_output_tokens(output_token)

            def _split_invalid_batch(error: Exception) -> List[Dict[str, Any]]:
                logger.warning(
                    "PPT content batch invalid JSON; splitting batch slides=%s error=%s",
                    [index + 1 for index in indices],
                    _safe_error_message(error),
                )
                split_slides: List[Dict[str, Any]] = []
                for index in indices:
                    if self._task_is_stopped(task_id):
                        raise PptTaskStopped()
                    split_slides.extend(_generate_batch([index]))
                return split_slides

            try:
                content_answer = _retry_llm_call(_call)
            except Exception as exc:
                # run_specialist_agent may convert an upstream non-JSON answer
                # into HTTP 502 before json.loads() is reached. Treat that
                # wrapper exactly like a locally observed invalid JSON payload,
                # but only split multi-page batches and only for JSON-specific
                # errors; ordinary 502s still use the normal batch fallback.
                if len(indices) > 1 and _is_invalid_json_llm_error(exc):
                    return _split_invalid_batch(exc)
                raise
            if self._task_is_stopped(task_id):
                raise PptTaskStopped()
            try:
                raw_content_payload = json.loads(content_answer)
                normalized = _sanitize_content_payload(
                    raw_content_payload,
                    batch_layout_ids,
                    batch_layouts_by_id,
                    len(indices),
                    current_slides,
                )
            except (TypeError, ValueError) as exc:
                # A long multi-page JSON response is much more likely to be
                # truncated or wrapped in prose than a one-page response. Do
                # not discard all pages in that batch: split it into singleton
                # requests and let each page use the normal model/fallback
                # chain. A singleton failure still reaches the existing
                # outline-only fallback without an infinite retry loop.
                if len(indices) > 1:
                    return _split_invalid_batch(exc)
                raise
            quality_flags = [
                _content_quality_flags(
                    normalized["slides"][offset],
                    items[slide_index],
                )
                for offset, slide_index in enumerate(indices)
            ]
            if PPT_CONTENT_QUALITY_REPAIR and any(quality_flags):
                # 只修复被质量规则命中的页面。原来一次质量问题会重新生成整个批次，
                # 既放大耗时，也让一个修复失败拖累同批其他正常页面；单页失败时保留
                # 首轮结果，质量门禁仍由后续 contract/QA 继续负责。
                correction_instruction = (
                    "以下 draftSlides 的 content 过于像大纲或页面目标。请重新生成页面正文："
                    "每条都要从 sourceMaterial 展开实际定义、原理、关系、公式、步骤结果、"
                    "资料中的例子或具体对比；不要写本页介绍/本页目标/梳理要点等元话语，"
                    "不要原样复制 keyPoints。componentContent 的 body/card 槽也同步改成展开后的正文。"
                )
                raw_slides = raw_content_payload.get("slides") if isinstance(raw_content_payload, Mapping) else []
                repair_offsets = [offset for offset, flags in enumerate(quality_flags) if flags]
                correction_payload = copy.deepcopy(payload)
                repair_indices = [indices[offset] for offset in repair_offsets]
                correction_payload["contentQualityCorrection"] = correction_instruction
                correction_payload["uiOutputInstruction"] = (
                    str(payload.get("uiOutputInstruction") or "")
                    + "这是质量修复批次，只返回被标记页面，必须返回每页完整 componentContent。"
                )
                correction_payload["selectedLayouts"] = [unique_layouts[offset] for offset in repair_offsets]
                correction_payload["currentSlides"] = [current_slides[offset] for offset in repair_offsets]
                correction_payload["slideInstructions"] = [
                    instruction
                    for instruction in payload.get("slideInstructions") or []
                    if int(instruction.get("index") or 0) in {index + 1 for index in repair_indices}
                ]
                correction_payload["draftSlides"] = [
                    {
                        "index": normalized["slides"][offset].get("index"),
                        "title": normalized["slides"][offset].get("title"),
                        "content": normalized["slides"][offset].get("content"),
                        "componentContent": (
                            raw_slides[offset].get("componentContent")
                            if isinstance(raw_slides, list)
                            and offset < len(raw_slides)
                            and isinstance(raw_slides[offset], Mapping)
                            else None
                        ),
                    }
                    for offset in repair_offsets
                ]
                try:
                    corrected_answer = _retry_llm_call(
                        lambda request_payload=correction_payload: _call(request_payload),
                        max_retries=0,
                    )
                    corrected = _sanitize_content_payload(
                        json.loads(corrected_answer),
                        [batch_layout_ids[offset] for offset in repair_offsets],
                        batch_layouts_by_id,
                        len(repair_offsets),
                        [current_slides[offset] for offset in repair_offsets],
                    )
                    for corrected_offset, original_offset in enumerate(repair_offsets):
                        corrected_flags = _content_quality_flags(
                            corrected["slides"][corrected_offset],
                            items[indices[original_offset]],
                        )
                        if len(corrected_flags) <= len(quality_flags[original_offset]):
                            normalized["slides"][original_offset] = corrected["slides"][corrected_offset]
                except Exception as exc:
                    logger.warning(
                        "PPT quality repair skipped for slides %s; keeping first-pass content: %s",
                        [indices[offset] + 1 for offset in repair_offsets],
                        _safe_error_message(exc),
                    )
            # 模板合同执行：校验 → 修复（内容优先，几何只还原）→ QA 元数据。
            # 单页失败不影响其他页；抛错只影响本批次（外层已有整批回退）。
            enforced: List[Dict[str, Any]] = []
            for offset, (slide, lid) in enumerate(zip(normalized["slides"], batch_layout_ids)):
                if self._task_is_stopped(task_id):
                    raise PptTaskStopped()
                try:
                    enforced.append(self._enforce_slide_contract(
                        slide,
                        template_id,
                        lid,
                        batch_layouts_by_id.get(lid) or {},
                        llm_config if PPT_ENABLE_CONTENT_REPAIR_LLM else None,
                        indices[offset] + 1,
                    ))
                except Exception as exc:
                    logger.warning(
                        "PPT slide %d contract enforcement skipped: %s",
                        indices[offset] + 1,
                        _safe_error_message(exc),
                    )
                    enforced.append(slide)
            return enforced

        completed = 0
        results: Dict[int, Dict[str, Any]] = {}
        active_batches: set[int] = set()

        def report(current: Optional[int] = None) -> None:
            if not progress_callback:
                return
            processing = sorted(
                i + 1
                for batch_idx in active_batches
                for i in batches[batch_idx]
            )
            next_slide = current or min(
                (i for i in range(total) if i not in results),
                default=min(completed + 1, total),
            )
            # 有批次在处理时展示页范围（并发批次跨多页），
            # 避免第一批完成前长时间停在"第 1 页"的观感
            if processing:
                span = f"第 {processing[0]}-{processing[-1]} 页" if len(processing) > 1 else f"第 {processing[0]} 页"
                message = f"正在生成 {span} / 共 {total} 页（已完成 {completed}）"
            elif next_slide < total:
                message = f"正在生成第 {next_slide + 1} / {total} 页"
            else:
                message = "正在生成页面内容"
            progress_callback({
                "stage": "writing",
                "progress": 10 + int(completed * 80 / max(1, total)),
                "currentSlide": next_slide + 1,
                "currentSlideTitle": str(items[next_slide].get("title") or "") if next_slide < total else "",
                "totalSlides": total,
                "completedSlides": completed,
                "remainingSlides": total - completed,
                "processingSlides": processing,
                "message": message,
            })

        max_workers = _content_worker_count(llm_config, len(batches))
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            future_to_batch: Dict[Any, int] = {}
            for batch_idx in range(min(max_workers, len(batches))):
                if self._task_is_stopped(task_id):
                    raise PptTaskStopped()
                active_batches.add(batch_idx)
                future_to_batch[executor.submit(_generate_batch, batches[batch_idx])] = batch_idx
            report()
            submitted = len(future_to_batch)
            while future_to_batch:
                for future in as_completed(list(future_to_batch)):
                    if self._task_is_stopped(task_id):
                        raise PptTaskStopped()
                    batch_idx = future_to_batch.pop(future)
                    active_batches.discard(batch_idx)
                    try:
                        batch_slides = future.result()
                    except Exception as exc:
                        logger.warning(
                            "PPT batch %d (slides %s) failed: %s",
                            batch_idx,
                            [i + 1 for i in batches[batch_idx]],
                            _safe_error_message(exc),
                        )
                        batch_slides = _fallback_slides(
                            batches[batch_idx], items, selected_layouts, layouts_by_id,
                        )
                    for offset, slide in enumerate(batch_slides):
                        idx = batches[batch_idx][offset]
                        results[idx] = slide
                        completed += 1
                    if submitted < len(batches):
                        if self._task_is_stopped(task_id):
                            raise PptTaskStopped()
                        active_batches.add(submitted)
                        future_to_batch[executor.submit(_generate_batch, batches[submitted])] = submitted
                        submitted += 1
                    report()
                    break
        return [results[i] for i in range(total)]

    def create_task(self, user_id: str, request: Mapping[str, Any], llm_config: Any) -> Dict[str, Any]:
        slides = request.get("slides")
        if not isinstance(slides, list) or len(slides) < 2:
            raise HTTPException(status_code=422, detail="slides 至少需要两页")
        task_id = f"ppt_task_{uuid.uuid4().hex}"
        now = int(time.time() * 1000)
        task = {
            "taskId": task_id,
            "userId": user_id,
            "status": "queued",
            "progress": 0,
            "stage": "queued",
            "message": "任务已进入队列",
            "createdAt": now,
            "updatedAt": now,
            "deadlineAt": self._deadline_ms(PPT_TASK_TIMEOUT_SECONDS),
            "outline": copy.deepcopy(request.get("outline") or {}),
            "slides": copy.deepcopy(slides),
            "sourceName": str(request.get("sourceName") or "演示文稿"),
            "sharedPrompt": str(request.get("sharedPrompt") or ""),
            "settings": copy.deepcopy(request.get("settings") or {}),
            "contentQuality": copy.deepcopy(request.get("contentQuality") or {}),
            "exportFormats": list(request.get("exportFormats") or ["pptx"]),
            "generationWarnings": list(request.get("generationWarnings") or []),
            "previews": [],
            "attachments": [],
            "formatErrors": {},
            "error": None,
        }
        self._register_task(task)
        self._executor.submit(self._execute_task, task_id, copy.deepcopy(dict(request)), llm_config)
        return {"taskId": task_id, "status": "queued"}

    def get_task(self, user_id: str, task_id: str) -> Dict[str, Any]:
        with self._lock:
            task = self._task_store.get(task_id) or self._tasks.get(task_id)
            if task is None:
                raise HTTPException(status_code=404, detail="PPT 任务不存在")
            if task["userId"] != user_id:
                raise HTTPException(status_code=403, detail="无权访问该 PPT 任务")
            return copy.deepcopy({key: value for key, value in task.items() if key != "userId"})

    def open_artifact(self, user_id: str, task_id: str, artifact_type: str, slide_index: int = 0):
        task = self.get_task(user_id, task_id)
        candidates = task["previews"] if artifact_type == "preview" else task["attachments"]
        item = next((entry for entry in candidates if (
            int(entry.get("slideIndex") or 0) == slide_index if artifact_type == "preview"
            else str(entry.get("type") or "") == artifact_type
        )), None)
        if item is None:
            raise HTTPException(status_code=404, detail="任务文件不存在或尚未生成")
        try:
            return generated_exporter.open_generated_export(item["storageKey"], item["internalCapability"])
        except generated_exporter.GeneratedExportAccessError as exc:
            raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc

    def cancel_task(self, user_id: str, task_id: str) -> Dict[str, Any]:
        task = self.get_task(user_id, task_id)
        if task.get("status") in _TERMINAL_TASK_STATUSES:
            return task
        self._update(
            task_id,
            status="cancelled",
            stage="cancelled",
            message="PPT 生成已取消",
            cancelRequested=True,
        )
        return self.get_task(user_id, task_id)

    def replace_slide_image(
        self,
        user_id: str,
        task_id: str,
        slide_index: int,
        image_bytes: bytes,
        extension: str = "png",
    ) -> Dict[str, Any]:
        task = self.get_task(user_id, task_id)
        if slide_index < 1 or slide_index > len(task.get("slides") or []):
            raise HTTPException(status_code=400, detail="页面编号无效")
        if not image_bytes:
            raise HTTPException(status_code=422, detail="图片不能为空")
        safe_extension = extension.lower().lstrip(".")
        if safe_extension not in {"png", "jpg", "jpeg", "webp"}:
            safe_extension = "png"
        image_path = generated_exporter._new_export_path(
            f"ppt-{task_id}-{slide_index}", safe_extension
        )
        generated_exporter._atomic_write_payload(
            image_path,
            lambda temporary_path: temporary_path.write_bytes(image_bytes),
        )
        slides = copy.deepcopy(task.get("slides") or [])
        slides[slide_index - 1]["imagePath"] = str(image_path)
        slides[slide_index - 1]["imageMode"] = "upload"
        slides[slide_index - 1]["imageStatus"] = "uploaded"
        request = {
            "sourceName": task.get("sourceName") or "演示文稿",
            "outline": task.get("outline") or {},
            "slides": slides,
            "sharedPrompt": task.get("sharedPrompt") or "",
            "generationWarnings": list(task.get("generationWarnings") or []),
            "contentQuality": copy.deepcopy(task.get("contentQuality") or {}),
            "settings": {**(task.get("settings") or {}), "imageMode": "placeholder"},
            "exportFormats": task.get("exportFormats") or ["pptx"],
        }
        with self._lock:
            task["slides"] = slides
            task["userId"] = user_id
            task["status"] = "queued"
            task["progress"] = 0
            task["stage"] = "queued"
            task["deadlineAt"] = self._deadline_ms(PPT_TASK_TIMEOUT_SECONDS)
            task["error"] = None
            self._task_store.put(task)
            self._tasks[task_id] = task
            old_timer = self._deadline_timers.pop(task_id, None)
            if old_timer is not None:
                old_timer.cancel()
        self._register_task(task)
        self._executor.submit(self._execute_task, task_id, request, None)
        return self.get_task(user_id, task_id)

    def retry_task(self, user_id: str, task_id: str, llm_config: Any) -> Dict[str, Any]:
        task = self.get_task(user_id, task_id)
        if task.get("status") not in {"failed", "cancelled", "timed_out"}:
            raise HTTPException(status_code=409, detail="只有失败、超时或已取消的 PPT 任务可以重试")
        return self.create_task(user_id, {
            "sourceName": task.get("sourceName") or "演示文稿",
            "outline": task.get("outline") or {},
            "slides": task.get("slides") or [],
            "sharedPrompt": task.get("sharedPrompt") or "",
            "settings": task.get("settings") or {},
            "contentQuality": copy.deepcopy(task.get("contentQuality") or {}),
            "exportFormats": task.get("exportFormats") or ["pptx"],
        }, llm_config)

    def _update(self, task_id: str, **values: Any) -> None:
        with self._lock:
            task = self._task_store.get(task_id) or self._tasks.get(task_id)
            if task is None:
                raise KeyError(task_id)
            if task.get("status") in _TERMINAL_TASK_STATUSES and values.get("status") != task.get("status"):
                return
            task.update(values)
            task["updatedAt"] = int(time.time() * 1000)
            self._tasks[task_id] = task
            self._task_store.put(task)
            if task.get("status") in _TERMINAL_TASK_STATUSES:
                timer = self._deadline_timers.pop(task_id, None)
                if timer is not None:
                    timer.cancel()

    def _resolve_source_text(self, request: Mapping[str, Any], user_id: str) -> str:
        source = str(request.get("sourceContent") or "").strip()
        if not source:
            file_id = str(request.get("sourceFileId") or "").strip()
            if file_id:
                metadata = self._source_files.get_owned(user_id, file_id)
                if metadata is None:
                    raise HTTPException(status_code=404, detail="PPT 资料文件不存在或已过期")
                local_path = Path(str(metadata.get("localPath") or "")).resolve()
                source_root = self._embedded_config.source_root.resolve()
                if not local_path.is_relative_to(source_root) or not local_path.is_file():
                    raise HTTPException(status_code=404, detail="PPT 资料文件不存在或已过期")
                try:
                    source = extract_source_text(local_path)
                except PptSourceParseError as exc:
                    raise HTTPException(status_code=422, detail=str(exc)) from exc
                except Exception as exc:
                    logger.exception("embedded PPT source parsing failed")
                    raise HTTPException(
                        status_code=422,
                        detail=f"PPT 资料解析失败：{_safe_error_message(exc)}",
                    ) from exc
        supplement = str(request.get("sourceSupplement") or "").strip()
        if supplement:
            source = f"{source}\n\n补充资料或额外要求：\n{supplement}" if source else supplement
        return source

    def _execute_presenton_task(self, task_id: str, request: Dict[str, Any], llm_config: Any = None) -> None:
        try:
            slides = request.get("slides")
            if not isinstance(slides, list) or len(slides) < 2:
                raise ValueError("slides 至少需要两页")
            outline = request.get("outline") if isinstance(request.get("outline"), Mapping) else {}
            settings = request.get("settings") if isinstance(request.get("settings"), Mapping) else {}
            raw_title = str(outline.get("title") or request.get("sourceName") or "演示文稿")
            title = re.sub(r"\.(?:txt|docx?|pptx?|xlsx?)$", "", raw_title, flags=re.IGNORECASE).strip()
            template_id = template_for_settings(settings, self._embedded_config.default_template)
            if not self._template_catalog.contains(template_id):
                template_id = self._embedded_config.default_template
            # 生成产品只交付可编辑 PPTX；页面 PNG 仍作为预览附件保留，
            # 页面预览和可编辑 PPTX 由同一条 Presenton 渲染链路产出。
            render_settings = {**dict(settings), "templateId": template_id, "pptxOnly": True}

            self._update(
                task_id,
                status="running",
                stage="preparing",
                progress=18,
                message="正在整理已确认的页面内容",
            )
            if self._is_cancelled(task_id):
                return
            slides, qa, _, quality_errors = self._prepare_final_slides(
                slides,
                template_id,
                llm_config,
                task_id,
            )
            request["slides"] = slides
            content_quality = request.get("contentQuality")
            if not isinstance(content_quality, Mapping) or not content_quality:
                content_quality = assess_content_quality(
                    "",
                    outline,
                    slides,
                    source_trace={
                        "version": 1,
                        "sourceName": str(request.get("sourceName") or title or "演示文稿"),
                        "sourceFileId": "",
                        "sha256": "",
                        "charCount": 0,
                        "chapterTitles": [],
                        "snapshotStored": False,
                        "snapshotPath": "",
                    },
                )
                request["contentQuality"] = content_quality
            if quality_errors:
                self._update(
                    task_id,
                    status="failed",
                    stage="quality_check",
                    progress=35,
                    message="PPT 质量校验未通过，未生成错误文件",
                    slides=slides,
                    qa=qa,
                    error={
                        "type": "PptQualityError",
                        "message": "部分页面无法在固定模板内完成排版",
                        "slides": quality_errors,
                    },
                )
                return
            slides = self._generate_task_images(task_id, slides, llm_config)
            request["slides"] = slides
            if self._is_cancelled(task_id):
                return
            self._update(
                task_id,
                stage="rendering",
                progress=48,
                message="正在使用内置模板渲染 PPT",
            )
            _, _, previews, pptx_attachment = render_presenton_html(
                slides,
                title or "演示文稿",
                render_settings,
            )
            attachments = [pptx_attachment] if pptx_attachment else []
            format_errors: Dict[str, str] = {}
            requested = {"pptx"}
            if not pptx_attachment:
                format_errors["pptx"] = (
                    "PPTX 转换器不可用，当前任务未生成可下载文件。"
                )
            available_types = {
                str(attachment.get("type") or "").lower()
                for attachment in attachments
            }
            if requested and not (requested & available_types):
                raise RuntimeError(
                    "请求的导出格式均未生成："
                    + ", ".join(sorted(requested))
                )
            # 对最终 PPTX 做一次本地只读复查。该复查不调用模型、不重试导出，
            # 即使发现成品问题也只进入 warning，不能把已成功生成的任务改成 failed。
            export_quality = _run_export_quality_check(pptx_attachment)
            warnings = [str(value) for value in (request.get("generationWarnings") or []) if str(value).strip()]
            warnings.extend(quality_warning_messages(content_quality))
            warnings.extend([
                f"第{item['slide']}页已自动压缩内容（{item['repairCount']}次修复），请检查预览"
                for item in (qa.get("repairWarnings") or [])
            ])
            warnings.extend([
                f"第{item['slide']}页存在容量提示（{', '.join(item['warnings'])}），请检查预览"
                for item in (qa.get("qualityWarnings") or [])
            ])
            warnings.extend(export_quality.get("messages") or [])
            warnings.extend(f"{key}: {value}" for key, value in format_errors.items())
            quality_status = "partial" if warnings or str(content_quality.get("status") or "") != "complete" or any(
                isinstance(slide.get("_qa"), Mapping)
                and str(slide.get("_qa", {}).get("finalStatus") or "") != "clean"
                for slide in slides
            ) else "complete"
            completion_message = (
                "PPT 已生成，但部分页面或导出结果需要复核"
                if quality_status == "partial" else "PPT 生成完成"
            )
            self._update(
                task_id,
                stage="exporting",
                progress=82,
                message="正在整理 PPTX 和页面预览",
            )
            self._update(
                task_id,
                status="completed",
                stage="completed",
                progress=100,
                message=completion_message,
                presentationId=f"embedded_{uuid.uuid4().hex}",
                editorUrl="",
                attachments=attachments,
                previews=previews,
                formatErrors=format_errors,
                warnings=warnings,
                qualityStatus=quality_status,
                requiresReview=quality_status == "partial",
                slides=slides,
                qa=qa,
                contentQuality=content_quality,
                exportQuality=export_quality,
                engine="presenton-embedded",
                templateId=template_id,
            )
        except Exception as exc:
            logger.exception("embedded PPT task failed")
            self._update(
                task_id,
                status="failed",
                stage="failed",
                message="PPT 生成失败",
                error={"type": exc.__class__.__name__, "message": _safe_error_message(exc)},
            )

    def _execute_task(self, task_id: str, request: Dict[str, Any], llm_config: Any) -> None:
        self._execute_presenton_task(task_id, request, llm_config)

    def _generate_task_images(
        self,
        task_id: str,
        slides: List[Dict[str, Any]],
        llm_config: Any,
    ) -> List[Dict[str, Any]]:
        pending = [slide for slide in slides if str(slide.get("imageStatus") or "") == "pending"]
        if not pending:
            return slides
        total_pending = len(pending)
        self._update(
            task_id,
            stage="visuals",
            progress=30,
            message=f"正在生成页面配图（0/{total_pending}）",
        )
        result = copy.deepcopy(slides)
        completed_count = [0]
        lock = threading.Lock()

        def _generate_one(slide: Dict[str, Any]) -> None:
            if self._is_cancelled(task_id):
                return
            try:
                import base64
                visual_token = set_active_llm_config(llm_config)
                try:
                    visual_result = image_agent.generate_images(
                        str(slide.get("visualPrompt") or ""),
                        [],
                        size="1664x928",
                        count=1,
                        return_type="url_and_base64",
                    )
                finally:
                    reset_active_llm_config(visual_token)
                images = visual_result.get("images") if isinstance(visual_result, Mapping) else []
                image = images[0] if isinstance(images, list) and images else {}
                encoded = str(image.get("base64") or "") if isinstance(image, Mapping) else ""
                if not encoded:
                    raise ValueError("图片模型未返回图片数据")
                image_bytes = base64.b64decode(encoded, validate=True)
                image_path = generated_exporter._new_export_path(
                    f"ppt-slide-{slide.get('index') or 0}", "png"
                )
                generated_exporter._atomic_write_payload(
                    image_path,
                    lambda temporary_path: temporary_path.write_bytes(image_bytes),
                )
                slide["imagePath"] = str(image_path)
                slide["imageStatus"] = "generated"
            except Exception as exc:
                logger.warning("PPT visual generation failed for slide %s: %s", slide.get("index"), _safe_error_message(exc))
                slide["imageStatus"] = "failed"
                slide["imageError"] = _safe_error_message(exc)
            with lock:
                completed_count[0] += 1
                self._update(
                    task_id,
                    progress=30 + int(completed_count[0] * 15 / max(1, total_pending)),
                    message=f"正在生成页面配图（{completed_count[0]}/{total_pending}）",
                )

        max_workers = min(2, max(1, total_pending))
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = [executor.submit(_generate_one, slide) for slide in result if str(slide.get("imageStatus") or "") == "pending"]
            for future in as_completed(futures):
                if self._is_cancelled(task_id):
                    break
                future.result()
        return result

    def _is_cancelled(self, task_id: str) -> bool:
        return self._task_is_stopped(task_id)

def _fallback_speaker_note(enriched: Mapping[str, Any], outline_item: Mapping[str, Any]) -> str:
    """模型未返回演讲备注时的程序化兜底（不额外调 LLM，避免拖慢生成）。"""
    title = str(enriched.get("title") or outline_item.get("title") or "").strip()
    objective = str(enriched.get("objective") or outline_item.get("objective") or "").strip()
    points = enriched.get("content") or outline_item.get("keyPoints") or []
    if isinstance(points, str):
        points = [line.strip(" -*•") for line in points.splitlines() if line.strip(" -*•")]
    points = [str(point).strip() for point in points if str(point).strip()][:2]
    parts = [f"这一页讲{title}" if title else "这一页"]
    if objective:
        parts.append(f"，重点说明{objective}")
    if points:
        parts.append("，可结合要点展开：" + "、".join(points))
    return ("".join(parts) + "。").strip()[:500]


def _outline_items(markdown: str) -> List[Dict[str, Any]]:
    standard = list(_PAGE_HEADING.finditer(markdown or ""))
    if standard:
        headings = [(match.group(1), match.end()) for match in standard]
    else:
        # 模型偶发用内容型标题（### 学校概况）而非编号标题（### 第N页），
        # 按 ### 二级标题分段兜底，顺序编号，避免整份大纲解析为 0/1 条
        headings = [
            (str(index + 1), match.end())
            for index, match in enumerate(
                re.finditer(r"^###\s+(?!大纲信息\s*$)(.+)$", markdown or "", flags=re.MULTILINE)
            )
        ]
    items: List[Dict[str, Any]] = []
    for offset, (page_no, block_start) in enumerate(headings):
        block_end = headings[offset + 1][1] if offset + 1 < len(headings) else len(markdown or "")
        block = markdown[block_start: block_end]
        fields: Dict[str, str] = {}
        active = ""
        known_fields = {
            "页标题", "页面类型", "本页目标", "核心内容", "页面节点", "展示建议", "素材建议",
            "层级", "大纲层级", "章节", "布局结构", "信息层级", "区域安排", "视觉建议", "素材处理",
        }
        for raw_line in block.splitlines():
            line = raw_line.strip()
            field = _FIELD.match(line)
            if field and field.group(1).strip() in known_fields:
                active = field.group(1).strip()
                fields[active] = field.group(2).strip()
            elif active in {"核心内容", "页面节点"} and line.startswith("-"):
                fields[active] = f"{fields.get(active, '')}\n{line.lstrip('- ').strip()}".strip()
        points = [line.strip(" -") for line in fields.get("核心内容", "").splitlines() if line.strip(" -")]
        nodes = _parse_outline_nodes(fields.get("页面节点", ""), points)
        title = fields.get("页标题") or f"第 {page_no} 页"
        explicit_level = fields.get("层级") or fields.get("大纲层级")
        item = {
            "id": f"slide_{page_no}",
            "level": _parse_outline_level(explicit_level) if explicit_level else _infer_outline_level(title),
            "title": title,
            "type": fields.get("页面类型") or "content",
            "objective": fields.get("本页目标") or "",
            "keyPoints": points,
            "nodes": nodes,
        }
        optional_fields = {
            "chapter": ("章节", "chapter"),
            "displaySuggestion": ("展示建议",),
            "assetSuggestion": ("素材建议",),
            "layoutStructure": ("布局结构",),
            "informationHierarchy": ("信息层级",),
            "regionArrangement": ("区域安排",),
            "visualSuggestion": ("视觉建议",),
            "assetHandling": ("素材处理",),
        }
        for key, names in optional_fields.items():
            value = next((fields.get(name) for name in names if fields.get(name)), "")
            if value:
                item[key] = value
        items.append(item)
    return items


def _parse_outline_level(value: Any) -> int:
    """Normalize the hierarchy value emitted by the model or an old client."""
    if isinstance(value, Mapping):
        value = value.get("level") or value.get("大纲层级") or value.get("层级")
    text = re.sub(r"\s+", "", str(value or "")).strip().lower()
    if not text:
        return 1

    # Models do not always follow the numeric contract. Accept both the UI
    # labels and the terms used by the outline prompt instead of collapsing
    # them to chapter level through int("小节") -> 1.
    aliases = {
        "章节": 1,
        "章": 1,
        "chapter": 1,
        "section": 1,
        "一级": 1,
        "level1": 1,
        "l1": 1,
        "小节": 2,
        "节": 2,
        "节点": 2,
        "node": 2,
        "subsection": 2,
        "二级": 2,
        "level2": 2,
        "l2": 2,
        "知识点": 3,
        "要点": 3,
        "知识节点": 3,
        "knowledgepoint": 3,
        "knowledge-point": 3,
        "三级": 3,
        "level3": 3,
        "l3": 3,
    }
    if text in aliases:
        return aliases[text]
    match = re.search(r"(?:level|lvl|层级|大纲层级)[^123]*([123])", text)
    if match:
        return int(match.group(1))
    match = re.search(r"(?<!\d)([123])(?!\d)", text)
    if match:
        return int(match.group(1))
    if any(token in text for token in ("知识点", "知识节点", "knowledgepoint", "要点")):
        return 3
    if any(token in text for token in ("小节", "节点", "subsection", "node")):
        return 2
    if any(token in text for token in ("章节", "chapter", "section")):
        return 1
    return 1


def _normalize_flat_outline_levels(items: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Give an unnumbered, flat model outline a usable three-level hierarchy."""
    normalized = [_ensure_outline_item_structure(item) for item in items]
    if len(normalized) < 2 or not all(item.get("level") == 1 for item in normalized):
        return normalized

    titles = [str(item.get("title") or "").strip() for item in normalized]
    # A real numbered/document outline has already been classified by its
    # headings. Do not rewrite a list that explicitly consists of chapters.
    if all(
        re.match(r"^(?:第[一二三四五六七八九十百\d]+[篇章节部分]|\d+[、.．)])", title)
        for title in titles
        if title
    ):
        return normalized

    for index, item in enumerate(normalized):
        page_type = str(item.get("type") or "").strip().lower()
        title = titles[index]
        if index == 0 or page_type in {"封面页", "目录页", "cover", "catalog"}:
            item["level"] = 1
        elif index == len(normalized) - 1 and page_type in {"总结页", "summary"}:
            item["level"] = 1
        elif re.search(r"知识点|知识节点|考点|要点|细节|关键概念|机制|方法|应用|案例|辨析", title):
            item["level"] = 3
        else:
            item["level"] = 2
    return normalized


def _infer_outline_level(title: Any) -> int:
    """Infer chapter/section/knowledge-point level from common DOCX headings."""
    value = re.sub(r"\s+", "", str(title or "")).strip()
    if not value:
        return 1
    if re.match(r"^第[一二三四五六七八九十百\d]+[篇章节部分]", value):
        return 1
    if re.match(r"^\d+\.\d+\.\d+(?:\.|、|．|\)|）|$|(?=[\u4e00-\u9fff]))", value):
        return 3
    if re.match(r"^\d+\.\d+(?:\.|、|．|\)|）|$|(?=[\u4e00-\u9fff]))", value):
        return 2
    if re.match(r"^\d+(?:、|\.|．|\)|）|(?=[\u4e00-\u9fff]))", value):
        return 2
    if re.match(r"^[一二三四五六七八九十百]+[、.．)）]", value):
        return 2
    return 1


def _outline_depth_deficits(items: List[Dict[str, Any]]) -> List[str]:
    """Return sparse topic-only pages that deserve one targeted regeneration."""
    return [
        str(items[index].get("title") or f"第{index + 1}页").strip()[:30]
        for index in _outline_depth_deficit_indices(items)
    ]


def _outline_depth_deficit_indices(items: List[Dict[str, Any]]) -> List[int]:
    """Return zero-based indices of non-cover pages that are too sparse."""
    deficits: List[int] = []
    for index, item in enumerate(items):
        page_type = str(item.get("type") or "").strip().lower()
        if page_type in {"封面页", "目录页", "cover", "catalog"}:
            continue
        points = item.get("keyPoints") or []
        nodes = item.get("nodes") or []
        if len(points) < 3 or len(nodes) < 2:
            deficits.append(index)
    return deficits


def _outline_output_token_budget(min_pages: int, max_pages: int) -> int:
    """Scale the completion cap with the requested deck size.

    A fixed 10k cap makes ordinary 5-10 page outlines look like long-form
    writing tasks. Keep enough room for the required fields, but avoid paying
    the latency cost of a large cap for small decks.
    """
    requested_pages = max(int(min_pages or 1), int(max_pages or min_pages or 1))
    estimated = 3200 + max(0, requested_pages - 5) * 150
    return max(1200, min(PPT_OUTLINE_MAX_OUTPUT_TOKENS, estimated))


def _compact_outline_source(source: str, max_chars: int) -> str:
    """Compress long source text while preserving headings and both ends.

    The old path sent only the first N characters. That was fast but silently
    dropped later chapters. This keeps the beginning, unique structure headings,
    evenly sampled middle excerpts, and the ending within the same budget.
    """
    text = str(source or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    limit = max(2_000, int(max_chars or 2_000))
    if len(text) <= limit:
        return text

    lines: List[str] = []
    line_counts: Dict[str, int] = {}
    raw_lines = [re.sub(r"[ \t]+", " ", raw_line).strip() for raw_line in text.splitlines()]
    for line in raw_lines:
        if line:
            line_counts[line] = line_counts.get(line, 0) + 1
    for line in raw_lines:
        if not line:
            if lines and lines[-1]:
                lines.append("")
            continue
        # Exact repeated lines are commonly page headers/footers from DOCX
        # extraction; only drop lines repeated at least three times so repeated
        # factual statements are not silently removed.
        if line_counts.get(line, 0) >= 3 and len(line) < 120:
            continue
        lines.append(line)
    normalized = "\n".join(lines).strip() or text
    if len(normalized) <= limit:
        return normalized

    heading_pattern = re.compile(
        r"^(?:#{1,6}\s+|第\s*[一二三四五六七八九十百\d]+\s*[章节篇部分]|"
        r"[一二三四五六七八九十百\d]+[、.．)）]\s*)",
    )
    headings = []
    for line in lines:
        if heading_pattern.match(line) and line not in headings:
            headings.append(line)
    heading_text = "\n".join(headings)
    head_budget = int(limit * 0.38)
    tail_budget = int(limit * 0.18)
    heading_budget = int(limit * 0.18)
    middle_budget = max(0, limit - head_budget - tail_budget - heading_budget - 120)

    chunks: List[str] = []
    if middle_budget:
        chunk_size = max(1, len(normalized) // 3)
        for start in (chunk_size, chunk_size * 2):
            excerpt = normalized[start:start + middle_budget // 2]
            if excerpt:
                chunks.append(excerpt)
    compacted = "\n\n".join(
        part for part in (
            "[资料开头]\n" + normalized[:head_budget],
            "[资料章节标题]\n" + heading_text[:heading_budget] if heading_text else "",
            "[资料中段摘录]\n" + "\n\n".join(chunks),
            "[资料结尾]\n" + normalized[-tail_budget:],
        )
        if part
    )
    return compacted[:limit]


def _repair_outline_sparse_pages(
    items: List[Dict[str, Any]],
    prompt_payload: Mapping[str, Any],
    topic: str,
    evidence: List[Dict[str, Any]],
) -> tuple[List[Dict[str, Any]], bool]:
    """Ask the model to repair only sparse pages, preserving the other pages."""
    deficit_indices = _outline_depth_deficit_indices(items)
    if not deficit_indices:
        return items, False
    repair_payload = copy.deepcopy(dict(prompt_payload))
    repair_payload["repair_only"] = True
    repair_payload["repair_pages"] = [
        {
            "page_number": index + 1,
            "current": copy.deepcopy(items[index]),
            "requirements": "补足至少 3 条核心内容和 2 个页面节点，不能改变本页主题。",
        }
        for index in deficit_indices[:6]
    ]
    repair_payload["correction"] = (
        "只修复 repair_pages 中列出的页面；不要重新生成其他页面。"
        "每个指定页面仍需输出页标题、页面类型、本页目标、核心内容、页面节点、展示建议、素材建议；"
        "核心内容至少 3 条，页面节点至少 2 个，节点必须包含标题和面向观众的具体说明。"
    )
    repair_output_token = set_active_max_output_tokens(
        max(1800, min(4200, 1200 + len(deficit_indices[:6]) * 500))
    )
    try:
        answer = _retry_llm_call(
            lambda: run_specialist_agent(
                "ppt_outline_agent",
                json.dumps(repair_payload, ensure_ascii=False),
                evidence,
            ),
            max_retries=0,
        )
        repaired_items = _outline_items(answer)
    except Exception as exc:
        logger.warning(
            "PPT outline sparse-page repair skipped pages=%s error=%s",
            [index + 1 for index in deficit_indices[:6]],
            _safe_error_message(exc),
        )
        return items, False
    finally:
        reset_active_max_output_tokens(repair_output_token)

    if not repaired_items:
        return items, False
    updated = copy.deepcopy(items)
    repaired_by_page: Dict[int, Dict[str, Any]] = {}
    for repaired in repaired_items:
        match = re.search(r"(\d+)", str(repaired.get("id") or ""))
        if match:
            repaired_by_page[int(match.group(1))] = repaired
    used_repaired_indices = set()
    changed = False
    for index in deficit_indices[:6]:
        repaired = repaired_by_page.get(index + 1)
        if repaired is None:
            repaired_index = next(
                (
                    candidate_index
                    for candidate_index in range(len(repaired_items))
                    if candidate_index not in used_repaired_indices
                ),
                None,
            )
            repaired = repaired_items[repaired_index] if repaired_index is not None else None
            if repaired_index is not None:
                used_repaired_indices.add(repaired_index)
        else:
            repaired_index = repaired_items.index(repaired)
            used_repaired_indices.add(repaired_index)
        if not repaired:
            continue
        normalized = _ensure_outline_item_structure(repaired)
        if len(normalized.get("keyPoints") or []) < 3 or len(normalized.get("nodes") or []) < 2:
            continue
        replacement = copy.deepcopy(updated[index])
        replacement.update(normalized)
        replacement["id"] = str(updated[index].get("id") or f"slide_{index + 1}")
        updated[index] = replacement
        changed = True
    return updated, changed


def _parse_outline_nodes(raw: str, points: List[str]) -> List[Dict[str, Any]]:
    """Read explicit page nodes and retain a useful hierarchy for slide generation."""
    nodes: List[Dict[str, Any]] = []
    for index, line in enumerate(str(raw or "").splitlines(), start=1):
        value = line.strip(" -*•\t")
        if not value:
            continue
        value = re.sub(r"^节点\s*\d*\s*[:：]\s*", "", value).strip()
        parts = re.split(r"\s*[|｜]\s*|\s*；\s*说明\s*[:：]\s*", value, maxsplit=1)
        title = parts[0].strip()
        content = (parts[1] if len(parts) > 1 else title).strip()
        if title:
            nodes.append({
                "id": f"node_{index}",
                "level": 2,
                "title": title[:80],
                "content": content[:400],
            })
    if nodes:
        return nodes[:6]
    return [
        {
            "id": f"node_{index}",
            "level": 2,
            "title": str(point).strip()[:36],
            "content": str(point).strip()[:400],
        }
        for index, point in enumerate(points[:6], start=1)
        if str(point).strip()
    ]


def _outline_nodes_from_points(points: Any) -> List[Dict[str, Any]]:
    values = points if isinstance(points, list) else [points]
    return [
        {
            "id": f"node_{index}",
            "level": 2,
            "title": str(point).strip()[:36],
            "content": str(point).strip()[:400],
        }
        for index, point in enumerate(values, start=1)
        if str(point).strip()
    ][:6]


def _ensure_outline_item_structure(item: Mapping[str, Any]) -> Dict[str, Any]:
    """Keep rich outline data through repair, fallback, and frontend round trips."""
    normalized = dict(item)
    page_title = str(normalized.get("title") or normalized.get("页标题") or "").strip()
    points = normalized.get("keyPoints") or normalized.get("content") or []
    if isinstance(points, str):
        points = [line.strip(" -*•") for line in points.splitlines() if line.strip(" -*•")]
    normalized["keyPoints"] = [str(point).strip() for point in points if str(point).strip()][:6]
    raw_nodes = normalized.get("nodes") or normalized.get("children") or []
    nodes: List[Dict[str, Any]] = []
    if isinstance(raw_nodes, list):
        for index, raw_node in enumerate(raw_nodes, start=1):
            if isinstance(raw_node, Mapping):
                node_title = str(raw_node.get("title") or raw_node.get("name") or raw_node.get("节点标题") or "").strip()
                content = str(raw_node.get("content") or raw_node.get("description") or raw_node.get("说明") or "").strip()
            else:
                node_title = str(raw_node or "").strip()
                content = node_title
            if node_title or content:
                nodes.append({
                    "id": str(raw_node.get("id") or f"node_{index}") if isinstance(raw_node, Mapping) else f"node_{index}",
                    "level": 2,
                    "title": (node_title or f"要点{index}")[:80],
                    "content": (content or node_title)[:400],
                })
    normalized["nodes"] = nodes[:6] or _outline_nodes_from_points(normalized["keyPoints"])
    if not str(normalized.get("objective") or normalized.get("本页目标") or "").strip():
        normalized["objective"] = f"明确“{page_title or '本页'}”需要传达的核心信息。"
    if not str(normalized.get("type") or normalized.get("页面类型") or "").strip():
        normalized["type"] = "内容页"
    # 这两项是后续布局的轻量提示，不应因为模型漏填而触发整份大纲失败。
    # 具体版式仍由布局智能体和模板能力决定，不在这里固化模板指令。
    normalized.setdefault("displaySuggestion", "突出本页核心信息，控制文字密度。")
    normalized.setdefault("assetSuggestion", "按内容需要使用图示、图表或简洁配图。")
    hierarchy_type = normalized.get("type") if str(normalized.get("type") or "").strip() in {
        "章节", "小节", "节点", "知识点"
    } else None
    explicit_level = (
        normalized.get("level")
        or normalized.get("大纲层级")
        or normalized.get("层级")
        or hierarchy_type
    )
    normalized["level"] = (
        _parse_outline_level(explicit_level)
        if explicit_level not in (None, "")
        else _infer_outline_level(page_title)
    )
    return normalized


def _source_outline_items(source: str, topic: str, max_pages: int) -> List[Dict[str, Any]]:
    """从用户资料恢复一个可编辑大纲。

    这是模型不可用时的最后一道数据恢复，不是模板兜底：标题和要点都必须
    来自用户原文，最多只做分段、去掉列表符号和长度裁剪，不补造事实。
    """
    text = str(source or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    topic_text = str(topic or "演示文稿").strip() or "演示文稿"
    page_limit = max(2, int(max_pages or 2))
    if not text:
        return []

    heading_pattern = re.compile(
        r"^(?:#{1,6}\s+|第\s*[一二三四五六七八九十百\d]+\s*[章节篇部分]|"
        r"[一二三四五六七八九十百\d]+[、.．)）]\s*)([^。！？!?]{2,80})$"
    )
    raw_lines = [re.sub(r"^\s*[-*•]\s*", "", line).strip() for line in text.splitlines()]
    raw_lines = [line for line in raw_lines if line]
    sections: List[Dict[str, Any]] = []
    current: Optional[Dict[str, Any]] = None
    for line in raw_lines:
        match = heading_pattern.match(line)
        if match:
            if current:
                sections.append(current)
            current = {"title": (match.group(1) or line).strip(), "points": []}
            continue
        if current is None:
            current = {"title": topic_text, "points": []}
        if len(line) >= 2:
            current["points"].append(line)
    if current:
        sections.append(current)

    # 有明确章节时优先保留章节边界。章节正文为空时仍保留原章节标题，
    # 这样用户至少能在编辑页继续补充，而不会再次得到空结果。
    if len(sections) >= 2:
        sections = sections[:page_limit]
    else:
        units = [line for line in raw_lines if line]
        if not units:
            return []
        # 没有章节结构的资料按真实段落/行均分；过长单段按句子或字符切开。
        if len(units) == 1 and len(units[0]) > 80:
            sentences = [part.strip() for part in re.split(r"(?<=[。！？!?；;])", units[0]) if part.strip()]
            units = sentences or [units[0][index:index + 80] for index in range(0, len(units[0]), 80)]
        target = min(page_limit, max(2, min(5, len(units))))
        chunk_size = max(1, (len(units) + target - 1) // target)
        sections = []
        for index in range(0, len(units), chunk_size):
            chunk = units[index:index + chunk_size]
            if not chunk:
                continue
            title = topic_text if index == 0 else chunk[0][:30]
            sections.append({"title": title, "points": chunk})
        sections = sections[:page_limit]

    items: List[Dict[str, Any]] = []
    for index, section in enumerate(sections, start=1):
        title = str(section.get("title") or f"第 {index} 页").strip()[:80]
        points: List[str] = []
        for point in section.get("points") or []:
            value = re.sub(r"^\s*(?:[-*•]|\d+[、.．)）])\s*", "", str(point)).strip()
            if value and value != title and value not in points:
                points.append(value[:240])
        if not points:
            points = [title]
        items.append(_ensure_outline_item_structure({
            "id": f"slide_{index}",
            "level": _infer_outline_level(title),
            "title": title,
            "type": "封面页" if index == 1 else ("总结页" if index == len(sections) else "内容页"),
            "objective": f"依据资料整理“{title}”的可编辑内容。",
            "keyPoints": points[:6],
            "displaySuggestion": "按资料中的信息关系组织正文，避免只保留章节标题。",
            "assetSuggestion": "优先使用资料中明确出现的结构、流程、表格或图示信息。",
        }))
    if len(items) == 1:
        # 资料正文确实只有一个可识别单元时，保留原内容并增加一个可编辑的整理页，
        # 避免模型异常时因为“少于两页”直接返回 502；不新增任何资料事实。
        items.append(_ensure_outline_item_structure({
            "id": "slide_2",
            "level": 2,
            "title": "补充与总结",
            "type": "总结页",
            "objective": f"继续围绕“{topic}”整理资料中的重点内容。",
            "keyPoints": ["根据原始资料补充需要展开的细节和结论。"],
        }))
    return items if len(items) >= 2 else []


def _normalize_outline_mode(value: Any) -> str:
    """Normalize the two user-facing outline modes without relying on display text."""
    # HTTP 的 OutlineRequest 默认值是 ai_outline；这里对旧的内部直接调用保留
    # original_outline 兼容语义，避免未带新字段的历史任务突然改变生成方式。
    normalized = str(value if value is not None else "original_outline").strip().lower()
    if normalized in {"original_outline", "outline", "大纲", "上传大纲"}:
        return "original_outline"
    return "ai_outline"


def _outline_topic_guidance(topic: str, audience: str, source_mode: str) -> Dict[str, Any]:
    """Give the outline model a semantic route without hard-coding a slide template."""
    topic_text = re.sub(r"\s+", "", str(topic or "")).lower()
    audience_text = str(audience or "通用受众").strip()
    if source_mode == "outline_grounded":
        return {
            "intent": "把用户已有结构整理为可编辑、可演示的页面计划",
            "recommended_narrative": "保留资料中的章节顺序和主要层级；只补足页面目标、具体要点和必要的页间衔接",
            "must_cover": ["原资料明确的章节和结论", "每页的核心信息", "原结构中的必要递进"],
            "avoid": ["擅自改造成另一套主题框架", "用模板字段替代资料内容", "补造资料没有的事实"],
            "audience_focus": audience_text,
        }

    routes = [
        (
            ("就业", "职业", "岗位", "求职", "招聘", "职业规划", "发展方向", "升学"),
            {
                "intent": "帮助受众理解方向选择，并形成可执行的选择或准备方案",
                "recommended_narrative": "主题问题/目标 → 方向或岗位选项 → 工作内容与差异 → 能力要求 → 学习/项目路径 → 求职或下一步行动",
                "must_cover": ["有哪些主要方向", "方向之间如何区分", "每个方向需要什么能力", "如何从当前状态走到下一步"],
                "avoid": ["把就业主题写成学科知识目录", "没有岗位对象的‘核心概念’空标题", "只讲知识不讲选择和行动"],
            },
        ),
        (
            ("方案", "规划", "提案", "汇报", "项目", "实施", "建设", "架构"),
            {
                "intent": "让受众理解现状、方案价值和落地路径，并支持后续决策",
                "recommended_narrative": "现状/问题 → 目标 → 方案框架 → 关键机制或模块 → 证据/风险 → 实施计划与下一步",
                "must_cover": ["要解决的问题", "方案如何解决", "关键组成或机制", "落地条件和下一步"],
                "avoid": ["把方案汇报写成概念百科", "只有功能罗列没有问题和价值", "虚构效果数据或实施结果"],
            },
        ),
        (
            ("产品", "功能", "运营", "市场", "商业", "品牌", "客户"),
            {
                "intent": "让受众理解对象、价值、使用方式和决策依据",
                "recommended_narrative": "受众问题 → 产品/对象定位 → 核心能力或流程 → 使用场景 → 差异与证据 → 行动建议",
                "must_cover": ["服务谁", "解决什么问题", "如何使用或运作", "为什么值得选择"],
                "avoid": ["脱离用户问题堆砌功能名词", "把产品介绍写成技术课程", "没有证据时虚构市场数据"],
            },
        ),
        (
            ("研究", "分析", "调研", "实验", "论文", "数据"),
            {
                "intent": "清晰呈现研究问题、方法、发现和结论边界",
                "recommended_narrative": "研究问题 → 背景与范围 → 方法 → 发现/证据 → 解释与局限 → 结论或后续工作",
                "must_cover": ["问题和范围", "方法或证据来源", "主要发现", "结论边界"],
                "avoid": ["没有数据时虚构数值", "把研究过程写成泛泛知识介绍", "把推测当成结论"],
            },
        ),
        (
            ("教程", "课程", "知识", "算法", "原理", "学习", "培训", "基础"),
            {
                "intent": "帮助受众建立理解、应用和复习所需的知识链路",
                "recommended_narrative": "问题/目标 → 核心概念 → 结构或机制 → 方法/示例 → 应用或辨析 → 总结",
                "must_cover": ["概念是什么", "概念之间的关系", "如何使用或判断", "需要记住的结论"],
                "avoid": ["只列章节标题不写可讲解内容", "把所有页面都写成定义", "为了填节点虚构例子"],
            },
        ),
    ]
    for keywords, guidance in routes:
        if any(keyword in topic_text for keyword in keywords):
            return {**guidance, "audience_focus": audience_text}
    return {
        "intent": "先识别主题的核心问题和受众需要带走的结论，再组织一条自然的演示叙事",
        "recommended_narrative": "主题目标/问题 → 核心对象或事实 → 关系、过程或方案 → 关键洞察/应用 → 结论与下一步；仅保留适用阶段",
        "must_cover": ["主题的核心对象", "受众真正关心的关系或结论", "能够支撑理解或行动的具体信息"],
        "avoid": ["默认套用背景-概念-知识结构-方法-总结", "用抽象标题代替主题对象", "为了适应模板虚构章节或事实"],
        "audience_focus": audience_text,
    }


def _is_topic_only_outline_request(
    request: Mapping[str, Any],
    source: str,
    topic: str,
    outline_mode: Optional[str] = None,
) -> bool:
    """判断非大纲模式下，输入是否实际上只有一个主题。"""
    source_text = re.sub(r"\s+", "", str(source or ""))
    topic_text = re.sub(r"\s+", "", str(topic or ""))
    if not source_text:
        return True
    if topic_text and source_text == topic_text:
        return True
    # 上传文件名不能再把短主题强行判成“已有资料”：非大纲模式的职责就是
    # 让模型围绕主题主动扩展；大纲模式则始终按用户提供的结构处理。
    if _normalize_outline_mode(outline_mode or request.get("outlineMode")) == "original_outline":
        return False
    if _is_generic_outline_topic(topic) and len(source_text) <= PPT_OUTLINE_TOPIC_ONLY_MAX_CHARS:
        compact_source = source_text.strip("。！？!?；;")
        if compact_source and not re.search(r"[。！？!?；;]", compact_source):
            return True
    if len(source_text) <= PPT_OUTLINE_TOPIC_ONLY_MAX_CHARS and "\n" not in str(source or ""):
        return not re.search(r"[。！？!?；;]", source_text)
    return False


def _is_generic_outline_topic(value: str) -> bool:
    normalized = re.sub(r"\s+", "", str(value or "")).strip()
    normalized = re.sub(r"\.(?:txt|docx?|pptx?|xlsx?)$", "", normalized, flags=re.IGNORECASE)
    return normalized in {re.sub(r"\s+", "", item) for item in PPT_OUTLINE_GENERIC_TOPICS}


def _resolve_outline_topic(
    request: Mapping[str, Any],
    source: str,
    topic: str,
    topic_only: bool,
) -> str:
    """短主题入口使用默认路由名时，恢复用户在资料输入框里的真实主题。"""
    if not topic_only or not _is_generic_outline_topic(topic):
        return topic
    if str(request.get("sourceFileId") or "").strip():
        return topic
    candidate = str(source or "").strip().strip("。！？!?；;")
    if not candidate or len(re.sub(r"\s+", "", candidate)) > PPT_OUTLINE_TOPIC_ONLY_MAX_CHARS:
        return topic
    return candidate[:200]


def _normalize_outline_topic_items(items: List[Dict[str, Any]], topic: str) -> List[Dict[str, Any]]:
    """清理模型偶发保留的路由默认标题，确保用户主题贯穿可编辑大纲。"""
    if not items or _is_generic_outline_topic(topic):
        return _normalize_flat_outline_levels([_ensure_outline_item_structure(item) for item in items])
    items = [_ensure_outline_item_structure(item) for item in items]
    generic_prefixes = tuple(PPT_OUTLINE_GENERIC_TOPICS)
    for index, item in enumerate(items):
        title = str(item.get("title") or "").strip()
        if not title:
            continue
        if index == 0 and _is_generic_outline_topic(title):
            item["title"] = topic
            continue
        if any(title.startswith(prefix) for prefix in generic_prefixes) and any(
            marker in title for marker in ("补充", "总结", "整理")
        ):
            suffix = re.sub(r"^(?:PPT生成|AIPPT|PPT大纲|演示文稿|复习资料|手动输入资料)", "", title).strip(" ：:、-")
            item["title"] = suffix or "补充与总结"
    return _normalize_flat_outline_levels(items)


def _source_document_title(source: str) -> str:
    """Recover a document title without treating a chapter as the title."""
    for raw_line in str(source or "").splitlines():
        value = re.sub(r"^#+\s*", "", raw_line).strip()
        if not value or value.startswith("[表格"):
            continue
        if len(value) <= 120 and not re.match(
            r"^(?:第\s*[一二三四五六七八九十百\d]+\s*[章节篇部分]|\d+[.、])",
            value,
        ):
            return value
    return ""


def _source_name_title(source_name: Any) -> str:
    """Use the uploaded filename as a last-resort title, without its extension."""
    value = str(source_name or "").strip()
    if not value:
        return ""
    title = Path(value).stem.strip()
    generic_names = {
        "source", "file", "document", "资料", "资料文件", "上传资料", "手动输入资料",
    }
    return "" if _is_generic_outline_topic(title) or title.lower() in generic_names else title[:120]


def _source_chapter_titles(source: str) -> List[str]:
    """Return top-level chapter headings preserved by the DOCX parser."""
    titles: List[str] = []
    for raw_line in str(source or "").splitlines():
        match = re.match(r"^#\s+(.+?)\s*$", raw_line)
        if not match:
            match = re.match(
                r"^(?:第\s*[一二三四五六七八九十百\d]+\s*[章节篇部分]|\d+[、.．)])\s*(.+)$",
                raw_line.strip(),
            )
        if not match:
            continue
        title = str(match.group(1) or "").strip()
        if title and title not in titles and not title.startswith("表格"):
            titles.append(title[:80])
    return titles


def _repair_material_outline_coverage(
    items: List[Dict[str, Any]],
    source: str,
    topic: str,
    max_pages: int,
) -> tuple[List[Dict[str, Any]], bool]:
    """Prevent a valid-looking outline from silently dropping whole chapters."""
    chapters = _source_chapter_titles(source)
    if not chapters or not items:
        return items, False
    haystack = "\n".join(
        " ".join(str(item.get(key) or "") for key in ("title", "objective", "keyPoints"))
        for item in items
    )
    missing = [chapter for chapter in chapters if chapter not in haystack]
    changed = False

    if not missing:
        if items and _is_generic_outline_topic(str(items[0].get("title") or "")) and topic:
            items[0]["title"] = topic[:80]
            changed = True
        return items, changed

    page_limit = max(2, int(max_pages or len(items)))
    available = max(0, page_limit - len(items))
    for chapter in missing[:available]:
        items.insert(max(1, len(items) - 1), {
            "id": f"slide_{len(items) + 1}",
            "level": 1,
            "title": chapter,
            "type": "内容页",
            "objective": f"依据原始资料展开“{chapter}”的核心知识。",
            "keyPoints": [chapter],
            "nodes": _outline_nodes_from_points([chapter]),
        })
        changed = True
    missing = missing[available:]

    if missing:
        # A fixed small page count cannot receive one page per chapter. Keep a
        # real cover, then dedicate the next page to a complete chapter map.
        target_index = 1 if len(items) > 1 else 0
        items[target_index].update({
            "title": "全书章节概览",
            "type": "目录页",
            "objective": "展示原始资料的完整章节范围，避免生成结果只覆盖开头内容。",
            "keyPoints": chapters[:12],
            "nodes": _outline_nodes_from_points(chapters[:12]),
        })
        changed = True

    if items and (not str(items[0].get("title") or "").strip() or _is_generic_outline_topic(str(items[0].get("title") or ""))):
        items[0]["title"] = topic[:80] or "演示文稿"
        items[0]["type"] = "封面页"
        changed = True
    for index, item in enumerate(items, start=1):
        item["id"] = f"slide_{index}"
    return [_ensure_outline_item_structure(item) for item in items], changed


def _topic_outline_items(
    topic: str,
    min_pages: int,
    max_pages: int,
    audience: str = "通用受众",
    tone: str = "简洁清晰",
    seed_items: Optional[List[Dict[str, Any]]] = None,
) -> List[Dict[str, Any]]:
    """主题模式的最后安全网：补充通用内容结构，不虚构具体事实。"""
    target = min(max(2, int(min_pages or 2)), max(2, int(max_pages or min_pages or 2)))
    seed_items = seed_items or []
    topic_route = _topic_route(topic)
    scaffold_by_route = {
        "career": [
            ("主题定位与目标", "封面页", [f"明确“{topic}”面向的选择或准备问题。", "说明本次演示希望受众形成的判断。", "交代从方向认识到行动计划的阅读路径。"]),
            ("就业方向与岗位地图", "内容页", ["梳理与主题相关的主要方向和岗位对象。", "说明每个方向主要解决什么工作问题。", "标出受众需要进一步核实的信息。"]),
            ("岗位差异与能力要求", "对比页", ["比较不同方向的工作内容和常见产出。", "对应说明需要发展的知识、技能和协作能力。", "帮助受众建立方向选择的判断标准。"]),
            ("学习与项目准备路径", "流程页", ["把当前基础、学习重点和实践项目串成准备路径。", "说明每一步应形成的可展示成果。", "标出从学习到求职之间需要补齐的能力证据。"]),
            ("求职行动与决策清单", "总结页", ["归纳选择方向时需要回答的关键问题。", "整理简历、项目展示和求职准备的行动顺序。", "形成受众可以立即执行的下一步清单。"]),
        ],
        "solution": [
            ("问题背景与建设目标", "封面页", [f"明确“{topic}”要解决的现状问题。", "界定目标范围和预期改变。", "交代从问题到落地方案的叙事路径。"]),
            ("方案框架与关键机制", "内容页", ["说明方案由哪些部分组成。", "解释关键机制之间如何协同。", "明确每个部分对目标的作用。"]),
            ("实施路径与资源条件", "流程页", ["拆解从准备到落地的主要阶段。", "列出每个阶段需要具备的输入和产出。", "标出需要进一步确认的资源条件。"]),
            ("风险控制与验证方式", "对比页", ["识别实施过程中可能出现的风险。", "说明对应的控制或验证方式。", "区分已知事实、待验证假设和决策事项。"]),
            ("落地计划与下一步", "总结页", ["归纳方案的核心价值和适用边界。", "整理近期、中期的推进动作。", "明确下一步需要做出的决策。"]),
        ],
        "product": [
            ("用户问题与对象定位", "封面页", [f"明确“{topic}”服务的对象和使用问题。", "说明本次演示要帮助受众理解的价值。", "交代从用户问题到使用行动的阅读路径。"]),
            ("核心能力与使用流程", "内容页", ["梳理对象的核心能力或服务环节。", "说明用户如何完成一次典型使用。", "指出流程中的关键决策点。"]),
            ("典型场景与价值", "案例页", ["描述与主题直接相关的典型使用场景。", "说明场景中的痛点如何被解决。", "区分可验证价值和仍需补充的证据。"]),
            ("差异化与选择依据", "对比页", ["整理受众选择时应比较的维度。", "说明不同选择的适用条件。", "避免在缺少资料时虚构市场数据。"]),
            ("使用建议与行动入口", "总结页", ["归纳受众需要记住的核心价值。", "整理开始使用或继续评估的步骤。", "明确下一步需要补充的信息。"]),
        ],
        "research": [
            ("研究问题与范围", "封面页", [f"明确“{topic}”要回答的研究问题。", "界定研究对象、范围和结论边界。", "交代从问题到证据再到结论的阅读路径。"]),
            ("方法与证据来源", "内容页", ["说明采用的方法和分析步骤。", "交代证据或资料的来源边界。", "区分观察结果、解释和待验证假设。"]),
            ("主要发现与关系", "数据页", ["整理与研究问题直接相关的主要发现。", "说明发现之间的关系或变化。", "不补造未提供的数值和实验结果。"]),
            ("解释、局限与应用", "对比页", ["说明发现可以支持的解释。", "列出方法和证据的局限。", "讨论结果可用于哪些后续判断或实践。"]),
            ("结论与后续工作", "总结页", ["归纳能够被证据支持的结论。", "明确不能由当前资料推出的内容。", "整理下一步研究或验证计划。"]),
        ],
        "tutorial": [
            ("学习目标与问题导入", "封面页", [f"明确“{topic}”需要解决的学习问题。", "说明受众完成学习后应能做出的判断或操作。", "交代从概念理解到应用练习的阅读路径。"]),
            ("核心概念与关系", "内容页", [f"解释“{topic}”中的关键概念。", "梳理概念之间的关系和边界。", "指出最容易混淆的理解点。"]),
            ("知识结构与关键机制", "流程页", [f"围绕“{topic}”组织由整体到局部的知识脉络。", "说明关键机制或步骤如何衔接。", "标出需要重点掌握的关系。"]),
            ("方法示例与应用", "案例页", [f"说明“{topic}”中的方法如何用于分析或实践。", "整理方法落地时的基本步骤。", "保留具体例子和细节的可编辑位置。"]),
            ("总结与练习方向", "总结页", ["归纳本次内容需要记住的结论。", "列出可用于检验理解的练习方向。", "形成后续学习或复习路径。"]),
        ],
        "general": [
            ("主题目标与范围", "封面页", [f"明确“{topic}”要讨论的对象和范围。", "说明受众需要带走的核心结论。", "交代后续内容的阅读路径。"]),
            ("关键对象与关系", "内容页", [f"识别“{topic}”中的关键对象。", "说明对象之间的关系或影响。", "指出理解主题时必须保留的边界。"]),
            ("过程、关系与判断", "流程页", [f"解释“{topic}”中的主要过程或关系。", "整理受众进行判断时需要关注的条件。", "说明前后环节如何递进。"]),
            ("应用场景与行动建议", "案例页", [f"说明“{topic}”如何进入实际场景。", "整理从理解到应用的基本步骤。", "保留需要用户补充的具体事实和案例。"]),
            ("核心结论与下一步", "总结页", [f"归纳“{topic}”的核心结论。", "列出仍需确认或继续讨论的问题。", "形成下一步行动或复习路径。"]),
        ],
    }
    scaffold = scaffold_by_route[topic_route]
    if target < len(scaffold):
        scaffold = scaffold[: max(1, target - 1)] + [scaffold[-1]]
    elif target > len(scaffold):
        extra_scaffold = [
            ("重点辨析", "对比页", [f"比较“{topic}”中容易混淆的概念或路径。", "明确不同选择的适用条件。"]),
            ("实践路径", "流程页", [f"整理学习或应用“{topic}”的基本步骤。", "标出后续可以补充的实践材料。"]),
        ]
        while len(scaffold) < target:
            scaffold.insert(-1, extra_scaffold[(len(scaffold) - 5) % len(extra_scaffold)])

    items: List[Dict[str, Any]] = []
    for index, (fallback_title, fallback_type, fallback_points) in enumerate(scaffold, start=1):
        seed = seed_items[index - 1] if index <= len(seed_items) and isinstance(seed_items[index - 1], Mapping) else {}
        seed_points = [str(point).strip() for point in (seed.get("keyPoints") or []) if str(point).strip()]
        title = topic if index == 1 else str(seed.get("title") or fallback_title).strip()
        page_type = "封面页" if index == 1 else ("总结页" if index == len(scaffold) else str(seed.get("type") or fallback_type))
        # 本地补页必须提供可直接展开的内容密度；模型只返回一两个
        # 要点时，不把稀疏 seed 原样带入新页面，避免再次调用修复模型。
        points = seed_points[:6] if len(seed_points) >= 3 else fallback_points
        items.append(_ensure_outline_item_structure({
            "id": f"slide_{index}",
            "level": 1 if index == 1 or index == len(scaffold) else (
                3 if re.search(r"知识点|知识节点|考点|要点|机制|方法|应用|案例|辨析", fallback_title) else 2
            ),
            "title": title[:80],
            "type": page_type,
            "objective": str(seed.get("objective") or f"面向{audience}，以{tone}的方式明确本页需要掌握的重点。"),
            "keyPoints": points[:6],
            "displaySuggestion": str(seed.get("displaySuggestion") or "围绕一个核心结论组织 3-5 个信息块，突出层次递进。"),
            "assetSuggestion": str(seed.get("assetSuggestion") or "根据节点关系选择结构图、流程图或简洁图标。"),
        }))
    return items


def _topic_route(topic: str) -> str:
    """选择主题兜底的叙事路线，避免所有主题落入同一套课程骨架。"""
    normalized = re.sub(r"\s+", "", str(topic or "")).lower()
    routes = (
        ("career", ("就业", "职业", "岗位", "求职", "招聘", "职业规划", "发展方向", "升学")),
        ("solution", ("方案", "规划", "提案", "汇报", "项目", "实施", "建设", "架构")),
        ("product", ("产品", "功能", "运营", "市场", "商业", "品牌", "客户")),
        ("tutorial", ("教程", "课程", "知识", "算法", "原理", "学习", "培训", "基础")),
        ("research", ("研究", "分析", "调研", "实验", "论文", "数据")),
    )
    for route, keywords in routes:
        if any(keyword in normalized for keyword in keywords):
            return route
    return "general"


def _is_generic_topic_scaffold(items: List[Dict[str, Any]], topic: str, source_mode: str) -> bool:
    """识别模型返回的旧通用骨架，只在主题语义明确时替换它。"""
    if source_mode not in {"topic_only", "non_outline"} or _topic_route(topic) == "tutorial":
        return False
    generic_titles = {"核心概念", "知识结构", "方法与应用", "总结与思考"}
    hits = sum(1 for item in items if str(item.get("title") or "").strip() in generic_titles)
    return hits >= 2


def _expand_single_page_outline(items: List[Dict[str, Any]], topic: str) -> List[Dict[str, Any]]:
    """模型页数不足时的数据驱动兜底：把单页的要点拆成逐页。

    只使用模型自己产出的要点，不虚构内容；每页承担一个要点。
    """
    if len(items) != 1:
        return items
    single = items[0]
    points = [str(point).strip() for point in (single.get("keyPoints") or []) if str(point).strip()]
    if len(points) < 2:
        return items
    title = str(single.get("title") or topic or "演示文稿").strip()
    expanded: List[Dict[str, Any]] = []
    for index, point in enumerate(points, start=1):
        expanded.append(_ensure_outline_item_structure({
            "id": f"slide_{index}",
            "level": 1 if index == 1 or index == len(points) else 2,
            "title": point[:30] if index > 1 else title,
            "type": "封面页" if index == 1 else ("总结页" if index == len(points) else "内容页"),
            "objective": str(single.get("objective") or "") if index == 1 else f"展开说明：{point[:50]}",
            "keyPoints": [point],
        }))
    return expanded


def _outline_markdown_from_items(items: List[Dict[str, Any]], topic: str) -> str:
    """从 items 重建大纲 markdown（展开兜底后保持 outlineMarkdown 与 items 一致）。"""
    lines = [
        "## PPT 大纲",
        "",
        "### 大纲信息",
        f"- 主题：{topic}",
        "- 受众：通用受众",
        f"- 建议页数：{len(items)} 页",
        f"- 整体目标：围绕 {topic} 建立清晰、连贯的演示结构。",
        "- 风格建议：简洁、清晰。",
        "",
    ]
    for index, item in enumerate(items, start=1):
        item = _ensure_outline_item_structure(item)
        points = item.get("keyPoints") or []
        nodes = item.get("nodes") or _outline_nodes_from_points(points)
        lines.extend([
            f"### 第{index}页",
            f"- 页标题：{item.get('title') or f'第 {index} 页'}",
            f"- 大纲层级：{item.get('level') or 1}",
            f"- 页面类型：{item.get('type') or '内容页'}",
            f"- 本页目标：{item.get('objective') or '明确本页需要掌握的重点。'}",
            "- 核心内容：",
            *[f"  - {point}" for point in points],
            "- 页面节点：",
            *[
                f"  - 节点{node_index}：{node.get('title') or f'要点{node_index}'}｜{node.get('content') or node.get('title') or ''}"
                for node_index, node in enumerate(nodes, start=1)
                if isinstance(node, Mapping)
            ],
            f"- 展示建议：{item.get('displaySuggestion') or '围绕核心结论组织信息块，控制文字密度并保留清晰层次。'}",
            f"- 素材建议：{item.get('assetSuggestion') or '根据页面节点关系选择结构图、流程图、图标或简洁配图。'}",
            *([f"- 布局结构：{item['layoutStructure']}"] if item.get("layoutStructure") else []),
            *([f"- 信息层级：{item['informationHierarchy']}"] if item.get("informationHierarchy") else []),
            *([f"- 区域安排：{item['regionArrangement']}"] if item.get("regionArrangement") else []),
            *([f"- 视觉建议：{item['visualSuggestion']}"] if item.get("visualSuggestion") else []),
            *([f"- 素材处理：{item['assetHandling']}"] if item.get("assetHandling") else []),
            "",
        ])
    return "\n".join(lines).strip()


def _selected_layouts_markdown(selected_layouts: List[str], items: List[Any]) -> str:
    lines = ["## PPT 布局方案", ""]
    for index, layout_id in enumerate(selected_layouts, start=1):
        item = items[index - 1] if index <= len(items) and isinstance(items[index - 1], Mapping) else {}
        lines.extend([
            f"### 第{index}页",
            f"- 页标题：{item.get('title') or f'第 {index} 页'}",
            f"- 布局结构：{layout_id}",
        ])
    return "\n".join(lines)


def _sanitize_content_payload(
    payload: Mapping[str, Any],
    selected_layouts: List[str],
    layouts_by_id: Mapping[str, Mapping[str, Any]],
    expected_count: int,
    current_slides: Optional[List[Mapping[str, Any]]] = None,
) -> Dict[str, Any]:
    slides = payload.get("slides") if isinstance(payload, Mapping) else None
    if not isinstance(slides, list) or len(slides) < expected_count:
        raise ValueError(f"ppt_content_agent 返回 {len(slides) if isinstance(slides, list) else 0} 页，期望 {expected_count} 页")
    normalized: List[Dict[str, Any]] = []
    for index in range(expected_count):
        raw = slides[index] if isinstance(slides[index], Mapping) else {}
        item = dict(raw)
        layout = layouts_by_id.get(selected_layouts[index]) or {}
        layout_model = parse_slide_layout(layout) if isinstance(layout, Mapping) and layout else None
        outline_item = current_slides[index] if current_slides and index < len(current_slides) else {}
        item["index"] = index + 1
        outline_title = str(outline_item.get("title") or "").strip()
        if outline_title:
            # The outline is the user's confirmed navigation contract. The
            # content model may expand the copy, but must not silently rename
            # a page after confirmation.
            item["title"] = outline_title
        source_evidence = str(
            outline_item.get("sourceMaterial")
            or outline_item.get("sourceExcerpt")
            or ""
        ).strip()
        if source_evidence:
            # Preserve the per-page source evidence used by the content model.
            # It is trace metadata, not visible slide copy.
            item["sourceMaterial"] = source_evidence
        layout_mismatch = item.get("layoutMismatch")
        if isinstance(layout_mismatch, Mapping) and layout_mismatch.get("fitsLayout") is False:
            logger.warning(
                "PPT slide %d AI 报告版式不兼容: %s (recommended=%s)",
                index + 1,
                str(layout_mismatch.get("reason") or "")[:200],
                str(layout_mismatch.get("recommendedSemanticType") or ""),
            )
        component_content = item.pop("componentContent", None)
        if isinstance(component_content, dict) and component_content:
            component_content = _promote_slide_content_to_repeat_slots(
                layout,
                _normalize_repeat_group_content(layout, component_content),
                item,
            )
            merged = _merge_content_into_layout(layout, component_content)
            matched = int(merged.pop("_matchedComponents", 0) or 0)
            matched_names = set(merged.pop("_matchedComponentNames", []) or [])
            matched_aliases = set(merged.pop("_matchedComponentAliases", []) or [])
            matched_occurrences = set(merged.pop("_matchedComponentOccurrences", []) or [])
            cardinality_issues = list(merged.pop("_contentCardinalityIssues", []) or [])
            if cardinality_issues:
                item["_contentCardinalityIssues"] = cardinality_issues
                logger.warning(
                    "PPT slide %d content cardinality incomplete: %s",
                    index + 1,
                    cardinality_issues,
                )
            # 结构化输出校验（第 26 节）：AI 写了不存在的 elementId 时不创建
            # 新元素，只记录并上报，交由上层 QA 呈现
            unknown = [
                key for key in component_content
                if _normalize_component_key(key) not in {
                    _normalize_component_key(name) for name in matched_names
                }
                and key not in matched_aliases
            ]
            if unknown:
                item["_unknownElementIds"] = unknown[:10]
                logger.warning(
                    "PPT slide %d unknown componentContent keys ignored: %s",
                    index + 1,
                    sorted(unknown)[:10],
                )
            if matched > 0:
                item["ui"] = merged
                _set_canonical_slide_title(merged, layout_model, str(item.get("title") or ""))
                # 槽位完备性：AI 漏填的文本槽位按大纲数据回填（不虚构），
                # 避免出现"卡片标题有内容、正文空着"的孤立元素（第 71 节）
                if layout_model is not None:
                    missing = _fill_missing_slots(
                        merged,
                        layout_model,
                        matched_names,
                        matched_occurrences,
                        item,
                        outline_item,
                    )
                    if missing:
                        item["_missingSlots"] = missing[:20]
                        logger.warning(
                            "PPT slide %d AI 漏填槽位 %s，已按大纲回填",
                            index + 1,
                            sorted(missing)[:20],
                        )
            else:
                # 键名全部未命中组件：按缺失处理，避免清空占位后输出"空模板"
                logger.warning(
                    "PPT slide %d componentContent matched no component, filling from outline",
                    index + 1,
                )
                item["ui"] = _fill_layout_with_slide_text(layout, item, outline_item)
                item["_contentFallback"] = True
        else:
            # componentContent 缺失时绝不输出模板原件：
            # 用大纲标题/要点按字号角色填充版式文本组件
            logger.warning(
                "PPT slide %d missing componentContent, filling from outline title/keyPoints",
                index + 1,
            )
            item["ui"] = _fill_layout_with_slide_text(layout, item, outline_item)
            item["_contentFallback"] = True
        normalized.append(item)
    return {"slides": normalized}


def _set_canonical_slide_title(
    ui: Dict[str, Any],
    model: Optional[SlideLayoutModel],
    title: str,
) -> None:
    """Keep the confirmed outline title in every page-title text node."""
    if model is None or not str(title or "").strip():
        return
    from app.ppt_generation.repair_engine import _find_node_by_name

    for name, elements in model.elements.items():
        for occurrence, element in enumerate(elements):
            if element.semantic_role not in {"page_title", "section_title"}:
                continue
            node = _find_node_by_name(ui, name, occurrence)
            if node is not None:
                _set_text_node_content(node, title, respect_capacity=False)


def _fill_missing_slots(
    ui: Dict[str, Any],
    model: SlideLayoutModel,
    matched_names: set,
    matched_occurrences: Optional[set] = None,
    slide_item: Optional[Mapping[str, Any]] = None,
    outline_item: Optional[Mapping[str, Any]] = None,
) -> List[str]:
    """AI 漏填的文本槽位按已生成的页面内容补齐，不直接搬运大纲要点。

    - title/subtitle 槽 → 页标题（标题允许沿用用户确认的大纲标题）
    - body/card 槽 → 内容智能体已经生成的 content 依次分配
    - label 槽 → 页标题（短标签）
    回填走 _set_text_node_content，容量截断由它保证。
    """
    from app.ppt_generation.repair_engine import _find_node_by_name

    slide_item = slide_item or {}
    outline_item = outline_item or {}

    title = str(
        slide_item.get("title")
        or outline_item.get("title")
        or ""
    ).strip()
    # outline_item.keyPoints 只是导航信息，不能在这里直接变成页面正文。
    def _points_from(value: Any) -> List[str]:
        if isinstance(value, str):
            value = [line.strip(" -*•") for line in value.splitlines() if line.strip(" -*•")]
        if not isinstance(value, (list, tuple)):
            return []
        return [str(point).strip() for point in value if str(point).strip()]

    # 正常情况下优先使用内容智能体展开后的正文；只有 AI 漏掉重复槽位、
    # 或没有返回 content 时，才把用户确认过的大纲要点作为“缺口回填”来源。
    # 这样不会把整页降级成大纲，但也不会留下空卡片/空节点。
    points = _points_from(slide_item.get("content"))
    # 大纲 keyPoints 只是导航信息，不是内容智能体已经审核过的正文。
    # 只有当前页对象自己明确返回 keyPoints 时才允许作为缺口回填来源；
    # 不能在 content 为空时跨层把 outline_item 的导航要点变成成品正文。
    fallback_points = _points_from(slide_item.get("keyPoints"))
    for point in fallback_points:
        if point not in points:
            points.append(point)
    point_iter = iter(points)
    connector_slots = {
        (relation.target_name, relation.target_index)
        for relation in model.connector_targets
    }

    def _actual_occurrence_count(root: Any, target_name: str) -> int:
        count = 0

        def walk(value: Any) -> None:
            nonlocal count
            if isinstance(value, list):
                for item in value:
                    walk(item)
                return
            if not isinstance(value, Mapping):
                return
            if str(value.get("name") or "") == target_name:
                count += 1
            for key in ("components", "elements", "children"):
                if key in value:
                    walk(value[key])
            if "child" in value:
                walk(value["child"])

        walk(root)
        return count

    def _card_copy(point: str) -> tuple[str, str]:
        value = str(point or "").strip()
        match = re.match(r"^(.{2,20})\s*[:：]\s*(.+)$", value)
        if match:
            return match.group(1).strip(), match.group(2).strip()
        compact = re.sub(r"[。！？.!?].*$", "", value).strip()
        # 与主填充路径保持一致：不要在进入真实文本槽位容量计算前
        # 用固定 16 字符截断标题，否则回填路径也会静默丢失信息。
        label = compact.rstrip("，,、：:") or value
        return label, value

    numeric_values: List[str] = []
    numeric_pattern = re.compile(
        r"(?<![A-Za-z])(?:O\s*\([^\n]{1,12}\)|\d+(?:[.,]\d+)?\s*(?:%|万|亿|人|项|个|门|所|年|次)?)"
    )
    for point in points:
        for value in numeric_pattern.findall(point):
            value = re.sub(r"\s+", "", value)
            if value and value not in numeric_values:
                numeric_values.append(value)

    missing: List[str] = []
    for name, elements in model.elements.items():
        if name in matched_names and not matched_occurrences:
            continue
        # parse_slide_layout models the template prototype once.  After a
        # dynamic group is materialized, the UI tree may contain more same-
        # named nodes than the static model.  Reuse the prototype's semantic
        # role and capacity for every actual occurrence so fallback filling
        # cannot stop at index zero.
        occurrence_count = max(len(elements), _actual_occurrence_count(ui, name))
        for index in range(occurrence_count):
            element = elements[min(index, len(elements) - 1)]
            if element.element_type not in {"text", "text-list"} or not element.mutable_text:
                continue
            slot_key = f"{name}[{index}]"
            if matched_occurrences and slot_key in matched_occurrences:
                continue
            node = _find_node_by_name(ui, name, index)
            if node is None:
                continue
            if element.semantic_role in {"page_title", "section_title", "page_subtitle"}:
                content = title
            elif element.semantic_role == "card_title":
                point = points[index] if index < len(points) else title
                content = _compact_text(_card_copy(point)[0] or title, _node_text_capacity(node))
            elif element.semantic_role == "card_value_label":
                point = points[index] if index < len(points) else title
                content = _compact_text(_card_copy(point)[0] or title, _node_text_capacity(node))
            elif element.semantic_role in {"body", "card_body", "bullet_body"}:
                # Connector-bearing card bodies cannot be left empty when a
                # repeated occurrence is omitted. Reuse the confirmed page
                # title as a bounded, non-fictional fallback rather than
                # leaving a floating connector in the rendered slide.
                content = next(point_iter, "")
                if not content and (name, index) in connector_slots:
                    content = title
            elif element.semantic_role in {"metric_value", "card_value"}:
                # 指标槽位只接受资料中真实出现过的数字/复杂度，不补造统计值。
                content = numeric_values[index] if index < len(numeric_values) else ""
            elif element.semantic_role == "metric_label":
                point = points[index] if index < len(points) else title
                content = _compact_text(_card_copy(point)[0] or title, _node_text_capacity(node))
            elif element.semantic_role == "metric_description":
                point = points[index] if index < len(points) else next(point_iter, "")
                content = _compact_text(point, _node_text_capacity(node))
            else:
                # author/date/badge/footer 等结构性文字保留模板原文，不能用普通
                # 要点覆盖；没有可靠来源时也不要编造。
                continue
            if not content:
                continue
            content = _safe_visible_text(node, content, element.semantic_role)
            if not content:
                continue
            _set_text_node_content(
                node,
                content,
                respect_capacity=False,
            )
            missing.append(f"{name}[{index}]")
    return missing


def _expand_repeated_layout_groups(
    root: Any,
    content_lengths: Mapping[str, int],
) -> set[str]:
    """Expand renderer-managed grid/flex groups to fit repeated text values.

    Some templates intentionally keep one child as a prototype and expose a
    larger ``max_children`` capacity.  Content binding must materialize the
    requested occurrences before walking the tree; otherwise only occurrence
    zero can ever receive AI content and the remaining outline items vanish.
    """
    def descendant_names(value: Any) -> set[str]:
        names: set[str] = set()
        if isinstance(value, list):
            for item in value:
                names.update(descendant_names(item))
            return names
        if not isinstance(value, Mapping):
            return names
        name = str(value.get("name") or "").strip()
        if name:
            names.add(name)
        for key in ("components", "elements", "children"):
            if key in value:
                names.update(descendant_names(value[key]))
        if "child" in value:
            names.update(descendant_names(value["child"]))
        return names

    dynamic_names: set[str] = set()

    def expand(value: Any) -> None:
        if isinstance(value, list):
            for item in value:
                expand(item)
            return
        if not isinstance(value, dict):
            return

        node_type = str(value.get("type") or "").lower()
        children = value.get("children")
        if node_type in {"grid", "flex"} and isinstance(children, list) and children:
            child_names = set().union(*(descendant_names(child) for child in children))
            if int(value.get("max_children") or len(children)) > 1:
                dynamic_names.update(child_names)
            requested = max(
                (int(content_lengths[name]) for name in child_names if int(content_lengths.get(name) or 0) > 0),
                default=0,
            )
            max_children = int(value.get("max_children") or len(children))
            fixed_children = int(value.get("fixed_children") or 0)
            same_named_children = len({
                str(child.get("name") or "")
                for child in children
                if isinstance(child, Mapping)
            }) <= 1
            target = min(requested, max_children)
            if fixed_children > 0:
                target = min(max_children, max(fixed_children, target))
            if target > len(children) and same_named_children:
                prototypes = [copy.deepcopy(child) for child in children]
                while len(children) < target:
                    children.append(copy.deepcopy(prototypes[(len(children) - 1) % len(prototypes)]))
                if node_type == "grid":
                    columns = max(1, int(value.get("columns") or 1))
                    value["rows"] = max(int(value.get("rows") or 1), (target + columns - 1) // columns)

        for key in ("components", "elements", "children"):
            if key in value:
                expand(value[key])
        if "child" in value:
            expand(value["child"])

    expand(root)
    return dynamic_names


def _repeat_group_descriptors(layout: Mapping[str, Any]) -> List[Dict[str, Any]]:
    """Return the semantic text slots owned by each repeatable layout group.

    Presenton stores many dynamic regions as one unnamed prototype child under
    a grid/flex node.  The old merge path only knew how to expand that child
    when the model happened to return a flat list for every descendant text
    name.  Keeping this small descriptor here lets the compatibility layer
    normalize group-shaped model output before the normal name-based merger
    runs, without changing template geometry or renderer behavior.
    """
    descriptors: List[Dict[str, Any]] = []

    def text_names(value: Any) -> List[str]:
        names: List[str] = []

        def walk(node: Any) -> None:
            if isinstance(node, list):
                for item in node:
                    walk(item)
                return
            if not isinstance(node, Mapping):
                return
            if str(node.get("type") or "") in {"text", "text-list"}:
                name = str(node.get("name") or "").strip()
                if name and name not in names:
                    names.append(name)
            for key in ("components", "elements", "children"):
                if key in node:
                    walk(node[key])
            if "child" in node:
                walk(node["child"])

        walk(value)
        return names

    def walk(value: Any) -> None:
        if isinstance(value, list):
            for item in value:
                walk(item)
            return
        if not isinstance(value, Mapping):
            return
        node_type = str(value.get("type") or "").lower()
        children = value.get("children")
        if node_type in {"grid", "flex"} and isinstance(children, list) and children:
            max_children = int(value.get("max_children") or len(children))
            if max_children > 1:
                for child in children:
                    if not isinstance(child, Mapping):
                        continue
                    group_name = str(child.get("name") or "").strip()
                    names = text_names(child)
                    if group_name and names:
                        descriptors.append({
                            "groupName": group_name,
                            "textNames": names,
                            "maxChildren": max_children,
                        })
        for key in ("components", "elements", "children"):
            if key in value:
                walk(value[key])
        if "child" in value:
            walk(value["child"])

    walk(layout)
    return descriptors


def _mapping_value_for_component(mapping: Mapping[str, Any], name: str) -> Any:
    """Find a child component value in a model-returned repeat item."""
    target = _normalize_component_key(name)
    for key, value in mapping.items():
        if _normalize_component_key(key) == target:
            return value

    name_key = str(name or "").lower()
    role_tokens = (
        ("title", ("title", "heading", "headline", "name")),
        ("body", ("body", "description", "detail", "content", "text", "copy")),
        ("label", ("label", "tag", "badge", "index", "number")),
    )
    desired_role = next(
        (role for role, tokens in role_tokens if any(token in name_key for token in tokens)),
        "",
    )
    if desired_role:
        for key, value in mapping.items():
            key_lower = str(key or "").lower()
            if any(token in key_lower for token in dict(role_tokens)[desired_role]):
                return value
    return None


def _normalize_repeat_group_content(
    layout: Mapping[str, Any],
    component_content: Mapping[str, Any],
) -> Dict[str, Any]:
    """Normalize repeat-group objects into the flat slot-array contract.

    New prompts ask for flat arrays, but older or weaker model responses can
    still look like ``{"agenda_items": [{"description": ...}]}``.  Treating
    that as a valid compatibility input prevents the group itself from being
    marked as matched while all of its child text slots remain empty.
    """
    normalized = dict(component_content)
    for descriptor in _repeat_group_descriptors(layout):
        group_name = str(descriptor["groupName"])
        group_key = next(
            (
                key for key in normalized
                if _normalize_component_key(key) == _normalize_component_key(group_name)
            ),
            None,
        )
        if group_key is None:
            continue
        raw_items = normalized.get(group_key)
        if not isinstance(raw_items, list) or not raw_items:
            continue

        transformed = False
        for text_name in descriptor["textNames"]:
            values: List[Any] = []
            found = False
            for item in raw_items:
                if isinstance(item, Mapping):
                    value = _mapping_value_for_component(item, text_name)
                    if value is not None:
                        found = True
                    values.append(value if value is not None else "")
                else:
                    # A scalar group item belongs to the first descriptive
                    # child; named title/body children are handled only when
                    # the model supplied an object with those fields.
                    name_key = text_name.lower()
                    if any(token in name_key for token in ("body", "description", "content", "detail")):
                        values.append(item)
                        found = True
                    else:
                        values.append("")
            if found and text_name not in normalized:
                normalized[text_name] = values
                transformed = True
            elif found and isinstance(normalized.get(text_name), list):
                # Prefer explicit flat fields if the model supplied both
                # forms; do not let a group wrapper overwrite them.
                transformed = True
        if transformed:
            normalized.pop(group_key, None)
    return normalized


def _promote_slide_content_to_repeat_slots(
    layout: Mapping[str, Any],
    component_content: Mapping[str, Any],
    slide_item: Mapping[str, Any],
) -> Dict[str, Any]:
    """Use confirmed page content to complete scalar repeat-slot responses.

    This is deliberately deterministic and conservative.  It only activates
    for a repeatable group whose child fields are scalar/missing and whose
    slide already has multiple confirmed content points.  It prevents the
    exact ``one visible item, three missing items`` failure without adding a
    model retry or inventing facts.
    """
    result = dict(component_content)
    raw_points = slide_item.get("content")
    if isinstance(raw_points, str):
        points = [line.strip(" -*•") for line in raw_points.splitlines() if line.strip(" -*•")]
    elif isinstance(raw_points, list):
        points = [str(point).strip() for point in raw_points if str(point).strip()]
    else:
        points = []
    if len(points) < 2:
        return result

    for descriptor in _repeat_group_descriptors(layout):
        text_names = list(descriptor["textNames"])
        repeatable_names = [
            name for name in text_names
            if any(token in name.lower() for token in (
                "title", "heading", "label", "body", "description", "content", "detail", "text"
            ))
        ]
        if not repeatable_names:
            continue
        existing_lengths = [
            len(value) for name, value in result.items()
            if name in repeatable_names and isinstance(value, list)
        ]
        target = min(
            int(descriptor["maxChildren"]),
            max(len(points), max(existing_lengths, default=0)),
        )
        if target <= 1:
            continue

        for name in repeatable_names:
            current = result.get(name)
            if isinstance(current, list) and len(current) >= target:
                continue
            if current is not None and isinstance(current, Mapping):
                continue
            values = list(current) if isinstance(current, list) else ([current] if current not in (None, "") else [])
            for index in range(len(values), target):
                point = points[index] if index < len(points) else points[-1]
                name_key = name.lower()
                if any(token in name_key for token in ("body", "description", "content", "detail", "text")):
                    values.append(point)
                elif any(token in name_key for token in ("title", "heading")):
                    label = re.split(r"[:：|｜。！？!?]", point, maxsplit=1)[0].strip(" ，,、")
                    values.append(label or point)
            if values:
                result[name] = values[:target]
    return result


def _merge_content_into_layout(layout: Mapping[str, Any], component_content: Mapping[str, Any]) -> Dict[str, Any]:
    """Merge LLM-generated component content into a Presenton layout template.

    Walks the layout tree and replaces text/table/chart data for any
    component whose name matches a key in component_content. The LLM
    only sends the mutable content, and the server reassembles the
    complete UI tree — no coordinate/style hallucination is possible.

    键名匹配先精确后容错（忽略大小写与分隔符）；合并后清除未命中
    组件上残留的英文模板占位文本，避免输出里出现"模板原件"痕迹。
    """
    result = copy.deepcopy(dict(layout))
    try:
        layout_model = parse_slide_layout(layout)
    except Exception:
        layout_model = None

    def _layout_component_names(root: Any) -> set[str]:
        names: set[str] = set()

        def _walk(node: Any) -> None:
            if isinstance(node, list):
                for child in node:
                    _walk(child)
                return
            if not isinstance(node, Mapping):
                return
            name = str(node.get("name") or "").strip()
            if name:
                names.add(name)
            for key in ("components", "elements", "children"):
                if key in node:
                    _walk(node[key])
            if "child" in node:
                _walk(node["child"])

        _walk(root)
        return names

    def _canonicalize_indexed_keys(
        content: Mapping[str, Any],
    ) -> tuple[Dict[str, Any], set[str]]:
        """把模型偶尔输出的 card_title_1/card_title[0] 归并为数组。

        prompt 要求同名槽位使用数组，但部分模型会把 occurrence 写进键名。
        这种写法会让严格按 name 匹配的合并器把整页判定为未命中，随后走
        兜底并留下模板示例文案。只对当前版式真实存在的基础组件名转换。
        """
        layout_names = _layout_component_names(layout)
        normalized_to_name = {
            _normalize_component_key(name): name
            for name in layout_names
            if _normalize_component_key(name)
        }
        canonical: Dict[str, Any] = {}
        indexed: Dict[str, List[tuple[int, Any, bool]]] = {}
        aliases: set[str] = set()
        indexed_pattern = re.compile(r"^(.*?)(?:\[(\d+)\]|[_\-\s]+(\d+))$")

        for raw_key, value in content.items():
            raw_name = str(raw_key)
            normalized = _normalize_component_key(raw_name)
            direct_name = normalized_to_name.get(normalized)
            if direct_name:
                canonical.setdefault(direct_name, value)
                if raw_name != direct_name:
                    aliases.add(raw_name)
                continue

            match = indexed_pattern.match(raw_name.strip())
            if not match:
                canonical[raw_name] = value
                continue
            base, bracket_index, separated_index = match.groups()
            base_name = normalized_to_name.get(_normalize_component_key(base))
            if not base_name:
                canonical[raw_name] = value
                continue
            indexed.setdefault(base_name, []).append((
                int(bracket_index or separated_index or 0),
                value,
                bool(bracket_index),
            ))
            aliases.add(raw_name)

        for name, entries in indexed.items():
            # _1/_2 通常是一基索引；出现 0 时按零基处理。
            zero_based = any(index == 0 for index, _, _ in entries)
            existing = canonical.get(name)
            if isinstance(existing, list):
                values = list(existing)
            elif existing is None:
                values = []
            else:
                values = [existing]
            for raw_index, value, _ in entries:
                index = raw_index if zero_based else raw_index - 1
                if index < 0:
                    continue
                while len(values) <= index:
                    values.append("")
                values[index] = value
            canonical[name] = values
        return canonical, aliases

    component_content = _normalize_repeat_group_content(layout, component_content)
    component_content, component_content_aliases = _canonicalize_indexed_keys(component_content)
    content_lengths = {
        str(name): len(value)
        for name, value in component_content.items()
        if isinstance(value, list)
    }
    dynamic_names = _expand_repeated_layout_groups(result, content_lengths)
    # 容错键表：归一化键 -> 原始键（精确匹配优先，不覆盖）
    fuzzy_keys: Dict[str, str] = {}
    for key in component_content:
        normalized_key = _normalize_component_key(key)
        if normalized_key and normalized_key not in fuzzy_keys:
            fuzzy_keys[normalized_key] = key
    matched_names: set = set()
    matched_occurrences: set = set()
    occurrence_cursors: Dict[str, int] = {}

    def _lookup(name: str):
        if name in component_content:
            return component_content[name]
        normalized_name = _normalize_component_key(name)
        if normalized_name and normalized_name in fuzzy_keys:
            return component_content[fuzzy_keys[normalized_name]]
        return None

    def _provided_list_length(name: str) -> Optional[int]:
        """Return the number of repeated values explicitly supplied for a name."""
        value = _lookup(name)
        if isinstance(value, list):
            return len(value)
        if isinstance(value, Mapping):
            for key in ("values", "items", "contents"):
                if isinstance(value.get(key), list):
                    return len(value[key])
        return None

    def _descendant_names(node: Any) -> set[str]:
        names: set[str] = set()

        def _walk(value: Any) -> None:
            if isinstance(value, list):
                for child in value:
                    _walk(child)
                return
            if not isinstance(value, Mapping):
                return
            name = str(value.get("name") or "")
            if name:
                names.add(name)
            for key in ("components", "elements", "children"):
                if key in value:
                    _walk(value[key])
            if "child" in value:
                _walk(value["child"])

        _walk(node)
        return names

    fixed_repeat_counts: Dict[str, int] = {}

    def _collect_fixed_repeat_counts(node: Any) -> None:
        if isinstance(node, list):
            for child in node:
                _collect_fixed_repeat_counts(child)
            return
        if not isinstance(node, Mapping):
            return
        node_type = str(node.get("type") or "").lower()
        children = node.get("children")
        if node_type in {"grid", "flex"} and isinstance(children, list) and children:
            fixed_children = int(node.get("fixed_children") or 0)
            if fixed_children > 0:
                names = set().union(*(_descendant_names(child) for child in children))
                for name in names:
                    fixed_repeat_counts[name] = max(fixed_repeat_counts.get(name, 0), fixed_children)
        for key in ("components", "elements", "children"):
            if key in node:
                _collect_fixed_repeat_counts(node[key])
        if "child" in node:
            _collect_fixed_repeat_counts(node["child"])

    _collect_fixed_repeat_counts(result)

    def _prune_repeated_groups(node: Any, fixed_children: int = 0) -> None:
        """Drop unused repeated card/item groups instead of leaving template art behind."""
        if isinstance(node, list):
            for child in list(node):
                _prune_repeated_groups(child)
            cursor = 0
            while cursor < len(node):
                candidate = node[cursor]
                if not isinstance(candidate, Mapping) or str(candidate.get("type") or "") != "group":
                    cursor += 1
                    continue
                group_name = str(candidate.get("name") or "")
                if not group_name:
                    cursor += 1
                    continue
                end = cursor + 1
                while end < len(node):
                    sibling = node[end]
                    if not isinstance(sibling, Mapping) or str(sibling.get("type") or "") != "group":
                        break
                    if str(sibling.get("name") or "") != group_name:
                        break
                    end += 1
                run_length = end - cursor
                if run_length > 1:
                    names = set().union(*(_descendant_names(group) for group in node[cursor:end]))
                    supplied_counts = [
                        count for name in names
                        for count in [_provided_list_length(name)]
                        if count is not None
                    ]
                    desired = max(supplied_counts) if supplied_counts else None
                    required = max(
                        (fixed_repeat_counts.get(name, 0) for name in names),
                        default=fixed_children,
                    )
                    if desired is not None:
                        desired = max(desired, required)
                    if desired is not None and desired < run_length:
                        del node[cursor + desired:end]
                        end = cursor + desired
                cursor = end
            return
        if not isinstance(node, Mapping):
            return
        child_fixed_children = int(node.get("fixed_children") or 0) \
            if str(node.get("type") or "").lower() in {"grid", "flex"} else 0
        for key in ("components", "elements", "children"):
            if key in node:
                _prune_repeated_groups(node[key], child_fixed_children if key == "children" else 0)
        if "child" in node:
            _prune_repeated_groups(node["child"])

    def _value_for_occurrence(content: Any, occurrence: int) -> tuple[bool, Any]:
        """Resolve repeated component values without copying the first card."""
        values = None
        if isinstance(content, list):
            values = content
        elif isinstance(content, Mapping):
            for key in ("values", "items", "contents"):
                if isinstance(content.get(key), list):
                    values = content[key]
                    break
        if values is None:
            return True, content
        if occurrence >= len(values):
            return False, None
        return True, values[occurrence]

    def _is_protected_component(node: Mapping[str, Any]) -> bool:
        if str(node.get("type") or "") not in {"text", "text-list"}:
            return False
        if node.get("locked") is True or node.get("decorative") is True:
            return True
        name_lower = str(node.get("name") or "").lower()
        if re.search(r"page|pagenum|slide[_ -]?number|logo|brand|watermark|background|decor|accent|copyright", name_lower):
            return True
        if "footer" in name_lower and not _is_template_placeholder_text(node):
            return True
        text = _node_display_text(node).strip()
        semantic_name = re.search(r"title|headline|heading|body|description|content|card|item|point|summary|intro", name_lower)
        if not semantic_name and (re.fullmatch(r"\d+(?:\.\d+)?[a-zA-Z]*", text) or (text.isupper() and len(text) <= 18)):
            return True
        return False

    def _merge(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                _merge(item)
            return
        if not isinstance(node, dict):
            return
        name = str(node.get("name") or "")
        occurrence = occurrence_cursors.get(name, 0) if name else 0
        if name:
            occurrence_cursors[name] = occurrence + 1
        content = _lookup(name) if name else None
        has_content, resolved_content = _value_for_occurrence(content, occurrence) if content is not None else (False, None)
        if name and has_content and not _is_protected_component(node):
            if isinstance(resolved_content, str) and not resolved_content.strip():
                has_content = False
            if isinstance(resolved_content, Mapping) and not any(
                str(value or "").strip()
                for value in (
                    resolved_content.get("text"),
                    resolved_content.get("value"),
                    resolved_content.get("content"),
                    resolved_content.get("items"),
                )
            ) and str(node.get("type") or "") in {"text", "text-list"}:
                has_content = False
        if name and has_content:
            matched_names.add(name)
            matched_occurrences.add(f"{name}[{occurrence}]")
            content = resolved_content
            model_element = (
                layout_model.element(name, occurrence)
                if layout_model is not None and name
                else None
            )
            preserve_formal_title = bool(
                model_element is not None
                and model_element.semantic_role in {"page_title", "section_title", "page_subtitle"}
            )
            if isinstance(content, str):
                _set_text_node_content(
                    node,
                    content,
                    respect_capacity=not preserve_formal_title,
                )
            elif isinstance(content, dict):
                node_type = str(node.get("type") or "")
                if node_type in {"text", "text-list"}:
                    _set_text_node_content(
                        node,
                        content.get("items") or content.get("text") or content.get("value") or content.get("content") or "",
                        respect_capacity=not preserve_formal_title,
                    )
                elif node_type == "table":
                    if "columns" in content:
                        node["columns"] = _compact_table_values(content["columns"], node.get("max_children"))
                    if "rows" in content:
                        node["rows"] = [
                            _compact_table_values(row, node.get("max_children"))
                            for row in _compact_table_values(content["rows"], node.get("max_children"))
                        ]
                elif node_type == "chart":
                    if "categories" in content:
                        node["categories"] = _compact_table_values(content["categories"], node.get("max_children"))
                    if "series" in content:
                        node["series"] = content["series"]
                # 其他类型（image/decoration/vector 等）不接受内容覆盖：
                # 盲目 node.update 会让 LLM 幻觉键改掉样式字段，破坏模板
        for key in ("components", "elements", "children"):
            if key in node:
                _merge(node[key])
        if "child" in node:
            _merge(node["child"])

    def _clear_unmatched_content_text(root: Dict[str, Any]) -> None:
        """Clear every remaining mutable content occurrence, not just English placeholders."""
        occurrence_cursors.clear()

        def _walk(node: Any) -> None:
            if isinstance(node, list):
                for item in node:
                    _walk(item)
                return
            if not isinstance(node, dict):
                return
            name = str(node.get("name") or "")
            occurrence = occurrence_cursors.get(name, 0) if name else 0
            if name:
                occurrence_cursors[name] = occurrence + 1
            if str(node.get("type") or "") in {"text", "text-list"} and name:
                model_element = (
                    layout_model.element(name, occurrence)
                    if layout_model is not None
                    else None
                )
                if model_element is not None and (
                    model_element.semantic_role in {"page_number", "logo"}
                    or (
                        model_element.semantic_role == "footer"
                        and not _is_template_placeholder_text(node)
                    )
                    or (
                        model_element.semantic_role == "badge"
                        and _is_template_structural_marker(_node_display_text(node))
                    )
                ):
                    # 页脚、页码、logo 和数字/全大写短标签是结构性标记。
                    # 姓名、日期等可编辑示例必须继续走清理逻辑。
                    for key in ("components", "elements", "children"):
                        if key in node:
                            _walk(node[key])
                    if "child" in node:
                        _walk(node["child"])
                    return
                name_lower = name.lower()
                is_content_slot = bool(re.search(
                    r"title|headline|heading|subtitle|body|paragraph|description|content|"
                    r"card|item|feature|profile|metric|point|summary|intro|label|role|value|text|"
                    r"badge|author|date",
                    name_lower,
                ))
                occurrence_key = f"{name}[{occurrence}]"
                if is_content_slot and occurrence_key not in matched_occurrences and not _is_protected_component(node):
                    _set_text_node_content(node, "")
            for key in ("components", "elements", "children"):
                if key in node:
                    _walk(node[key])
            if "child" in node:
                _walk(node["child"])

        for key in ("components", "elements"):
            if key in root:
                _walk(root[key])

    def _count_text_nodes_by_name(root: Any) -> Dict[str, int]:
        counts: Dict[str, int] = {}

        def _walk(value: Any) -> None:
            if isinstance(value, list):
                for item in value:
                    _walk(item)
                return
            if not isinstance(value, Mapping):
                return
            if str(value.get("type") or "") in {"text", "text-list"}:
                name = str(value.get("name") or "").strip()
                if name:
                    counts[name] = counts.get(name, 0) + 1
            for key in ("components", "elements", "children"):
                if key in value:
                    _walk(value[key])
            if "child" in value:
                _walk(value["child"])

        _walk(root)
        return counts

    for key in ("components", "elements"):
        if key in result:
            _prune_repeated_groups(result[key])
            _merge(result[key])

    rendered_text_counts = _count_text_nodes_by_name(result)
    cardinality_issues = []
    for name, value in component_content.items():
        if not isinstance(value, list) or len(value) <= 1 or str(name) not in dynamic_names:
            continue
        required = fixed_repeat_counts.get(str(name), 0)
        expected = max(len(value), required)
        rendered = rendered_text_counts.get(str(name), 0)
        if len(value) < required or rendered < expected:
            issue = {
                "elementId": str(name),
                "expected": expected,
                "rendered": rendered,
            }
            if len(value) < required:
                issue["provided"] = len(value)
                issue["required"] = required
            cardinality_issues.append(issue)

    if not matched_names:
        logger.warning(
            "componentContent keys %s matched no layout component; layout=%s",
            sorted(str(k) for k in component_content)[:10],
            sorted(matched_names),
        )
    _clear_unmatched_content_text(result)
    _clear_template_placeholder_text(result, matched_names)
    # 匹配计数/名称标记：调用方据此判断键名是否全部未命中（需回退大纲填充），
    # 以及哪些键对应不存在的组件（结构化输出校验：不创建新元素，仅报告）
    result["_matchedComponents"] = len(matched_names)
    result["_matchedComponentNames"] = sorted(matched_names)
    result["_matchedComponentAliases"] = sorted(component_content_aliases)
    result["_matchedComponentOccurrences"] = sorted(matched_occurrences)
    result["_contentCardinalityIssues"] = cardinality_issues
    return result


def _normalize_component_key(key: Any) -> str:
    """归一化组件名：小写并去掉非字母数字/中文分隔符，用于容错匹配。"""
    return re.sub(r"[^a-z0-9\u4e00-\u9fff]+", "", str(key or "").lower())


def _node_display_text(node: Mapping[str, Any]) -> str:
    text = node.get("text")
    if not isinstance(text, str) or not text:
        runs = node.get("runs")
        if isinstance(runs, list) and runs and isinstance(runs[0], Mapping):
            text = str(runs[0].get("text") or "")
    if isinstance(text, str) and text:
        return text
    items = node.get("items")
    if isinstance(items, list):
        values: List[str] = []
        for item in items:
            if isinstance(item, list):
                values.extend(
                    str(run.get("text") or "")
                    for run in item
                    if isinstance(run, Mapping) and str(run.get("text") or "")
                )
            elif isinstance(item, Mapping):
                values.append(str(item.get("text") or ""))
            elif item:
                values.append(str(item))
        return "\n".join(values)
    return str(text or "")


def _is_template_structural_marker(value: Any) -> bool:
    """判断短徽标文本是否属于模板结构，而不是示例内容。"""
    text = str(value or "").strip()
    return bool(
        re.fullmatch(r"\d+(?:\.\d+)?[A-Za-z]?", text)
        or re.fullmatch(r"[A-Z0-9][A-Z0-9 _&/+.-]{1,20}", text)
    )


_PLACEHOLDER_LEXICON = re.compile(
    r"(?i)\b(lorem|ipsum|dolor|consectetur|title|heading|headline|subtitle|"
    r"description|body|paragraph|your\s?text|sample|placeholder|text\s?here|"
    r"enter\s?text|untitled|caption|label|content|www\.yourwebsite\.com)\b|"
    r"^[-–—.]+$|^[a-z]{1,2}\d{0,2}$"
)
_TEMPLATE_SAMPLE_TEXTS = frozenset({
    "ceo", "cto", "coo", "cmo",
    "john doe", "juliana silva", "daniel gallego", "ketut susilo",
    "anna robertson", "www.yourwebsite.com", "december 2025", "jan 1, 2025",
})


def _is_template_placeholder_text(node: Mapping[str, Any]) -> bool:
    """识别模板占位文本。

    只清真正的占位词（lorem/title/description/your text 等通用词或
    长英文句子）；"01"、"KEY POINTS"、"CONCLUSION" 这类短装饰文字
    是模板设计的一部分，保留以维持模板原貌。
    """
    text = _node_display_text(node).strip()
    if not text or re.search(r"[\u4e00-\u9fff]", text):
        return False
    if text.casefold() in _TEMPLATE_SAMPLE_TEXTS:
        return True
    # 短装饰文字（数字角标、全大写设计词、单词标签）保留
    if len(text) <= 14 and not _PLACEHOLDER_LEXICON.search(text):
        return False
    # 长英文：占位词命中才清；命中也清（长英文正文多半是占位示例）
    return bool(_PLACEHOLDER_LEXICON.search(text)) or len(text.split()) >= 12


def _clear_template_placeholder_text(root: Dict[str, Any], matched_names: set) -> None:
    """合并后清空未命中组件上的英文模板占位文本。"""

    def _walk(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                _walk(item)
            return
        if not isinstance(node, dict):
            return
        if str(node.get("type") or "") in {"text", "text-list"}:
            name = str(node.get("name") or "")
            if (not name or name not in matched_names) and _is_template_placeholder_text(node):
                _set_text_node_content(node, "")
        for key in ("components", "elements", "children"):
            if key in node:
                _walk(node[key])
        if "child" in node:
            _walk(node["child"])

    for key in ("components", "elements"):
        if key in root:
            _walk(root[key])


def _node_text_capacity(node: Mapping[str, Any]) -> int:
    """估算文本节点容量，并以真实几何容量约束模板声明。

    中文字符宽约等于字号，英文约 0.55 倍；行高按 1.35 倍字号。
    模板中的 max_length 经常是英文/理想字体下的宽松值，不能覆盖几何
    容量；否则模型会收到“能放下”，浏览器却实际换行溢出的矛盾契约。
    """
    explicit = 0
    try:
        explicit = int(node.get("max_length") or 0)
    except (TypeError, ValueError):
        explicit = 0
    geometry_capacity = 0
    if str(node.get("type") or "") == "text-list":
        try:
            max_items = int(node.get("max_items") or 0)
            max_item_length = int(node.get("max_item_length") or 0)
        except (TypeError, ValueError):
            max_items = max_item_length = 0
        if max_items > 0 and max_item_length > 0:
            geometry_capacity = max_items * max_item_length
    font_size = _text_font_size(node)
    size = node.get("size")
    if font_size <= 0 or not isinstance(size, Mapping):
        return explicit
    try:
        width = float(size.get("width") or 0)
        height = float(size.get("height") or 0)
    except (TypeError, ValueError):
        return 0
    if width <= 0 or height <= 0:
        return explicit
    chars_per_line = max(1, int(width / (font_size * 0.95)))
    max_lines = max(1, int(height / (font_size * 1.35) + 0.35))
    geometry_capacity = chars_per_line * max_lines
    if explicit > 0:
        return min(explicit, geometry_capacity)
    return geometry_capacity


def _node_text_fits(node: Mapping[str, Any], text: str) -> bool:
    """Check a fallback string against the node's actual geometry."""
    size = node.get("size") if isinstance(node.get("size"), Mapping) else {}
    try:
        width = float(size.get("width") or 0)
        height = float(size.get("height") or 0)
    except (TypeError, ValueError):
        return True
    font_size = _text_font_size(node)
    if width <= 0 or height <= 0 or font_size <= 0:
        return True
    line_height = 1.35
    font = node.get("font") if isinstance(node.get("font"), Mapping) else {}
    try:
        line_height = float(font.get("line_height") or line_height)
    except (TypeError, ValueError):
        pass
    lines, max_width = measure_text(str(text or ""), font_size, width, line_height)
    return (
        lines * font_size * (line_height if line_height >= 0.4 else 1.2) <= height + 2.0
        and max_width <= width + 1.0
        and (
            _node_text_capacity(node) <= 0
            or len(re.sub(r"(?m)^[•·\-*\d.、]+\s*|\n", "", str(text or "")))
            <= _node_text_capacity(node)
        )
    )


def _safe_visible_text(node: Mapping[str, Any], value: Any, semantic_role: str = "") -> str:
    """Produce bounded visible copy for fallback/missing slots.

    The full source remains in slide content and source trace. The visible
    fallback must not put a paragraph into a one-line title slot.
    """
    if isinstance(value, (list, tuple)):
        text = "\n".join(str(item).strip(" -*•") for item in value if str(item).strip(" -*•"))
    elif isinstance(value, Mapping):
        text = str(value.get("text") or value.get("content") or value.get("value") or "")
    else:
        text = str(value or "")
    text = re.sub(r"[ \t]+", " ", text.replace("\r", "\n")).strip()
    if not text or _node_text_fits(node, text):
        return text

    # Complexity/value slots are not prose. Keep verified expressions such
    # as O(1) intact; the numeric-slot contract already rejects invented
    # values and punctuation is part of the visible notation.
    if semantic_role in {"metric_value", "card_value"}:
        return text

    short_role = semantic_role in {
        "card_title", "card_value_label", "metric_label", "label", "badge",
        "page_title", "section_title", "page_subtitle",
    }
    candidates: List[str] = []
    if short_role:
        match = re.match(r"^(.{2,20})\s*[:：|｜]\s*(.+)$", text)
        if match:
            candidates.append(match.group(1).strip())
        candidates.extend(
            part.strip(" ，,、：:；;。.!！？?")
            for part in re.split(r"(?<=[。！？!?；;])\s*", text)
            if part.strip(" ，,、：:；;。.!！？?")
        )
        for token in ("围绕", "聚焦", "理解", "掌握", "介绍", "说明", "分析", "本页"):
            if text.startswith(token):
                candidates.append(text[len(token):].lstrip(" ：:，,"))
    else:
        candidates.extend(
            part.strip()
            for part in re.split(r"(?<=[。！？!?；;])\s*|\n", text)
            if part.strip()
        )
    candidates = list(dict.fromkeys(candidate for candidate in candidates if candidate))
    if short_role:
        semantic_prefixes: List[str] = []
        for base in candidates:
            for end in range(len(base), 2, -1):
                semantic_prefixes.append(base[:end].rstrip(" ，,、：:；;。.!！？?"))
        candidates = list(dict.fromkeys([*candidates, *semantic_prefixes]))
    for candidate in candidates:
        if _node_text_fits(node, candidate):
            return candidate

    # Last resort: keep a geometry-safe prefix rather than exporting an
    # overflowing text box. This is only used for fallback/missing content;
    # the original full copy remains in the slide payload for traceability.
    for end in range(len(text), 2, -1):
        candidate = text[:end].rstrip(" ，,、：:；;。.!！？?")
        if candidate and _node_text_fits(node, candidate):
            return candidate
    return ""


def _set_text_node_content(
    node: Dict[str, Any], content: Any, *, respect_capacity: bool = True
) -> None:
    def _normalize_cjk_letter_spacing(text: str) -> None:
        """避免拉丁标题的负字距挤压中文/中英混排字形。"""
        if not re.search(r"[\u2e80-\u9fff\uff00-\uffef]", text):
            return

        def normalize(font: Any) -> None:
            if not isinstance(font, dict) or "letter_spacing" not in font:
                return
            try:
                spacing = float(font.get("letter_spacing"))
            except (TypeError, ValueError):
                return
            # Lato/Anton 模板中的 -5~-8 是英文字体的压缩字距，
            # 对 CJK fallback 字体会造成字符粘连；保留轻微紧凑感即可。
            if spacing < -1.0:
                font["letter_spacing"] = -1.0

        normalize(node.get("font"))
        runs = node.get("runs")
        if isinstance(runs, list):
            for run in runs:
                if isinstance(run, Mapping):
                    normalize(run.get("font"))

    if str(node.get("type") or "") == "text-list":
        if isinstance(content, Mapping):
            content = content.get("items") or content.get("content") or content.get("text") or ""
        if isinstance(content, (list, tuple)):
            values = [str(value).strip(" -*•") for value in content if str(value).strip(" -*•")]
        else:
            values = [line.strip(" -*•") for line in str(content or "").splitlines() if line.strip(" -*•")]
        try:
            max_items = int(node.get("max_items") or 0)
        except (TypeError, ValueError):
            max_items = 0
        try:
            max_item_length = int(node.get("max_item_length") or 0)
        except (TypeError, ValueError):
            max_item_length = 0
        if max_items > 0:
            values = values[:max_items]
        normalized = [
            _compact_text(value, max_item_length if max_item_length > 0 else None)
            for value in values
        ]
        font = dict(node.get("font") or {})
        node["items"] = [[{"text": value, "font": dict(font)}] for value in normalized]
        node["text"] = "\n".join(normalized)
        _normalize_cjk_letter_spacing(node["text"])
        return
    capacity = _node_text_capacity(node) if respect_capacity else None
    name_key = str(node.get("name") or "").lower()
    text = _compact_text(str(content or ""), capacity)
    # 只容得下一行的位置（几何高度不足两行）压平换行，避免撑高版式
    font_size = _text_font_size(node)
    size = node.get("size")
    if "\n" in text and font_size > 0 and isinstance(size, Mapping):
        try:
            height = float(size.get("height") or 0)
        except (TypeError, ValueError):
            height = 0
        if 0 < height < font_size * 2.5:
            text = re.sub(r"\s*\n+\s*", " ", text)
    node["text"] = text
    _normalize_cjk_letter_spacing(text)
    runs = node.get("runs")
    if isinstance(runs, list) and runs:
        normalized_runs: List[Dict[str, Any]] = []
        for index, run in enumerate(runs):
            next_run = dict(run) if isinstance(run, Mapping) else {}
            next_run["text"] = text if index == 0 else ""
            normalized_runs.append(next_run)
        node["runs"] = normalized_runs


def _compact_text(value: str, max_length: Any = None) -> str:
    """Normalize text without silently deleting user/model content.

    ``max_length`` is retained for callers that use it as a capacity hint,
    but capacity enforcement belongs to the layout/QA layer. Truncating here
    made the XML differ from the generated content and hid the real overflow.
    """
    text = re.sub(r"[ \t]+", " ", str(value or "").replace("\r", "\n")).strip()
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text


def _compact_table_values(values: Any, max_children: Any = None) -> List[Any]:
    if not isinstance(values, list):
        return []
    try:
        limit = int(max_children or 0)
    except (TypeError, ValueError):
        limit = 0
    rows = values[:limit] if limit > 0 else values
    return [
        _compact_text(item, 80) if isinstance(item, str) else item
        for item in rows
    ]


def _text_font_size(node: Mapping[str, Any]) -> float:
    """取文本节点字号（node.font.size 优先，回退 runs[0].font.size）。"""
    font = node.get("font")
    if isinstance(font, Mapping):
        try:
            return float(font.get("size") or 0)
        except (TypeError, ValueError):
            pass
    runs = node.get("runs")
    if isinstance(runs, list) and runs and isinstance(runs[0], Mapping):
        run_font = runs[0].get("font")
        if isinstance(run_font, Mapping):
            try:
                return float(run_font.get("size") or 0)
            except (TypeError, ValueError):
                pass
    return 0.0


def _collect_text_nodes(root: Mapping[str, Any]) -> List[Dict[str, Any]]:
    """收集普通文本和模板原生 bullet 列表节点。"""
    found: List[Dict[str, Any]] = []

    def _walk(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                _walk(item)
            return
        if not isinstance(node, dict):
            return
        if str(node.get("type") or "") in {"text", "text-list"}:
            found.append(node)
        for key in ("components", "elements", "children"):
            if key in node:
                _walk(node[key])
        if "child" in node:
            _walk(node["child"])

    for key in ("components", "elements"):
        if key in root:
            _walk(root[key])
    return found


def _fill_layout_with_slide_text(
    layout: Mapping[str, Any],
    slide_item: Mapping[str, Any],
    outline_item: Mapping[str, Any],
) -> Dict[str, Any]:
    """componentContent 缺失时的兜底：把大纲标题/要点填进版式文本组件。

    规则：标题优先放名称含 title/heading 的槽位（否则最大字号非装饰位），
    正文按面积降序分摊要点；装饰位（数字角标/全大写标签/超大号字）
    保留模板原文字以维持版式原貌；所有写入按槽位容量截断防溢出。
    """
    result = copy.deepcopy(dict(layout))
    title = str(
        slide_item.get("title")
        or outline_item.get("title")
        or f"第 {slide_item.get('index') or outline_item.get('index') or ''} 页"
    ).strip()

    # 这是内容智能体失败时的版式兜底，只使用已经生成/用户确认的 content；
    # outline.keyPoints 不能悄悄成为成品正文。
    raw_points = slide_item.get("content")
    if isinstance(raw_points, str):
        points = [line.strip(" -*•") for line in raw_points.splitlines() if line.strip(" -*•")]
    elif isinstance(raw_points, list):
        points = raw_points
    else:
        points = []
    points = [str(point).strip() for point in points if str(point).strip()][:6]
    body_text = "\n".join(f"• {point}" for point in points)

    # 版式 JSON 本身没有把“标题/正文/装饰”角色直接写在节点上，角色由
    # template_model 根据槽位名称推导。兜底填充也必须使用同一套角色判断，
    # 否则会把 INTRODUCTION、SALES REPORT 这类全大写的可编辑模板示例
    # 误当成装饰，反而把用户标题写进副标题或卡片位。
    try:
        layout_model = parse_slide_layout(layout)
    except Exception:
        layout_model = None
    fallback_content_lengths = {}
    if layout_model is not None and points:
        repeatable_roles = {
            "body",
            "card_title",
            "card_body",
            "bullet_body",
            "metric_label",
            "metric_description",
        }
        fallback_content_lengths = {
            name: len(points)
            for name, elements in layout_model.elements.items()
            if any(element.semantic_role in repeatable_roles for element in elements)
        }
    _expand_repeated_layout_groups(result, fallback_content_lengths)
    text_nodes = _collect_text_nodes(result)
    name_occurrences: Dict[str, int] = {}
    node_roles: Dict[int, str] = {}
    node_semantic_roles: Dict[int, str] = {}

    def _write_content(
        node: Dict[str, Any], content: Any, *, respect_capacity: bool = False
    ) -> None:
        # 兜底页没有后续可靠的内容智能体重写机会；先按节点真实几何做
        # 语义压缩，避免把完整正文写进一行 card_title。完整内容仍保留在
        # slide.content/sourceTrace 中，页面只承载适合该槽位的可见副本。
        semantic_role = node_semantic_roles.get(id(node), "")
        safe_content = _safe_visible_text(node, content, semantic_role)
        _set_text_node_content(node, safe_content, respect_capacity=respect_capacity)

    def _is_decorative(node: Mapping[str, Any]) -> bool:
        """装饰位：纯数字角标/全大写短标签/容量过小，不参与标题正文分配。"""
        name = str(node.get("name") or "")
        name_key = name.lower()
        occurrence = name_occurrences.get(name, 0)
        name_occurrences[name] = occurrence + 1
        model_element = (
            layout_model.element(name, occurrence)
            if layout_model is not None and name
            else None
        )
        if model_element is not None:
            node_roles[id(node)] = model_element.role
            node_semantic_roles[id(node)] = model_element.semantic_role
            if model_element.locked or not model_element.mutable_text:
                return True
            if model_element.semantic_role == "footer":
                # Keep real footer metadata (Page, section markers), but treat
                # obvious template URLs/sample copy as mutable placeholders so
                # they cannot leak into exported PPTX.
                return not _is_template_placeholder_text(node)
            if model_element.semantic_role == "badge":
                # 数字/全大写短标签通常是卡片编号或结构性标记；姓名、
                # 日期等模板示例不是装饰，必须清掉，不能泄漏到成品。
                return _is_template_structural_marker(_node_display_text(node))
            if model_element.semantic_role in {"author", "date"}:
                return False
            # 可编辑标题/正文/卡片/标签即使原文是全大写，也必须参与填充。
            # 仅保留明确的结构性标记（logo、日期标签、角标等）。
            if model_element.role in {"title", "subtitle", "body", "card", "label"}:
                if re.search(r"(?:^|_)(?:logo|brand|icon|mark|index|number)(?:_|$)", name_key):
                    return True
                if re.search(r"(?:^|_)(?:date|presenter)_label$", name_key):
                    return True
                return False
        text = _node_display_text(node).strip()
        if text and (text.isdigit() or (text.isupper() and len(text) <= 12)):
            return True
        if _text_font_size(node) >= 100:
            return True
        return 0 < _node_text_capacity(node) < 8

    def _area(node: Mapping[str, Any]) -> float:
        size = node.get("size")
        if not isinstance(size, Mapping):
            return 0.0
        try:
            return float(size.get("width") or 0) * float(size.get("height") or 0)
        except (TypeError, ValueError):
            return 0.0

    fillable = [n for n in text_nodes if not _is_decorative(n)]
    filled_nodes: set[int] = set()
    # 标题位优先使用语义模型；只有旧模板无法解析语义时才回退到名称/字号。
    title_node = next(
        (n for n in fillable if node_semantic_roles.get(id(n)) in {"page_title", "section_title"}),
        None,
    )
    if title_node is None:
        title_node = next(
            (n for n in fillable if re.search(r"(?i)title|heading|headline", str(n.get("name") or ""))),
            None,
        )
    if title_node is None:
        candidates = sorted(fillable, key=_text_font_size, reverse=True)
        title_node = candidates[0] if candidates else None
    if title_node is not None:
        _write_content(title_node, title)
        filled_nodes.add(id(title_node))

    def _is_card_heading(node: Mapping[str, Any]) -> bool:
        return node_semantic_roles.get(id(node)) in {"card_title", "card_value_label"}

    def _is_card_body(node: Mapping[str, Any]) -> bool:
        return node_semantic_roles.get(id(node)) == "card_body"

    def _card_copy(point: str) -> tuple[str, str]:
        value = str(point or "").strip()
        match = re.match(r"^(.{2,20})\s*[:：]\s*(.+)$", value)
        if match:
            return match.group(1).strip(), match.group(2).strip()
        compact = re.sub(r"[。！？.!?].*$", "", value).strip()
        # 卡片标题的容量由真实文本槽位决定。固定截取 16 个字符会在
        # 进入 Layout/RepairEngine 之前丢失信息，而且不会留下可诊断的
        # 省略标记；卡片正文槽位仍由调用方按自己的容量单独处理。
        label = compact.rstrip("，,、：:") or value
        return label, value

    numeric_values: List[str] = []
    for point in points:
        numeric_values.extend(
            re.findall(r"O\s*\([^)]{1,24}\)|[-+]?\d+(?:[.,]\d+)?\s*%?", point, re.I)
        )
    metric_value_nodes = [
        n for n in fillable
        if n is not title_node
        and node_semantic_roles.get(id(n)) in {"metric_value", "card_value"}
    ]
    metric_label_nodes = [
        n for n in fillable
        if n is not title_node
        and node_semantic_roles.get(id(n)) in {"metric_label", "card_value_label"}
    ]
    metric_description_nodes = [
        n for n in fillable
        if n is not title_node
        and node_semantic_roles.get(id(n)) == "metric_description"
    ]
    for node, value in zip(metric_value_nodes, numeric_values):
        _write_content(node, value)
        filled_nodes.add(id(node))
    for node, point in zip(metric_label_nodes, points):
        label, _ = _card_copy(point)
        _write_content(node, label, respect_capacity=True)
        filled_nodes.add(id(node))
    for node, point in zip(metric_description_nodes, points):
        _write_content(node, point)
        filled_nodes.add(id(node))

    def _capacity(node: Mapping[str, Any]) -> int:
        return _node_text_capacity(node)

    def _can_receive_body_content(node: Mapping[str, Any]) -> bool:
        semantic_role = node_semantic_roles.get(id(node), "")
        if semantic_role and semantic_role not in {"body", "card_body", "bullet_body"}:
            return False
        role = node_roles.get(id(node), "")
        if not semantic_role and role and role not in {"body", "card"}:
            return False
        # 作者/日期/页脚等元数据即使被模型归为 body，也不能吞正文要点。
        name = str(node.get("name") or "").lower()
        if re.search(r"(?:date|presenter|author|metadata|caption|badge|label|footer)", name):
            return False
        return True

    # 卡片版式先按“标题 + 解释”成对填充，避免旧兜底把第 1 条要点
    # 填进卡片标题、第 2 条要点填进卡片正文，导致信息关系错位。
    card_heading_nodes = [n for n in fillable if n is not title_node and _is_card_heading(n)]
    card_body_nodes = [n for n in fillable if n is not title_node and _is_card_body(n)]
    # Some process/agenda layouts intentionally expose only short node
    # headings and no body slot. Do not clear those headings merely because
    # the usual title+body pair is absent; a node label is still useful,
    # audience-facing content. The inverse (body-only cards) is handled too.
    paired_points = min(len(points), len(card_heading_nodes), len(card_body_nodes))
    if card_heading_nodes and not card_body_nodes:
        for point, heading_node in zip(points, card_heading_nodes):
            label, _ = _card_copy(point)
            _write_content(heading_node, label, respect_capacity=True)
            filled_nodes.add(id(heading_node))
        paired_points = min(len(points), len(card_heading_nodes))
    elif card_body_nodes and not card_heading_nodes:
        for point, body_node in zip(points, card_body_nodes):
            _write_content(body_node, point)
            filled_nodes.add(id(body_node))
        paired_points = min(len(points), len(card_body_nodes))
    for point, heading_node, body_node in zip(
        points[:paired_points], card_heading_nodes[:paired_points], card_body_nodes[:paired_points]
    ):
        label, body = _card_copy(point)
        _write_content(heading_node, label, respect_capacity=True)
        _write_content(body_node, body)
        filled_nodes.update({id(heading_node), id(body_node)})

    # 正文位：面积降序分配要点（正文框才是最大的可写区域，字号大不代表是正文）
    body_nodes = sorted(
        [
            n for n in fillable
            if n is not title_node
            and id(n) not in filled_nodes
            and _can_receive_body_content(n)
            and (_capacity(n) == 0 or _capacity(n) >= 8)
        ],
        key=_area,
        reverse=True,
    )
    remaining_points = points[paired_points:] if paired_points else points
    remaining_body = "\n".join(f"• {point}" for point in remaining_points)
    if len(body_nodes) == 1:
        content = remaining_body
        if content:
            _write_content(body_nodes[0], content)
            filled_nodes.add(id(body_nodes[0]))
    elif body_nodes:
        # 多个正文位：要点逐个分摊，多余要点并入首个正文位
        for node, point in zip(body_nodes, remaining_points):
            _write_content(node, f"• {point}")
            filled_nodes.add(id(node))
        if len(remaining_points) > len(body_nodes):
            extras = remaining_points[len(body_nodes) - 1:]
            merged = _node_display_text(body_nodes[0]) + "\n" + "\n".join(f"• {p}" for p in extras)
            _write_content(body_nodes[0], merged)
            filled_nodes.add(id(body_nodes[0]))
        elif not remaining_points and body_text:
            _write_content(body_nodes[0], body_text)
            filled_nodes.add(id(body_nodes[0]))
        # 未分摊到要点的正文位清掉模板占位（装饰位保留原文字）
        for node in body_nodes[len(remaining_points):] if remaining_points else body_nodes[1:]:
            if _is_template_placeholder_text(node):
                _write_content(node, "")
    # 降级页不能把模板自带的英文示例或假数据继续带到成品中。
    # fillable 已经排除了数字角标、全大写标签、超大装饰字和容量过小的
    # 节点，因此这里必须清理所有未被本页内容使用的可写槽位，而不是只
    # 清理能被词典识别的 placeholder。真实模板文案（如 Customer
    # Retention、Retail Sales）看起来完全像正常业务文案，无法靠词典可靠
    # 区分；保留它们会让兜底页出现“半套旧模板 + 半套新内容”。
    for node in fillable:
        if id(node) not in filled_nodes:
            _write_content(node, "")
    return result


_EMPTY_SLIDE_UI: Dict[str, Any] = {
    "components": [{
        "type": "text",
        "name": "content",
        "text": "（AI 内容生成失败，请重试）",
        "x": 100, "y": 300, "width": 1080, "height": 120,
        "fontSize": 24, "color": "#999999",
        "fontFamily": "sans-serif",
    }],
    "width": 1280, "height": 720,
}


def _fallback_slides(
    indices: List[int],
    items: List[Mapping[str, Any]],
    selected_layouts: List[str],
    layouts_by_id: Mapping[str, Mapping[str, Any]],
) -> List[Dict[str, Any]]:
    """LLM 整批失败时的降级：用真实版式 + 大纲标题/要点填充，而不是占位页。"""
    fallback: List[Dict[str, Any]] = []
    for i in indices:
        item = items[i] if i < len(items) else {}
        points = item.get("keyPoints") or item.get("content") or []
        if isinstance(points, str):
            points = [line.strip(" -") for line in points.splitlines() if line.strip(" -*")]
        layout = layouts_by_id.get(selected_layouts[i]) if i < len(selected_layouts) else None
        slide_item = {
            "index": i + 1,
            "type": item.get("type") or "content",
            "title": str(item.get("title") or f"第 {i + 1} 页"),
            "content": list(points) if isinstance(points, list) else [],
            "objective": str(item.get("objective") or ""),
            "visualPrompt": "",
            "speakerNote": "",
        }
        slide = dict(slide_item)
        if isinstance(layout, Mapping) and layout:
            slide["ui"] = _fill_layout_with_slide_text(layout, slide_item, item)
        else:
            slide["ui"] = copy.deepcopy(_EMPTY_SLIDE_UI)
        slide["_generationError"] = "AI 内容生成失败，使用大纲原始内容"
        fallback.append(slide)
    return fallback


def _item_has_explicit_numeric_data(item: Mapping[str, Any]) -> bool:
    values: List[str] = [
        str(item.get("title") or ""),
        str(item.get("objective") or ""),
        str(item.get("sourceExcerpt") or ""),
    ]
    for key in ("keyPoints", "content", "data", "statistics", "metrics"):
        value = item.get(key)
        if isinstance(value, list):
            values.extend(str(entry) for entry in value)
        elif value:
            values.append(str(value))
    return bool(re.search(r"(?<![A-Za-z])\d+(?:[.,]\d+)?\s*(?:%|万|亿|人|项|个|门|所|年|次|万元|亿元|m|k|b)?\b", " ".join(values), re.I))


def _numeric_token_count(item: Mapping[str, Any]) -> int:
    """Count usable numeric facts instead of treating one stray number as a dataset."""
    values: List[str] = [
        str(item.get("title") or ""),
        str(item.get("objective") or ""),
        str(item.get("sourceExcerpt") or ""),
    ]
    for key in ("keyPoints", "content", "data", "statistics", "metrics"):
        value = item.get(key)
        if isinstance(value, list):
            values.extend(str(entry) for entry in value)
        elif value:
            values.append(str(value))
    return len(re.findall(r"O\s*\([^)]{1,24}\)|[-+]?\d+(?:[.,]\d+)?\s*%?", " ".join(values), re.I))


def _layout_requires_numeric_data(layout: Mapping[str, Any]) -> bool:
    semantic_slots = layout.get("semanticSlots")
    if isinstance(semantic_slots, list) and any(
        isinstance(slot, Mapping)
        and str(slot.get("semanticRole") or "") in {"metric_value", "card_value"}
        for slot in semantic_slots
    ):
        return True
    if layout.get("requiresNumericData") is True:
        return True
    haystack = " ".join([
        str(layout.get("id") or ""),
        str(layout.get("description") or ""),
        " ".join(str(value) for value in layout.get("elementTypes") or []),
        " ".join(str(value) for value in layout.get("slots") or []),
    ]).lower()
    return bool(re.search(r"chart|metric|gauge|donut|bar[_ -]?chart|line[_ -]?chart|stat", haystack))


def _item_allows_numeric_layout(item: Mapping[str, Any]) -> bool:
    kind = str(item.get("type") or item.get("pageType") or "").lower()
    return _item_has_explicit_numeric_data(item) or (
        any(token in kind for token in ("chart", "data", "stat", "metric", "数据", "统计"))
        and _numeric_token_count(item) > 0
    )


def _numeric_layout_has_sufficient_data(
    layout: Mapping[str, Any],
    item: Mapping[str, Any],
) -> bool:
    """Require more than one fact before a repeated metric layout is used."""
    if not _layout_requires_numeric_data(layout):
        return True
    # A single token such as O(1) is a fact, but not enough evidence for a
    # repeated metric composition. Prefer a text/card layout until at least
    # two independent values are available.
    minimum = 2
    return _item_allows_numeric_layout(item) and _numeric_token_count(item) >= minimum


def _visuals_enabled(settings: Mapping[str, Any]) -> bool:
    """Return whether the request can populate non-decorative image slots."""
    value = settings.get("includeVisuals") if isinstance(settings, Mapping) else None
    return str(value or "").strip().lower() in {"1", "true", "yes", "on"}


def _layout_has_required_visual(layout: Mapping[str, Any]) -> bool:
    """Detect a layout whose composition relies on a real image asset."""
    def walk(value: Any) -> bool:
        if isinstance(value, list):
            return any(walk(item) for item in value)
        if not isinstance(value, Mapping):
            return False
        if str(value.get("type") or "") == "image":
            return not bool(value.get("decorative")) and not bool(value.get("is_icon"))
        return any(walk(value.get(key)) for key in ("components", "elements", "children", "child"))

    return walk(layout)


def _select_layout_fallback(
    layout_catalog: List[Mapping[str, Any]],
    item: Mapping[str, Any],
    index: int,
    total: int,
    used_layout_ids: Optional[set[str]] = None,
) -> str:
    """Choose a Presenton layout when the structure model is unavailable.

    The fallback only selects from the embedded Presenton catalog. It never
    invents a layout id, so rendering remains compatible with the selected
    template even when the model times out.
    """
    if not layout_catalog:
        return ""
    candidates = layout_catalog
    # 目录版式是一个动态重复组，适合真正的目录页；把它分配给封面或
    # 普通内容页会把正文挤进导航行，并制造不必要的动态裁剪警报。
    contents_compatible = [
        layout
        for layout in layout_catalog
        if _layout_is_contents(layout) == _item_is_contents(item)
    ]
    if contents_compatible:
        candidates = contents_compatible
    compatible = [
        layout
        for layout in candidates
        if _numeric_layout_has_sufficient_data(layout, item)
    ]
    if compatible:
        candidates = compatible
    else:
        candidates = [layout for layout in candidates if not _layout_requires_numeric_data(layout)] or candidates
    ranked = sorted(
        candidates,
        key=lambda layout: _layout_match_score(layout, item, index, total),
        reverse=True,
    )
    if used_layout_ids:
        # Prefer a new layout when the score is close. This is the same
        # editorial trade-off used by mainstream generators: fit wins, but
        # adjacent pages should not look mechanically duplicated.
        best_score = _layout_match_score(ranked[0], item, index, total)
        for candidate in ranked:
            candidate_id = str(candidate.get("id") or "")
            if candidate_id not in used_layout_ids:
                score = _layout_match_score(candidate, item, index, total)
                if score >= best_score - 10:
                    return candidate_id
    return str(ranked[0].get("id") or "") if ranked else ""


def _layout_match_score(
    layout: Mapping[str, Any],
    item: Mapping[str, Any],
    index: int,
    total: int,
) -> float:
    """Score a layout for deterministic fallback/rebalancing.

    The score is intentionally explainable and conservative. It is not a
    second designer: it only protects the semantic contracts that an LLM can
    miss when it times out (cover/data/summary purpose and text capacity).
    """
    layout_id = str(layout.get("id") or "").lower()
    haystack = " ".join([
        layout_id,
        str(layout.get("description") or "").lower(),
        " ".join(str(value).lower() for value in layout.get("elementTypes") or []),
        " ".join(str(value).lower() for value in layout.get("slots") or []),
    ])
    kind = str(item.get("type") or item.get("pageType") or "").lower()
    text = " ".join([
        str(item.get("title") or ""),
        kind,
        str(item.get("objective") or ""),
        *[str(value) for value in (item.get("keyPoints") or item.get("content") or [])],
    ]).lower()
    point_count = len(item.get("keyPoints") or item.get("content") or [])
    score = 0.0

    if _layout_is_contents(layout) != _item_is_contents(item):
        score -= 1000
    elif _layout_is_contents(layout):
        score += 90

    if index == 1 or any(token in kind for token in ("cover", "title", "封面")):
        score += 80 if any(token in haystack for token in ("title_intro", "intro", "hero")) else 0
    if index == total or any(token in kind for token in ("summary", "conclusion", "总结", "结论")):
        score += 45 if any(token in haystack for token in ("metrics", "summary", "conclusion")) else 0
    if any(token in text for token in ("table", "表格", "对比", "比较")):
        score += 75 if "table" in haystack or "comparison" in haystack else -35
    elif any(token in text for token in ("chart", "趋势", "比例", "数据", "统计", "增长")):
        score += 75 if "chart" in haystack or "metric" in haystack else -20
    elif "table" in haystack or "chart" in haystack:
        score -= 25

    if any(token in text for token in ("流程", "步骤", "路径", "process", "step")):
        score += 18 if any(token in haystack for token in ("process", "flow", "step", "bullet")) else 0
    if any(token in text for token in ("团队", "成员", "角色", "team")):
        score += 22 if any(token in haystack for token in ("team", "profile", "member")) else 0

    hints = layout.get("capacityHints") if isinstance(layout.get("capacityHints"), Mapping) else {}
    try:
        cards = int(hints.get("cards") or 0)
        slots = int(hints.get("slotCount") or 0)
    except (TypeError, ValueError):
        cards = slots = 0
    if cards:
        expected_cards = max(1, min(4, point_count or 1))
        score += max(0, 18 - abs(cards - expected_cards) * 5)
    if slots:
        score += min(12, slots * 0.5)
    if point_count >= 5 and cards >= 3:
        score += 8
    if point_count <= 2 and cards >= 5:
        score -= 8
    return score


def _layout_is_contents(layout: Mapping[str, Any]) -> bool:
    """Return whether a layout is a navigation/contents layout."""
    haystack = " ".join(
        str(value or "").lower()
        for value in (
            layout.get("id"),
            layout.get("description"),
            *(layout.get("slots") or []),
        )
    )
    return any(token in haystack for token in ("table_of_contents", "contents", "目录", "toc"))


def _item_is_contents(item: Mapping[str, Any]) -> bool:
    kind = str(item.get("type") or item.get("pageType") or "").lower()
    title = str(item.get("title") or "").lower()
    return any(token in kind or token in title for token in ("目录", "catalog", "contents", "toc"))


def _rebalance_layout_choices(
    selected_layouts: List[str],
    layout_catalog: List[Mapping[str, Any]],
    items: List[Mapping[str, Any]],
) -> List[str]:
    """Fill missing choices and avoid accidental adjacent layout duplication."""
    valid = {str(layout.get("id") or "") for layout in layout_catalog}
    result: List[str] = []
    for index, item in enumerate(items, start=1):
        current = selected_layouts[index - 1] if index <= len(selected_layouts) else ""
        current_layout = next((layout for layout in layout_catalog if str(layout.get("id") or "") == current), {})
        if current not in valid or _layout_is_contents(current_layout) != _item_is_contents(item) or (
            _layout_requires_numeric_data(current_layout)
            and not _numeric_layout_has_sufficient_data(current_layout, item)
        ):
            current = _select_layout_fallback(
                layout_catalog, item, index, len(items), set(result[-2:])
            )
        elif result and current == result[-1] and index not in {1, len(items)}:
            current_score = _layout_match_score(
                next((layout for layout in layout_catalog if str(layout.get("id") or "") == current), {}),
                item,
                index,
                len(items),
            )
            alternatives = sorted(
                (layout for layout in layout_catalog if str(layout.get("id") or "") != current),
                key=lambda layout: _layout_match_score(layout, item, index, len(items)),
                reverse=True,
            )
            if alternatives:
                alternative = alternatives[0]
                if _layout_match_score(alternative, item, index, len(items)) >= current_score - 10:
                    current = str(alternative.get("id") or current)
        result.append(current)
    return result


def _cleanup_source_files(root: Path) -> None:
    ttl_seconds = max(60, int(os.getenv("PPT_SOURCE_FILE_TTL_SECONDS") or 86_400))
    cutoff = time.time() - ttl_seconds
    resolved_root = root.resolve()
    for path in resolved_root.iterdir():
        try:
            resolved = path.resolve()
            if resolved.parent == resolved_root and resolved.is_file() and resolved.stat().st_mtime < cutoff:
                resolved.unlink(missing_ok=True)
        except OSError:
            logger.warning("failed to clean expired PPT source file: %s", path)


ppt_generation_service = PptGenerationService()


_OUTLINE_LIKE_CONTENT = re.compile(
    r"本页(?:介绍|将|主要|重点|围绕|聚焦)|"
    r"(?:明确|梳理|概述|说明|了解|掌握).{0,18}(?:范围|目标|核心问题|基本关系|要点|内容|概念)",
)


def _content_quality_flags(
    slide: Mapping[str, Any],
    outline_item: Mapping[str, Any],
) -> List[str]:
    """Detect the low-value outline prose that should receive one repair pass."""
    kind = str(slide.get("type") or outline_item.get("type") or "").lower()
    if any(token in kind for token in ("cover", "封面", "目录", "toc", "catalog")):
        return []
    content = slide.get("content")
    if isinstance(content, str):
        content = [line.strip(" -*•") for line in content.splitlines() if line.strip(" -*•")]
    if not isinstance(content, list) or not any(str(value).strip() for value in content):
        return ["missing_content"]
    values = [str(value).strip() for value in content if str(value).strip()]
    points = outline_item.get("keyPoints") or []
    if isinstance(points, str):
        points = [line.strip(" -*•") for line in points.splitlines() if line.strip(" -*•")]
    outline_values = [str(value).strip() for value in points if str(value).strip()]

    def _compact(value: str) -> str:
        return re.sub(r"[\s，。、“”‘’：:；;,.!?！？（）()\[\]{}]", "", value).lower()

    compact_values = [_compact(value) for value in values]
    compact_points = [_compact(value) for value in outline_values]
    flags: List[str] = []
    if compact_points and all(
        value in compact_points or any(point and (value == point or value.startswith(point)) for point in compact_points)
        for value in compact_values
    ):
        flags.append("copies_outline_points")
    if any(_OUTLINE_LIKE_CONTENT.search(value) for value in values):
        flags.append("outline_summary_language")
    return flags


def _source_segment(source: str, slide_index: int, total_slides: int, max_chars: int = 60_000) -> str:
    """Return a position-aware segment of the source text.

    Early slides get the beginning, middle slides get the middle, late slides
    get the end. This ensures later pages still have relevant material even
    when the source exceeds the per-call character budget.
    """
    if not source or len(source) <= max_chars:
        return source[:max_chars]
    if total_slides <= 1:
        return source[:max_chars]
    ratio = slide_index / max(total_slides - 1, 1)
    start = int(ratio * max(0, len(source) - max_chars))
    return source[start:start + max_chars]


def _source_for_outline_item(
    source: str,
    item: Mapping[str, Any],
    slide_index: int,
    total_slides: int,
    max_chars: int = 6_000,
) -> str:
    """Find source evidence for a page before falling back to position slicing.

    Position-only slicing made a page about a late chapter receive unrelated
    text, which encouraged the content model to repeat the outline instead of
    expanding the actual source. Heading/keyword matching keeps the page tied
    to its chapter while retaining a deterministic fallback for unstructured
    documents.
    """
    text = str(source or "")
    if not text:
        return ""
    title = str(item.get("title") or "").strip()
    key_points = item.get("keyPoints") or item.get("content") or []
    if isinstance(key_points, str):
        key_points = [key_points]
    candidates = [title, *(str(value).strip() for value in key_points if str(value).strip())]
    needles = []
    for value in candidates:
        normalized = re.sub(r"[\s\W_]+", "", value, flags=re.UNICODE)
        if len(normalized) >= 3:
            needles.append(normalized[:40])
    lines = text.splitlines(keepends=True)
    offsets = []
    cursor = 0
    for line in lines:
        offsets.append((cursor, cursor + len(line), line))
        cursor += len(line)
    for needle in needles:
        for start, end, line in offsets:
            normalized_line = re.sub(r"[\s\W_]+", "", line, flags=re.UNICODE)
            if needle in normalized_line:
                half_window = max_chars // 2
                left = max(0, start - half_window)
                right = min(len(text), max(end, start + half_window) + half_window)
                return text[left:right][:max_chars]
    return _source_segment(text, slide_index, total_slides, max_chars=max_chars)


def _retry_llm_call(fn, max_retries: int = PPT_LLM_MAX_RETRIES, base_delay: float = PPT_LLM_RETRY_BASE_DELAY):
    """Call fn() with exponential backoff; max_retries excludes the first call."""
    last_exc: Optional[Exception] = None
    attempts = max(1, int(max_retries) + 1)
    for attempt in range(attempts):
        try:
            return fn()
        except HTTPException as exc:
            if exc.status_code in {429, 502, 503, 504} and attempt < attempts - 1:
                delay = base_delay * (2 ** attempt)
                logger.warning("LLM call failed (attempt %d/%d), retrying in %.1fs: %s", attempt + 1, attempts, delay, _safe_error_message(exc))
                time.sleep(delay)
                last_exc = exc
            else:
                raise
        except Exception as exc:
            if attempt < attempts - 1:
                delay = base_delay * (2 ** attempt)
                logger.warning("LLM call failed (attempt %d/%d), retrying in %.1fs: %s", attempt + 1, attempts, delay, _safe_error_message(exc))
                time.sleep(delay)
                last_exc = exc
            else:
                raise
    if last_exc is not None:
        raise last_exc
    raise RuntimeError("LLM call did not execute")


def _is_recoverable_outline_error(error: HTTPException) -> bool:
    """判断大纲模型错误是否可以交给原始资料恢复。"""
    status_code = int(getattr(error, "status_code", 0) or 0)
    if status_code in {429, 502, 503, 504}:
        return True
    message = str(getattr(error, "detail", error) or "").lower()
    return "timed out" in message or "request timeout" in message or "timeout" in message


def _is_outline_transport_error(error: BaseException) -> bool:
    """Recognize SDK transport failures without broadening normal 502 recovery.

    OpenAI-compatible clients do not necessarily expose connection failures as
    Python's built-in ``ConnectionError``.  The OpenAI SDK wraps them as
    ``APIConnectionError`` and httpx exposes ``ConnectError``/``NetworkError``.
    Walk the exception chain because either wrapper can be used as the cause of
    the other, but keep the SDK class/module allow-list narrow so validation,
    authentication, and malformed-output errors still take the normal 502 path.
    """
    pending = [error]
    seen: set[int] = set()
    while pending:
        current = pending.pop(0)
        if current is None or id(current) in seen:
            continue
        seen.add(id(current))

        if isinstance(current, (ConnectionError, TimeoutError)):
            return True

        exception_type = type(current)
        module_name = str(getattr(exception_type, "__module__", "") or "").lower()
        class_name = str(getattr(exception_type, "__name__", "") or "").lower()
        if module_name.startswith("openai") and class_name == "apiconnectionerror":
            return True
        if module_name.startswith(("httpx", "httpcore")) and class_name in {
            "connecterror",
            "networkerror",
            "connecttimeout",
            "readtimeout",
            "writetimeout",
            "pooltimeout",
            "timeoutexception",
        }:
            return True

        message = str(current or "").lower()
        if (
            any(marker in class_name for marker in ("timeout", "readtimeout", "connecttimeout"))
            or "timed out" in message
            or "request timeout" in message
        ):
            return True

        pending.extend(
            related
            for related in (getattr(current, "__cause__", None), getattr(current, "__context__", None))
            if related is not None
        )
    return False


def _safe_error_message(error: Exception) -> str:
    raw_message = str(error or "")
    if re.search(r"access\s+to\s+mode(?:l)?\s+denied", raw_message, flags=re.IGNORECASE):
        return "阿里云百炼拒绝了当前模型访问：请确认该 API Key 已开通所选模型，且业务空间/账号具备访问资格"
    message = re.sub(r"(?i)(api[-_ ]?key|authorization|token)\s*[:=]\s*\S+", r"\1=[已隐藏]", raw_message)
    message = re.sub(r"https?://[^\s]+", "[模型服务地址]", message)
    return (message.strip() or error.__class__.__name__)[:300]


def _run_export_quality_check(attachment: Any) -> Dict[str, Any]:
    """Inspect the finished PPTX without adding a failure path to generation.

    Export QA is deliberately best-effort.  The PPTX has already been
    produced at this point, so a missing/invalid audit input must not turn a
    successful export into a 502 or failed task.
    """
    if not isinstance(attachment, Mapping):
        return {
            "status": "skipped",
            "passed": True,
            "slides": 0,
            "errors": [],
            "warnings": [],
            "messages": [],
        }
    if str(attachment.get("ext") or "").strip().lower() != "pptx":
        return {
            "status": "skipped",
            "passed": True,
            "slides": 0,
            "errors": [],
            "warnings": [],
            "messages": [],
        }
    storage_key = str(attachment.get("storageKey") or "").strip()
    if not storage_key:
        return {
            "status": "unavailable",
            "passed": False,
            "slides": 0,
            "errors": [],
            "warnings": [],
            "messages": [],
        }
    try:
        export_root = generated_exporter._current_export_root().resolve()
        export_path = (export_root / storage_key).resolve()
        if not export_path.is_relative_to(export_root) or not export_path.is_file():
            raise FileNotFoundError(storage_key)
        report = validate_exported_pptx(export_path)
        errors = list(report.get("errors") or [])
        audit_warnings = list(report.get("warnings") or [])
        messages = _export_quality_messages(errors, audit_warnings)
        return {
            "status": "warning" if errors or audit_warnings else "clean",
            "passed": not errors,
            "slides": int(report.get("slides") or 0),
            "errors": errors,
            "warnings": audit_warnings,
            "messages": messages,
        }
    except Exception as exc:
        logger.warning("PPT exported-file QA skipped: %s", _safe_error_message(exc))
        return {
            "status": "unavailable",
            "passed": False,
            "slides": 0,
            "errors": [],
            "warnings": [],
            "messages": [],
        }


def _export_quality_messages(
    errors: List[Mapping[str, Any]],
    warnings: List[Mapping[str, Any]],
) -> List[str]:
    labels = {
        "TEMPLATE_PLACEHOLDER": "检测到模板示例内容",
        "TEXT_OVERLAP": "检测到文本区域重叠",
        "TEXT_ELLIPSIS": "检测到疑似省略文本",
        "INCOMPLETE_EXPRESSION": "检测到疑似不完整公式或表达式",
        "PAGE_NUMBER_MISMATCH": "检测到页码与页面顺序不一致",
    }
    result: List[str] = []
    seen: set[tuple[int, str]] = set()
    for issue in [*errors, *warnings]:
        if not isinstance(issue, Mapping):
            continue
        kind = str(issue.get("kind") or "EXPORT_QA")
        slide = int(issue.get("slide") or 0)
        key = (slide, kind)
        if key in seen:
            continue
        seen.add(key)
        label = labels.get(kind, "导出文件质量提示")
        result.append(f"第{slide}页{label}")
        if len(result) >= 80:
            break
    return result


def _is_invalid_json_llm_error(error: Exception) -> bool:
    """识别模型网关把非 JSON 响应包装成 502 的情况。"""
    detail = getattr(error, "detail", error)
    message = str(detail or error).lower()
    return (
        "invalid json" in message
        or "invalid_json" in message
        or "无效 json" in message
        or "无效json" in message
        or "未返回有效 json" in message
        or "未返回有效json" in message
    )
