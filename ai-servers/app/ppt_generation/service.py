import copy
import json
import logging
import os
import re
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any, Dict, List, Mapping

from fastapi import HTTPException

from app.model_providers.runtime_config import reset_active_llm_config, set_active_llm_config
from app.multi_agents.image_agent.agent import image_agent
from app.multi_agents.ppt_structure_agent.agent import normalize_structure_answer
from app.multi_agents.runner import run_specialist_agent
from app.ppt_generation.embedded_config import EmbeddedPptConfig
from app.ppt_generation.presenton_html_renderer import render_presenton_html
from app.ppt_generation.presenton_generation_prompts import (
    PRESENTON_CONTENT_RULES,
    PRESENTON_STRUCTURE_RULES,
)
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
        structure_prompt = json.dumps({
            "presentonStructureRules": PRESENTON_STRUCTURE_RULES,
            "templateId": template_id,
            "slideCount": len(items),
            "layouts": layout_catalog,
            "outline": items,
            "settings": dict(settings),
            "userInstructions": str(request.get("sharedPrompt") or request.get("instructions") or ""),
        }, ensure_ascii=False)
        token = set_active_llm_config(llm_config)
        try:
            try:
                structure_answer = run_specialist_agent("ppt_structure_agent", structure_prompt, [])
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
        enriched_slides: List[Dict[str, Any]] = []
        generation_warnings: List[str] = []
        valid_layout_ids = {str(item["id"]) for item in layout_catalog}
        selected_layouts = [
            value if value in valid_layout_ids else _select_layout_fallback(
                layout_catalog, items[index - 1], index, len(items)
            )
            for index, value in enumerate(selected_layouts, start=1)
        ]
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
                    "presentonContentRules": PRESENTON_CONTENT_RULES,
                    "layoutContentInstruction": (
                        "如果 layout.slots 中存在组件 name，请额外返回 layoutContent 对象，"
                        "键必须使用这些组件 name，值为该组件应显示的标题、正文、标签或数值。"
                        "不要伪造图片；没有可靠映射时返回空对象。"
                    ),
                    "layoutContentOutputExample": {
                        "headline_text": "本页核心标题",
                        "supporting_paragraph": "支持标题的简短解释",
                        "item_title": "卡片或条目标题",
                        "item_body": "卡片或条目正文",
                        "chart_data": {"categories": ["A", "B"], "series": [{"name": "数据", "values": [1, 2]}]},
                    },
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
                normalized_content = _sanitize_content_payload(
                    content_payload,
                    selected_layouts,
                    layout_catalog,
                    len(items),
                )
                enriched_slides = normalized_content["slides"]
            except Exception as exc:
                logger.warning("ppt content enrichment failed; using confirmed outline content: %s", _safe_error_message(exc))
                generation_warnings.append("逐页内容模型未完全返回模板组件数据，已使用确认的大纲内容继续生成")
        slides = []
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
                "speakerNote": str(enriched.get("speakerNote") or ""),
                "visualPrompt": str(enriched.get("visualPrompt") or ""),
                "imagePath": str(enriched.get("imagePath") or ""),
                "imageMode": image_mode,
                "imageStatus": (
                    "generated" if enriched.get("imagePath")
                    else "pending" if wants_ai_image
                    else "placeholder"
                ),
                # Preserve the Presenton component-slot payload generated by
                # the content agent.  The renderer uses this before falling
                # back to sequential legacy content.
                "layoutContent": copy.deepcopy(
                    enriched.get("layoutContent")
                    if isinstance(enriched.get("layoutContent"), Mapping)
                    and enriched.get("layoutContent")
                    else _fallback_layout_content(
                        {
                            "index": index,
                            "title": str(enriched.get("title") or item.get("title") or f"第 {index} 页"),
                            "content": list(points) if isinstance(points, list) else [],
                            "objective": str(enriched.get("objective") or item.get("objective") or ""),
                        },
                        next(
                            (value for value in layout_catalog if value["id"] == selected_layouts[index - 1]),
                            None,
                        ),
                    )
                ),
            })
        return {
            "slides": slides,
            "sharedPrompt": str(request.get("sharedPrompt") or "简洁、清晰、适合学生复习"),
            "warnings": generation_warnings,
            # Keep the historical response field for existing clients. The
            # actual decision now comes from Presenton structure JSON above.
            "layoutMarkdown": _selected_layouts_markdown(selected_layouts, items),
        }

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
            "sourceName": task.get("sourceName") or "复习资料 PPT",
            "outline": task.get("outline") or {},
            "slides": slides,
            "sharedPrompt": task.get("sharedPrompt") or "",
            "settings": {**(task.get("settings") or {}), "imageMode": "placeholder"},
            "exportFormats": task.get("exportFormats") or ["pptx"],
        }
        with self._lock:
            task["slides"] = slides
            task["status"] = "queued"
            task["progress"] = 0
            task["stage"] = "queued"
            task["error"] = None
            self._task_store.put(task)
            self._tasks[task_id] = task
        self._executor.submit(self._execute_task, task_id, request, None)
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

    def _execute_presenton_task(self, task_id: str, request: Dict[str, Any], llm_config: Any = None) -> None:
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
            if self._is_cancelled(task_id):
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
            pdf_attachment, _, previews, pptx_attachment = render_presenton_html(
                slides,
                title or "复习资料 PPT",
                render_settings,
            )
            attachments = [pdf_attachment]
            if pptx_attachment:
                attachments.insert(0, pptx_attachment)
            format_errors: Dict[str, str] = {}
            requested = {str(value).lower() for value in request.get("exportFormats") or ["pptx"]}
            if "pptx" in requested and not pptx_attachment:
                format_errors["pptx"] = (
                    "Presenton HTML 渲染已启用；PPTX 需要 Linux 容器内置 presenton-export 转换器。"
                )
            if "pdf" in requested:
                self._update(
                    task_id,
                    stage="exporting",
                    progress=82,
                    message="正在生成 PDF 和页面预览",
                )
                # PDF and previews are already produced by the Presenton
                # Chromium renderer above.
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
        self._update(
            task_id,
            stage="visuals",
            progress=30,
            message=f"正在生成页面配图（0/{len(pending)}）",
        )
        result = copy.deepcopy(slides)
        for position, slide in enumerate(result):
            if str(slide.get("imageStatus") or "") != "pending":
                continue
            if self._is_cancelled(task_id):
                return result
            try:
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
                import base64
                image_bytes = base64.b64decode(encoded, validate=True)
                image_path = generated_exporter._new_export_path(
                    f"ppt-slide-{slide.get('index') or position + 1}", "png"
                )
                generated_exporter._atomic_write_payload(
                    image_path,
                    lambda temporary_path: temporary_path.write_bytes(image_bytes),
                )
                slide["imagePath"] = str(image_path)
                slide["imageStatus"] = "generated"
            except Exception as exc:
                logger.warning("PPT visual generation failed for slide %s: %s", position + 1, _safe_error_message(exc))
                slide["imageStatus"] = "failed"
                slide["imageError"] = _safe_error_message(exc)
            completed = sum(1 for value in result if value.get("imageStatus") in {"generated", "failed"})
            self._update(
                task_id,
                progress=30 + int(completed * 15 / max(1, len(pending))),
                message=f"正在生成页面配图（{completed}/{len(pending)}）",
            )
        return result

    def _is_cancelled(self, task_id: str) -> bool:
        with self._lock:
            task = self._task_store.get(task_id) or self._tasks.get(task_id)
            return bool(task and task.get("status") == "cancelled")

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
    layout_catalog: List[Mapping[str, Any]],
    expected_count: int,
) -> Dict[str, Any]:
    slides = payload.get("slides") if isinstance(payload, Mapping) else None
    if not isinstance(slides, list) or len(slides) < expected_count:
        raise ValueError(f"ppt_content_agent 返回 {len(slides) if isinstance(slides, list) else 0} 页，期望 {expected_count} 页")
    layouts = {str(value.get("id")): value for value in layout_catalog}
    normalized: List[Dict[str, Any]] = []
    for index in range(expected_count):
        raw = slides[index] if isinstance(slides[index], Mapping) else {}
        item = dict(raw)
        layout = layouts.get(selected_layouts[index]) or {}
        item["index"] = index + 1
        item["layoutContent"] = _sanitize_layout_content(
            item.get("layoutContent") or item.get("componentContent"), layout
        )
        normalized.append(item)
    return {"slides": normalized}


def _sanitize_layout_content(value: Any, layout: Mapping[str, Any]) -> Dict[str, Any]:
    if not isinstance(value, Mapping):
        return {}
    schema = layout.get("componentSchema") if isinstance(layout.get("componentSchema"), list) else []
    by_name = {
        str(item.get("name") or "").strip().lower(): item
        for item in schema if isinstance(item, Mapping) and str(item.get("name") or "").strip()
    }
    result: Dict[str, Any] = {}
    for raw_key, raw_value in value.items():
        key = str(raw_key or "").strip()
        if not key:
            continue
        component = by_name.get(key.lower())
        canonical_key = str(component.get("name")) if component else key
        if component is None and key.lower() not in {"chart_data", "table_data"}:
            continue
        if key.lower() == "chart_data":
            chart = _sanitize_chart_data(raw_value)
            if chart:
                result[canonical_key] = chart
            continue
        if key.lower() == "table_data":
            table = _sanitize_table_data(raw_value)
            if table:
                result[canonical_key] = table
            continue
        if isinstance(raw_value, list):
            values = [item if isinstance(item, (str, int, float, bool)) else str(item) for item in raw_value]
            max_children = component.get("max_children") if component else None
            if isinstance(max_children, int) and max_children > 0:
                values = values[:max_children]
            result[canonical_key] = values
        elif isinstance(raw_value, Mapping):
            result[canonical_key] = dict(raw_value)
        else:
            text = str(raw_value) if raw_value is not None else ""
            max_length = component.get("max_length") if component else None
            if isinstance(max_length, int) and max_length > 0:
                text = _fit_component_text(text, max_length)
            result[canonical_key] = text
    return result


def _fit_component_text(value: str, max_length: int) -> str:
    if max_length <= 0:
        return ""
    if len(value) <= max_length:
        return value
    if max_length == 1:
        return "…"
    clipped = value[:max_length].rstrip()
    boundary = max(clipped.rfind("。"), clipped.rfind("；"), clipped.rfind("，"), clipped.rfind(" "))
    if boundary >= max_length // 2:
        clipped = clipped[:boundary]
    return (clipped.rstrip("，；、,; ") + "…")[:max_length]


def _sanitize_table_data(value: Any) -> Dict[str, Any]:
    if not isinstance(value, Mapping):
        return {}
    columns = value.get("columns")
    rows = value.get("rows")
    if not isinstance(columns, list) or not columns or not isinstance(rows, list):
        return {}
    normalized_columns = [str(item)[:80] for item in columns]
    normalized_rows = []
    for row in rows[:30]:
        if not isinstance(row, list) or len(row) != len(normalized_columns):
            return {}
        normalized_rows.append([str(item)[:160] for item in row])
    return {"columns": normalized_columns, "rows": normalized_rows}


def _sanitize_chart_data(value: Any) -> Dict[str, Any]:
    if not isinstance(value, Mapping):
        return {}
    categories = value.get("categories")
    series = value.get("series")
    if not isinstance(categories, list) or not categories or not isinstance(series, list):
        return {}
    normalized_categories = [str(item)[:80] for item in categories[:30]]
    normalized_series = []
    for item in series[:8]:
        if not isinstance(item, Mapping) or not isinstance(item.get("values"), list):
            return {}
        values = item.get("values")
        if len(values) != len(normalized_categories):
            return {}
        try:
            numeric_values = [float(number) for number in values]
        except (TypeError, ValueError):
            return {}
        normalized_series.append({"name": str(item.get("name") or "数据")[:80], "values": numeric_values})
    if not normalized_series:
        return {}
    result: Dict[str, Any] = {"categories": normalized_categories, "series": normalized_series}
    chart_type = str(value.get("chartType") or value.get("chart_type") or "").strip()
    if chart_type:
        result["chartType"] = chart_type[:32]
    return result


def _fallback_layout_content(
    slide: Mapping[str, Any],
    layout: Mapping[str, Any] | None,
) -> Dict[str, Any]:
    """Build a safe slot payload when the model omits layoutContent.

    This is deliberately semantic rather than coordinate-based: the slot name
    is the contract, so the same fallback works across Presenton templates.
    """
    if not isinstance(layout, Mapping):
        return {}
    slots = layout.get("slots") if isinstance(layout.get("slots"), list) else []
    title = str(slide.get("title") or "").strip()
    values = [str(value).strip() for value in slide.get("content") or [] if str(value).strip()]
    objective = str(slide.get("objective") or "").strip()
    if objective and objective not in values:
        values.append(objective)
    cursor = 0
    result: Dict[str, Any] = {}
    for raw_name in slots:
        name = str(raw_name or "").strip()
        lower = name.lower()
        if not name or name in result:
            continue
        if any(token in lower for token in ("page_number", "page_number", "folio", "pagination")):
            result[name] = str(slide.get("index") or "")
        elif any(token in lower for token in ("headline", "heading", "main_title", "cover_title", "header_title", "intro_heading")):
            result[name] = title
        elif any(token in lower for token in ("list", "items", "bullet")):
            result[name] = values[:6]
        elif any(token in lower for token in ("body", "paragraph", "description", "copy", "supporting", "detail", "summary", "note")):
            result[name] = values[cursor] if cursor < len(values) else ""
            cursor += 1
        elif any(token in lower for token in ("title", "label", "value", "metric", "quote", "item")):
            result[name] = values[cursor] if cursor < len(values) else title
            cursor += 1
    return result


def _select_layout_fallback(
    layout_catalog: List[Mapping[str, Any]],
    item: Mapping[str, Any],
    index: int,
    total: int,
) -> str:
    """Choose a Presenton layout when the structure model is unavailable.

    The fallback only selects from the embedded Presenton catalog. It never
    invents a layout id, so rendering remains compatible with the selected
    template even when the model times out.
    """
    if not layout_catalog:
        return ""
    title = str(item.get("title") or "").lower()
    kind = str(item.get("type") or item.get("pageType") or "").lower()
    text = " ".join(
        [title, kind, str(item.get("objective") or "")] +
        [str(value) for value in (item.get("keyPoints") or item.get("content") or [])]
    ).lower()

    def find(*needles: str) -> str:
        for layout in layout_catalog:
            haystack = " ".join([
                str(layout.get("id") or ""),
                str(layout.get("description") or ""),
                " ".join(str(value) for value in layout.get("elementTypes") or []),
                " ".join(str(value) for value in layout.get("slots") or []),
            ]).lower()
            if all(needle in haystack for needle in needles):
                return str(layout.get("id") or "")
        return ""

    if index == 1 or kind in {"cover", "title", "封面"}:
        choice = find("title_intro") or find("intro") or find("title", "description")
    elif any(token in text for token in ("table", "表格", "对比", "比较")):
        choice = find("table")
    elif any(token in text for token in ("chart", "趋势", "比例", "数据", "统计")):
        choice = find("chart")
    elif any(token in text for token in ("image", "图片", "示意图", "流程图")):
        choice = find("image", "bullet") or find("image")
    elif index == total or kind in {"summary", "总结", "复习"}:
        choice = find("metrics") or find("description", "bullet")
    else:
        choice = find("description", "bullet") or find("description")

    if choice:
        return choice
    return str(layout_catalog[(index - 1) % len(layout_catalog)].get("id") or "")


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


def _safe_error_message(error: Exception) -> str:
    message = re.sub(r"(?i)(api[-_ ]?key|authorization|token)\s*[:=]\s*\S+", r"\1=[已隐藏]", str(error or ""))
    message = re.sub(r"https?://[^\s]+", "[模型服务地址]", message)
    return (message.strip() or error.__class__.__name__)[:300]
