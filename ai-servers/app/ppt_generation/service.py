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

from app.model_providers.runtime_config import (
    reset_active_llm_config,
    reset_active_llm_timeout,
    set_active_llm_config,
    set_active_llm_timeout,
)
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
PPT_PAGE_LLM_TIMEOUT_SECONDS = max(60, int(os.getenv("PPT_PAGE_LLM_TIMEOUT_SECONDS") or 300))


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
        timeout_token = set_active_llm_timeout(PPT_PAGE_LLM_TIMEOUT_SECONDS)
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
            reset_active_llm_timeout(timeout_token)
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
        progress_callback: Optional[Callable[[Mapping[str, Any]], None]] = None,
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
        template_payload = self._template_catalog.load(template_id)
        layouts_by_id = {
            str(layout.get("id")): layout
            for layout in template_payload.get("layouts") or []
            if isinstance(layout, Mapping) and str(layout.get("id") or "").strip()
        }
        structure_prompt = json.dumps({
            "presentonStructureRules": PRESENTON_STRUCTURE_RULES,
            "templateId": template_id,
            "slideCount": len(items),
            "layouts": layout_catalog,
            "outline": items,
            "settings": dict(settings),
            "userInstructions": str(request.get("sharedPrompt") or request.get("instructions") or ""),
        }, ensure_ascii=False)
        timeout_token = set_active_llm_timeout(PPT_PAGE_LLM_TIMEOUT_SECONDS)
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
            reset_active_llm_timeout(timeout_token)
        enriched_slides: List[Dict[str, Any]] = []
        generation_warnings: List[str] = []
        valid_layout_ids = {str(item["id"]) for item in layout_catalog}
        selected_layouts = [
            value if value in valid_layout_ids else _select_layout_fallback(
                layout_catalog, items[index - 1], index, len(items)
            )
            for index, value in enumerate(selected_layouts, start=1)
        ]
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
        if self._embedded_config.enabled:
            try:
                source = self._resolve_source_text(request, user_id)
                enriched_slides = self._generate_presenton_ui_slides(
                    outline=outline,
                    items=items,
                    settings=settings,
                    template_id=template_id,
                    selected_layouts=selected_layouts,
                    layouts_by_id=layouts_by_id,
                    shared_prompt=str(request.get("sharedPrompt") or ""),
                    source=source,
                    llm_config=llm_config,
                    progress_callback=progress_callback,
                )
            except Exception as exc:
                logger.exception("ppt content UI generation failed")
                raise HTTPException(
                    status_code=502,
                    detail="PPT 内容模型未返回符合 Presenton UI Schema 的完整页面，请重试",
                ) from exc
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
                # The renderer receives the complete Presenton UI tree. No
                # application-side slot or coordinate hydration is performed.
                "ui": copy.deepcopy(enriched.get("ui") if isinstance(enriched.get("ui"), Mapping) else {}),
            })
        return {
            "slides": slides,
            "sharedPrompt": str(request.get("sharedPrompt") or "简洁、清晰、适合学生复习"),
            "warnings": generation_warnings,
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
        `/tasks` endpoint still owns HTML/PDF/PPTX rendering, so the client can
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
            "outline": copy.deepcopy(dict(outline)),
            "slides": [],
            "sourceName": str(request.get("sourceName") or "复习资料 PPT"),
            "sharedPrompt": str(request.get("sharedPrompt") or ""),
            "settings": copy.deepcopy(request.get("settings") or {}),
            "error": None,
        }
        with self._lock:
            self._tasks[task_id] = task
            self._task_store.put(task)
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
                currentSlide=total,
                totalSlides=total,
                completedSlides=total,
                remainingSlides=0,
                processingSlides=[],
            )
        except Exception as exc:
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
        progress_callback: Optional[Callable[[Mapping[str, Any]], None]] = None,
    ) -> List[Dict[str, Any]]:
        """Generate one complete Presenton UI tree per slide.

        Presenton generates pages independently. Doing the same here prevents a
        multi-page deck from exceeding the model context with repeated template
        JSON and makes a malformed page fail without corrupting its neighbours.
        """
        def generate_one(index: int) -> Dict[str, Any]:
            layout_id = selected_layouts[index]
            payload = {
                "title": str(outline.get("title") or "复习资料 PPT"),
                "outline": items,
                "currentSlide": items[index],
                "settings": dict(settings),
                "templateId": template_id,
                "selectedLayouts": [{
                    "slideIndex": index + 1,
                    "layoutId": layout_id,
                    "layout": copy.deepcopy(layouts_by_id.get(layout_id) or {}),
                }],
                "presentonContentRules": PRESENTON_CONTENT_RULES,
                "uiOutputInstruction": (
                    "必须完整复制当前 layout 的 components 数组，只修改文本和图表/表格数据；"
                    "禁止修改坐标、尺寸、字体、颜色、SVG、图片路径或组件树；本次只返回当前页。"
                ),
                "sharedPrompt": shared_prompt,
                "sourceMaterial": source[:60_000],
            }
            timeout_token = set_active_llm_timeout(PPT_PAGE_LLM_TIMEOUT_SECONDS)
            content_token = set_active_llm_config(llm_config)
            try:
                content_answer = run_specialist_agent(
                    "ppt_content_agent",
                    json.dumps(payload, ensure_ascii=False),
                    ([{"id": "uploaded-material", "source": "学习资料", "content": source[:60_000]}] if source else []),
                )
            finally:
                reset_active_llm_config(content_token)
                reset_active_llm_timeout(timeout_token)
            normalized = _sanitize_content_payload(
                json.loads(content_answer),
                [layout_id],
                {layout_id: layouts_by_id[layout_id]},
                1,
            )
            return normalized["slides"][0]

        total = len(items)
        completed = 0
        processing: set[int] = set()
        results: Dict[int, Dict[str, Any]] = {}
        max_workers = min(4, max(1, total))

        def report(current: Optional[int] = None) -> None:
            if not progress_callback:
                return
            active = sorted(index + 1 for index in processing)
            next_slide = current or (active[0] if active else min(completed + 1, total))
            progress_callback({
                "stage": "writing",
                "progress": 10 + int(completed * 80 / max(1, total)),
                "currentSlide": next_slide,
                "currentSlideTitle": str(items[next_slide - 1].get("title") or "") if next_slide else "",
                "totalSlides": total,
                "completedSlides": completed,
                "remainingSlides": total - completed,
                "processingSlides": active,
                "message": f"正在生成第 {next_slide} / {total} 页" if next_slide else "正在生成页面内容",
            })

        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {}
            for index in range(total):
                processing.add(index)
                future = executor.submit(generate_one, index)
                futures[future] = index
                if len(processing) >= max_workers:
                    break
            report()
            submitted = len(futures)
            while futures:
                for future in as_completed(list(futures)):
                    index = futures.pop(future)
                    results[index] = future.result()
                    processing.discard(index)
                    completed += 1
                    if submitted < total:
                        processing.add(submitted)
                        next_future = executor.submit(generate_one, submitted)
                        futures[next_future] = submitted
                        submitted += 1
                    report()
                    break
        return [results[index] for index in range(total)]

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
    layouts_by_id: Mapping[str, Mapping[str, Any]],
    expected_count: int,
) -> Dict[str, Any]:
    slides = payload.get("slides") if isinstance(payload, Mapping) else None
    if not isinstance(slides, list) or len(slides) < expected_count:
        raise ValueError(f"ppt_content_agent 返回 {len(slides) if isinstance(slides, list) else 0} 页，期望 {expected_count} 页")
    normalized: List[Dict[str, Any]] = []
    for index in range(expected_count):
        raw = slides[index] if isinstance(slides[index], Mapping) else {}
        item = dict(raw)
        layout = layouts_by_id.get(selected_layouts[index]) or {}
        item["index"] = index + 1
        item["ui"] = _validate_presenton_ui(item.get("ui"), layout)
        normalized.append(item)
    return {"slides": normalized}


def _validate_presenton_ui(value: Any, layout: Mapping[str, Any]) -> Dict[str, Any]:
    """Validate a model-returned UI tree without rebuilding or hydrating it."""
    if not isinstance(value, Mapping):
        raise ValueError("ppt_content_agent 每页必须返回完整 ui JSON")
    expected_items = layout.get("components") or layout.get("elements")
    actual_items = value.get("components") or value.get("elements")
    if not isinstance(expected_items, list) or not isinstance(actual_items, list):
        raise ValueError("Presenton UI 必须包含 components 或 elements 数组")
    if _ui_static_signature(expected_items) != _ui_static_signature(actual_items):
        raise ValueError("Presenton UI 组件树或布局属性被模型修改")
    return copy.deepcopy(dict(value))


def _ui_static_signature(value: Any, parent_type: str = "") -> Any:
    """Return the immutable portion of a Presenton UI tree for comparison."""
    if isinstance(value, list):
        return tuple(_ui_static_signature(item, parent_type) for item in value)
    if not isinstance(value, Mapping):
        return value
    node_type = str(value.get("type") or parent_type or "")
    mutable = {"text", "runs"} if node_type == "text" else set()
    if node_type == "table":
        mutable.update({"columns", "rows"})
    if node_type == "chart":
        mutable.update({"categories", "series", "data", "chartType", "chart_type"})
    entries = []
    for key, raw in sorted(value.items(), key=lambda pair: str(pair[0])):
        key_name = str(key)
        if key_name in mutable:
            # Text content is mutable, but run-level font/alignment metadata is
            # not. Compare the run shape while ignoring only run text values.
            if node_type == "text" and key_name == "runs":
                entries.append((key_name, _ui_text_runs_static_signature(raw)))
            continue
        entries.append((key_name, _ui_static_signature(raw, node_type)))
    return tuple(entries)


def _ui_text_runs_static_signature(value: Any) -> Any:
    if isinstance(value, list):
        return tuple(_ui_text_runs_static_signature(item) for item in value)
    if not isinstance(value, Mapping):
        return value
    return tuple(
        (str(key), _ui_text_runs_static_signature(raw))
        for key, raw in sorted(value.items(), key=lambda pair: str(pair[0]))
        if str(key) != "text"
    )


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
