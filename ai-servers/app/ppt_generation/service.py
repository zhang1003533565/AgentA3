import copy
import json
import logging
import re
import shutil
import subprocess
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any, Dict, List, Mapping

import fitz
from fastapi import HTTPException

from app.model_providers.runtime_config import reset_active_llm_config, set_active_llm_config
from app.multi_agents.ppt_layout_agent.agent import normalize_ppt_layout_answer
from app.multi_agents.runner import run_specialist_agent
from app.rag.document_conversion import export_presentation
from app.rag.document_conversion import generated_exporter


_PAGE_HEADING = re.compile(r"###\s*第\s*(\d+)\s*页", re.IGNORECASE)
_FIELD = re.compile(r"^-\s*([^：:]+)[：:]\s*(.*)$")
logger = logging.getLogger(__name__)


class PptGenerationService:
    def __init__(self) -> None:
        self._tasks: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.RLock()
        self._executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="ai-ppt")

    def generate_outline(self, request: Mapping[str, Any], llm_config: Any) -> Dict[str, Any]:
        source = str(request.get("sourceContent") or "").strip()
        topic = str(request.get("topic") or request.get("sourceName") or "复习资料").strip()
        if not source:
            raise HTTPException(status_code=422, detail="sourceContent 不能为空")
        prompt = json.dumps({
            "topic": topic,
            "scene_type": str(request.get("scene") or "teaching"),
            "audience": "学生复习",
            "slide_count": int(request.get("pageCount") or 15),
            "constraints": "严格依据上传资料，不得补造事实；输出结构化 PPT 大纲。",
        }, ensure_ascii=False)
        evidence = [{"id": "uploaded-material", "source": request.get("sourceName"), "content": source[:12000]}]
        token = set_active_llm_config(llm_config)
        try:
            try:
                markdown = run_specialist_agent("ppt_outline_agent", prompt, evidence)
            except HTTPException:
                raise
            except Exception as exc:
                logger.exception("ppt outline generation failed")
                raise HTTPException(
                    status_code=502,
                    detail=f"PPT 大纲模型调用失败：{_safe_error_message(exc)}",
                ) from exc
        finally:
            reset_active_llm_config(token)
        return {
            "outlineId": f"outline_{uuid.uuid4().hex}",
            "title": topic,
            "items": _outline_items(markdown),
            "outlineMarkdown": markdown,
        }

    def generate_slides(self, request: Mapping[str, Any], llm_config: Any) -> Dict[str, Any]:
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
        outline_markdown = str(outline.get("outlineMarkdown") or _items_to_markdown(items))
        # Keep the outline as real Markdown instead of embedding it in a JSON
        # string.  The layout normalizer uses page headings as a deterministic
        # fallback when the model returns a slightly different format.
        layout_prompt = "\n".join([
            "请根据下面已经确认的 PPT 大纲生成逐页布局方案。",
            f"主题风格：{settings.get('pptStyle') or 'simple'}",
            f"其他约束：{json.dumps(dict(settings), ensure_ascii=False)}",
            "",
            outline_markdown,
        ])
        token = set_active_llm_config(llm_config)
        try:
            try:
                layout_markdown = run_specialist_agent("ppt_layout_agent", layout_prompt, [])
            except HTTPException as exc:
                if exc.status_code == 502 and "返回内容不符合约定格式" in str(exc.detail):
                    logger.warning(
                        "ppt layout agent returned a non-contract answer; using outline-based fallback"
                    )
                    layout_markdown = normalize_ppt_layout_answer("", layout_prompt)
                else:
                    raise
            except Exception as exc:
                logger.exception("ppt slide layout generation failed")
                raise HTTPException(
                    status_code=502,
                    detail=f"PPT 布局模型调用失败：{_safe_error_message(exc)}",
                ) from exc
        finally:
            reset_active_llm_config(token)
        slides = []
        for index, item in enumerate(items, start=1):
            if not isinstance(item, Mapping):
                continue
            points = item.get("keyPoints") or item.get("content") or []
            if isinstance(points, str):
                points = [line.strip(" -") for line in points.splitlines() if line.strip()]
            slides.append({
                "index": index,
                "type": item.get("type") or item.get("pageType") or "content",
                "title": str(item.get("title") or f"第 {index} 页"),
                "content": list(points) if isinstance(points, list) else [],
                "objective": str(item.get("objective") or ""),
                "layout": _layout_for_index(layout_markdown, index),
                "privatePrompt": str(item.get("privatePrompt") or ""),
                "visualPrompt": "",
            })
        return {
            "slides": slides,
            "sharedPrompt": str(request.get("sharedPrompt") or "简洁、清晰、适合学生复习"),
            "layoutMarkdown": layout_markdown,
        }

    def create_task(self, user_id: str, request: Mapping[str, Any], llm_config: Any) -> Dict[str, Any]:
        slides = request.get("slides")
        if not isinstance(slides, list) or not slides:
            raise HTTPException(status_code=422, detail="slides 不能为空")
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
            "outline": copy.deepcopy(request.get("outline") or {}),
            "slides": copy.deepcopy(slides),
            "settings": copy.deepcopy(request.get("settings") or {}),
            "exportFormats": list(request.get("exportFormats") or ["pptx"]),
            "previews": [],
            "attachments": [],
            "formatErrors": {},
            "error": None,
        }
        with self._lock:
            self._tasks[task_id] = task
        self._executor.submit(self._execute_task, task_id, copy.deepcopy(dict(request)), llm_config)
        return {"taskId": task_id, "status": "queued"}

    def get_task(self, user_id: str, task_id: str) -> Dict[str, Any]:
        with self._lock:
            task = self._tasks.get(task_id)
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

    def _update(self, task_id: str, **values: Any) -> None:
        with self._lock:
            task = self._tasks[task_id]
            task.update(values)
            task["updatedAt"] = int(time.time() * 1000)

    def _execute_task(self, task_id: str, request: Dict[str, Any], llm_config: Any) -> None:
        try:
            self._update(task_id, status="running", stage="validating", progress=15, message="正在校验已确认的页面内容")
            slides = request["slides"]
            outline = {"slides": [
                {
                    "title": str(slide.get("title") or f"第 {index} 页"),
                    "bullets": slide.get("content") or [slide.get("objective") or "复习要点"],
                    "evidenceIds": ["uploaded-material"],
                }
                for index, slide in enumerate(slides, start=1)
            ]}
            title = str(request.get("sourceName") or request.get("outline", {}).get("title") or "复习资料 PPT")
            self._update(task_id, stage="exporting", progress=60, message="正在生成 PPTX 文件")
            attachment = export_presentation(outline, {
                "title": re.sub(r"\.txt$", "", title, flags=re.IGNORECASE),
                "subtitle": str(request.get("sharedPrompt") or "AI 复习资料"),
                "reviewStatus": "passed",
                "evidenceIds": ["uploaded-material"],
            })
            attachment = {**attachment, "type": "pptx"}
            attachments = [attachment]
            previews: List[Dict[str, Any]] = []
            format_errors: Dict[str, str] = {}
            requested = {str(value).lower() for value in request.get("exportFormats") or ["pptx"]}
            if "pdf" in requested:
                self._update(task_id, stage="rendering", progress=78, message="正在生成 PDF 和页面预览")
                try:
                    pdf_attachment, previews = _render_pdf_and_previews(attachment)
                    attachments.append(pdf_attachment)
                except Exception as exc:
                    format_errors["pdf"] = str(exc)
            self._update(task_id, status="completed", stage="completed", progress=100,
                         message="PPT 生成完成", attachments=attachments, previews=previews,
                         formatErrors=format_errors)
        except Exception as exc:
            self._update(task_id, status="failed", stage="failed", message="PPT 生成失败",
                         error={"type": exc.__class__.__name__, "message": str(exc)})


def _outline_items(markdown: str) -> List[Dict[str, Any]]:
    matches = list(_PAGE_HEADING.finditer(markdown or ""))
    items: List[Dict[str, Any]] = []
    for offset, match in enumerate(matches):
        block = markdown[match.end(): matches[offset + 1].start() if offset + 1 < len(matches) else len(markdown)]
        fields: Dict[str, str] = {}
        active = ""
        for raw_line in block.splitlines():
            line = raw_line.strip()
            field = _FIELD.match(line)
            if field:
                active = field.group(1).strip()
                fields[active] = field.group(2).strip()
            elif active == "核心内容" and line.startswith("-"):
                fields[active] = f"{fields.get(active, '')}\n{line.lstrip('- ').strip()}".strip()
        points = [line.strip(" -") for line in fields.get("核心内容", "").splitlines() if line.strip(" -")]
        items.append({
            "id": f"slide_{match.group(1)}",
            "level": 1,
            "title": fields.get("页标题") or f"第 {match.group(1)} 页",
            "type": fields.get("页面类型") or "content",
            "objective": fields.get("本页目标") or "",
            "keyPoints": points,
        })
    return items


def _items_to_markdown(items: List[Any]) -> str:
    lines = ["## PPT 大纲", "", "### 大纲信息", "- 使用场景：教学", "- 受众：学生"]
    for index, item in enumerate(items, start=1):
        item = item if isinstance(item, Mapping) else {}
        points = item.get("keyPoints") or item.get("content") or []
        if isinstance(points, str):
            points = [points]
        lines.extend(["", f"### 第{index}页", f"- 页标题：{item.get('title') or f'第 {index} 页'}",
                      f"- 页面类型：{item.get('type') or '内容页'}", f"- 本页目标：{item.get('objective') or '复习核心内容'}",
                      "- 核心内容：", *[f"  - {point}" for point in points], "- 展示建议：突出重点", "- 素材建议：按需配图"])
    return "\n".join(lines)


def _layout_for_index(markdown: str, index: int) -> str:
    match = re.search(rf"###\s*第\s*{index}\s*页(?P<body>.*?)(?=###\s*第|\Z)", markdown or "", re.DOTALL)
    if not match:
        return "content_default"
    field = re.search(r"-\s*布局结构[：:]\s*([^\n]+)", match.group("body"))
    return field.group(1).strip() if field else "content_default"


def _render_pdf_and_previews(ppt_attachment: Mapping[str, Any]):
    soffice = shutil.which("soffice") or shutil.which("libreoffice")
    if not soffice:
        raise RuntimeError("运行环境未安装 LibreOffice，无法生成 PDF 和预览")
    export_root = generated_exporter._current_export_root()
    ppt_path = (export_root / str(ppt_attachment["storageKey"])).resolve()
    subprocess.run([soffice, "--headless", "--convert-to", "pdf", "--outdir", str(export_root), str(ppt_path)],
                   check=True, capture_output=True, timeout=120)
    pdf_path = ppt_path.with_suffix(".pdf")
    if not pdf_path.is_file():
        raise RuntimeError("LibreOffice 未生成 PDF 文件")
    pdf_attachment = generated_exporter._attachment_for_file(pdf_path, "ai_ppt_generation_tool", "PDF")
    pdf_attachment["type"] = "pdf"
    previews = []
    document = fitz.open(pdf_path)
    try:
        for index, page in enumerate(document, start=1):
            preview_path = generated_exporter._new_export_path(f"{ppt_path.stem}-slide-{index}", "png")
            page.get_pixmap(matrix=fitz.Matrix(1.4, 1.4), alpha=False).save(preview_path)
            item = generated_exporter._attachment_for_file(preview_path, "ai_ppt_generation_tool", f"第 {index} 页预览")
            item.update({"type": "preview", "slideIndex": index})
            previews.append(item)
    finally:
        document.close()
    return pdf_attachment, previews


ppt_generation_service = PptGenerationService()


def _safe_error_message(error: Exception) -> str:
    message = re.sub(r"(?i)(api[-_ ]?key|authorization|token)\s*[:=]\s*\S+", r"\1=[已隐藏]", str(error or ""))
    message = re.sub(r"https?://[^\s]+", "[模型服务地址]", message)
    return (message.strip() or error.__class__.__name__)[:300]
