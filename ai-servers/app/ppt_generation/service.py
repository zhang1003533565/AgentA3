import copy
import json
import logging
import os
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
from app.ppt_generation.embedded_config import EmbeddedPptConfig
from app.ppt_generation.presenton_renderer import render_presenton_presentation
from app.ppt_generation.ppt_mapper import (
    template_for_settings,
)
from app.ppt_generation.task_store import PptTaskStore
from app.ppt_generation.source_file_store import PptSourceFileStore
from app.ppt_generation.source_parser import PptSourceParseError, extract_source_text
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.rag.document_conversion import generated_exporter


_PAGE_HEADING = re.compile(r"###\s*第\s*(\d+)\s*页", re.IGNORECASE)
_FIELD = re.compile(r"^-\s*([^：:]+)[：:]\s*(.*)$")
logger = logging.getLogger(__name__)


class PptGenerationService:
    def __init__(self) -> None:
        self._tasks: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.RLock()
        self._task_store = PptTaskStore()
        self._source_files = PptSourceFileStore()
        self._executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="ai-ppt")
        self._embedded_config = EmbeddedPptConfig.from_env()
        self._template_catalog = EmbeddedTemplateCatalog()
        self._embedded_config.source_root.mkdir(parents=True, exist_ok=True)

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
                "default": bool(item.get("is_default")),
            })
        return {
            "engine": "presenton-embedded",
            "enhancedEngineAvailable": True,
            "editorEnabled": False,
            "scenes": [{
                "value": "review",
                "label": "复习资料",
                "description": "将学习资料整理成结构清晰的复习 PPT",
                "enabled": True,
                "default": True,
            }],
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
        allowed = {".txt", ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx"}
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
    ) -> Dict[str, Any]:
        source = self._resolve_source_text(request, user_id)
        topic = str(request.get("topic") or request.get("sourceName") or "复习资料").strip()
        if not source:
            raise HTTPException(status_code=422, detail="sourceContent 和 sourceFileId 不能同时为空")
        prompt = json.dumps({
            "topic": topic,
            "scene_type": str(request.get("scene") or "teaching"),
            "audience": "学生复习",
            "slide_count": int(request.get("pageCount") or 15),
            "constraints": "严格依据上传资料，不得补造事实；输出结构化 PPT 大纲。",
        }, ensure_ascii=False)
        evidence = [{"id": "uploaded-material", "source": request.get("sourceName"), "content": source[:60_000]}]
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

    def generate_slides(
        self,
        request: Mapping[str, Any],
        llm_config: Any,
        user_id: str = "",
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
        template_id = template_for_settings(settings, self._embedded_config.default_template)
        if not self._template_catalog.contains(template_id):
            template_id = self._embedded_config.default_template
        layout_catalog = self._template_catalog.layout_summaries(template_id)
        layout_prompt_catalog = "\n".join(
            f"- {item['id']}：{item['description']}；元素：{','.join(item['elementTypes'])}"
            for item in layout_catalog
        )
        outline_markdown = str(outline.get("outlineMarkdown") or _items_to_markdown(items))
        # Keep the outline as real Markdown instead of embedding it in a JSON
        # string.  The layout normalizer uses page headings as a deterministic
        # fallback when the model returns a slightly different format.
        layout_prompt = "\n".join([
            "请根据下面已经确认的 PPT 大纲生成逐页布局方案。",
            f"当前模板：{template_id}",
            "每页的“布局结构”必须只填写下面列表中的一个 layoutId，不要自行创造版式名称。",
            "含表格的内容优先选择 table 布局；没有数值数据不得选择 chart 布局；没有图片素材时尽量避免 image 布局。",
            "相邻页面尽量选择不同布局，但内容适配优先于形式变化。",
            "可用 Presenton 布局：",
            layout_prompt_catalog,
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
        enriched_slides: List[Dict[str, Any]] = []
        selected_layouts = [_layout_for_index(layout_markdown, index) for index in range(1, len(items) + 1)]
        valid_layout_ids = {str(item["id"]) for item in layout_catalog}
        selected_layouts = [value if value in valid_layout_ids else "" for value in selected_layouts]
        if self._embedded_config.enabled:
            try:
                source = self._resolve_source_text(request, user_id)
                content_prompt = json.dumps({
                    "title": str(outline.get("title") or "复习资料 PPT"),
                    "outline": items,
                    "settings": dict(settings),
                    "templateId": template_id,
                    "selectedLayouts": [
                        {
                            "slideIndex": index,
                            "layoutId": selected_layouts[index - 1],
                            "layout": next(
                                (value for value in layout_catalog if value["id"] == selected_layouts[index - 1]),
                                None,
                            ),
                        }
                        for index in range(1, len(items) + 1)
                    ],
                    "sharedPrompt": str(request.get("sharedPrompt") or ""),
                    "sourceMaterial": source[:60_000],
                }, ensure_ascii=False)
                content_token = set_active_llm_config(llm_config)
                try:
                    content_answer = run_specialist_agent(
                        "ppt_content_agent",
                        content_prompt,
                        ([{"id": "uploaded-material", "source": "学习资料", "content": source[:60_000]}] if source else []),
                    )
                finally:
                    reset_active_llm_config(content_token)
                content_payload = json.loads(content_answer)
                if isinstance(content_payload.get("slides"), list):
                    enriched_slides = [item for item in content_payload["slides"] if isinstance(item, dict)]
            except Exception:
                logger.exception("ppt content enrichment failed; using confirmed outline content")
        slides = []
        for index, item in enumerate(items, start=1):
            if not isinstance(item, Mapping):
                continue
            enriched = enriched_slides[index - 1] if index <= len(enriched_slides) else {}
            points = enriched.get("content") or item.get("keyPoints") or item.get("content") or []
            if isinstance(points, str):
                points = [line.strip(" -") for line in points.splitlines() if line.strip()]
            slides.append({
                "index": index,
                "type": enriched.get("type") or item.get("type") or item.get("pageType") or "content",
                "title": str(enriched.get("title") or item.get("title") or f"第 {index} 页"),
                "content": list(points) if isinstance(points, list) else [],
                "objective": str(enriched.get("objective") or item.get("objective") or ""),
                "layout": selected_layouts[index - 1],
                "templateLayoutId": selected_layouts[index - 1],
                "privatePrompt": str(item.get("privatePrompt") or ""),
                "visualPrompt": str(enriched.get("visualPrompt") or ""),
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
            "sourceName": str(request.get("sourceName") or "复习资料 PPT"),
            "sharedPrompt": str(request.get("sharedPrompt") or ""),
            "settings": copy.deepcopy(request.get("settings") or {}),
            "exportFormats": list(request.get("exportFormats") or ["pptx"]),
            "previews": [],
            "attachments": [],
            "formatErrors": {},
            "error": None,
        }
        with self._lock:
            self._tasks[task_id] = task
            self._task_store.put(task)
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
        if task.get("status") in {"completed", "failed", "cancelled"}:
            return task
        self._update(
            task_id,
            status="cancelled",
            stage="cancelled",
            message="PPT 生成已取消",
            cancelRequested=True,
        )
        return self.get_task(user_id, task_id)

    def retry_task(self, user_id: str, task_id: str, llm_config: Any) -> Dict[str, Any]:
        task = self.get_task(user_id, task_id)
        if task.get("status") not in {"failed", "cancelled"}:
            raise HTTPException(status_code=409, detail="只有失败或已取消的 PPT 任务可以重试")
        return self.create_task(user_id, {
            "sourceName": task.get("sourceName") or "复习资料 PPT",
            "outline": task.get("outline") or {},
            "slides": task.get("slides") or [],
            "sharedPrompt": task.get("sharedPrompt") or "",
            "settings": task.get("settings") or {},
            "exportFormats": task.get("exportFormats") or ["pptx"],
        }, llm_config)

    def _update(self, task_id: str, **values: Any) -> None:
        with self._lock:
            task = self._task_store.get(task_id) or self._tasks.get(task_id)
            if task is None:
                raise KeyError(task_id)
            if task.get("status") == "cancelled" and values.get("status") != "cancelled":
                return
            task.update(values)
            task["updatedAt"] = int(time.time() * 1000)
            self._tasks[task_id] = task
            self._task_store.put(task)

    def _resolve_source_text(self, request: Mapping[str, Any], user_id: str) -> str:
        source = str(request.get("sourceContent") or "").strip()
        if source:
            return source
        file_id = str(request.get("sourceFileId") or "").strip()
        if not file_id:
            return ""
        metadata = self._source_files.get_owned(user_id, file_id)
        if metadata is None:
            raise HTTPException(status_code=404, detail="PPT 资料文件不存在或已过期")
        local_path = Path(str(metadata.get("localPath") or "")).resolve()
        source_root = self._embedded_config.source_root.resolve()
        if not local_path.is_relative_to(source_root) or not local_path.is_file():
            raise HTTPException(status_code=404, detail="PPT 资料文件不存在或已过期")
        try:
            return extract_source_text(local_path)
        except PptSourceParseError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        except Exception as exc:
            logger.exception("embedded PPT source parsing failed")
            raise HTTPException(
                status_code=422,
                detail=f"PPT 资料解析失败：{_safe_error_message(exc)}",
            ) from exc

    def _execute_presenton_task(self, task_id: str, request: Dict[str, Any]) -> None:
        try:
            slides = request.get("slides")
            if not isinstance(slides, list) or len(slides) < 2:
                raise ValueError("slides 至少需要两页")
            outline = request.get("outline") if isinstance(request.get("outline"), Mapping) else {}
            settings = request.get("settings") if isinstance(request.get("settings"), Mapping) else {}
            raw_title = str(outline.get("title") or request.get("sourceName") or "复习资料 PPT")
            title = re.sub(r"\.(?:txt|pdf|docx?|pptx?|xlsx?)$", "", raw_title, flags=re.IGNORECASE).strip()
            template_id = template_for_settings(settings, self._embedded_config.default_template)
            if not self._template_catalog.contains(template_id):
                template_id = self._embedded_config.default_template
            render_settings = {**dict(settings), "templateId": template_id}

            self._update(
                task_id,
                status="running",
                stage="preparing",
                progress=18,
                message="正在整理已确认的页面内容",
            )
            self._update(
                task_id,
                stage="rendering",
                progress=48,
                message="正在使用内置模板渲染 PPT",
            )
            pptx_attachment, _ = render_presenton_presentation(
                slides,
                title or "复习资料 PPT",
                render_settings,
            )
            attachments = [pptx_attachment]
            previews: List[Dict[str, Any]] = []
            format_errors: Dict[str, str] = {}
            requested = {str(value).lower() for value in request.get("exportFormats") or ["pptx"]}
            if "pdf" in requested:
                self._update(
                    task_id,
                    stage="exporting",
                    progress=82,
                    message="正在生成 PDF 和页面预览",
                )
                try:
                    pdf_attachment, previews = _render_pdf_and_previews(pptx_attachment)
                    attachments.append(pdf_attachment)
                except Exception as exc:
                    logger.exception("embedded PPT PDF export failed")
                    format_errors["pdf"] = _safe_error_message(exc)
            self._update(
                task_id,
                status="completed",
                stage="completed",
                progress=100,
                message="PPT 生成完成",
                presentationId=f"embedded_{uuid.uuid4().hex}",
                editorUrl="",
                attachments=attachments,
                previews=previews,
                formatErrors=format_errors,
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
        del llm_config
        self._execute_presenton_task(task_id, request)

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
