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
from app.ppt_generation.template_preview import (
    get_template_layout_preview,
    warm_up_previews,
)
from app.rag.document_conversion import generated_exporter


_PAGE_HEADING = re.compile(r"###\s*第\s*(\d+)\s*页", re.IGNORECASE)
_FIELD = re.compile(r"^-\s*([^：:]+)[：:]\s*(.*)$")
logger = logging.getLogger(__name__)
PPT_PAGE_LLM_TIMEOUT_SECONDS = max(60, int(os.getenv("PPT_PAGE_LLM_TIMEOUT_SECONDS") or 300))
PPT_CONTENT_BATCH_SIZE = max(1, min(5, int(os.getenv("PPT_CONTENT_BATCH_SIZE") or 3)))
PPT_LLM_MAX_RETRIES = max(0, min(5, int(os.getenv("PPT_LLM_MAX_RETRIES") or 3)))
PPT_LLM_RETRY_BASE_DELAY = max(0.5, float(os.getenv("PPT_LLM_RETRY_BASE_DELAY") or 1.0))


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
        self._start_template_preview_warmup()

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
                markdown = _retry_llm_call(
                    lambda: run_specialist_agent("ppt_outline_agent", prompt, evidence)
                )
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
            batch_layout_ids = [selected_layouts[i] for i in indices]
            unique_layouts: List[Dict[str, Any]] = []
            seen: set[str] = set()
            batch_layouts_by_id: Dict[str, Dict[str, Any]] = {}
            for i in indices:
                lid = selected_layouts[i]
                if lid not in seen:
                    seen.add(lid)
                    batch_layouts_by_id[lid] = copy.deepcopy(layouts_by_id.get(lid) or {})
                unique_layouts.append({
                    "slideIndex": i + 1,
                    "layoutId": lid,
                    "componentSchema": self._template_catalog._component_schema(
                        batch_layouts_by_id[lid].get("components") or []
                    ),
                })
            first, last = indices[0] + 1, indices[-1] + 1
            count = len(indices)
            payload = {
                "title": str(outline.get("title") or "复习资料 PPT"),
                "outline": items,
                "currentSlides": [items[i] for i in indices],
                "settings": dict(settings),
                "templateId": template_id,
                "selectedLayouts": unique_layouts,
                "presentonContentRules": PRESENTON_CONTENT_RULES,
                "uiOutputInstruction": (
                    f"本次返回第{first}到第{last}页，共{count}页。"
                    "不要返回完整的 layout JSON，只返回 componentContent：每页一个扁平映射，"
                    "键为组件 name，值为文本字符串（表格用 {columns:[...], rows:[...]}，"
                    "图表用 {categories:[...], series:[...]}）。"
                ),
                "sharedPrompt": shared_prompt,
                "sourceMaterial": _source_segment(source, indices[0], total),
            }

            def _call() -> str:
                timeout_token = set_active_llm_timeout(PPT_PAGE_LLM_TIMEOUT_SECONDS)
                content_token = set_active_llm_config(llm_config)
                batch_source = _source_segment(source, indices[0], total)
                try:
                    return run_specialist_agent(
                        "ppt_content_agent",
                        json.dumps(payload, ensure_ascii=False),
                        (
                            [{"id": "uploaded-material", "source": "学习资料", "content": batch_source}]
                            if source else []
                        ),
                    )
                finally:
                    reset_active_llm_config(content_token)
                    reset_active_llm_timeout(timeout_token)

            content_answer = _retry_llm_call(_call)
            normalized = _sanitize_content_payload(
                json.loads(content_answer),
                batch_layout_ids,
                batch_layouts_by_id,
                len(indices),
                [items[i] for i in indices],
            )
            return normalized["slides"]

        completed = 0
        results: Dict[int, Dict[str, Any]] = {}
        active_batches: set[int] = set()

        def report(current: Optional[int] = None) -> None:
            if not progress_callback:
                return
            next_slide = current or min(
                (i for i in range(total) if i not in results),
                default=min(completed + 1, total),
            )
            progress_callback({
                "stage": "writing",
                "progress": 10 + int(completed * 80 / max(1, total)),
                "currentSlide": next_slide + 1,
                "currentSlideTitle": str(items[next_slide].get("title") or "") if next_slide < total else "",
                "totalSlides": total,
                "completedSlides": completed,
                "remainingSlides": total - completed,
                "processingSlides": sorted(
                    i + 1
                    for batch_idx in active_batches
                    for i in batches[batch_idx]
                ),
                "message": (
                    f"正在生成第 {next_slide + 1} / {total} 页"
                    if next_slide < total
                    else "正在生成页面内容"
                ),
            })

        max_workers = min(2, max(1, len(batches)))
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            future_to_batch: Dict[Any, int] = {}
            for batch_idx in range(min(max_workers, len(batches))):
                active_batches.add(batch_idx)
                future_to_batch[executor.submit(_generate_batch, batches[batch_idx])] = batch_idx
            report()
            submitted = len(future_to_batch)
            while future_to_batch:
                for future in as_completed(list(future_to_batch)):
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
        item["index"] = index + 1
        component_content = item.pop("componentContent", None)
        outline_item = current_slides[index] if current_slides and index < len(current_slides) else {}
        if isinstance(component_content, dict) and component_content:
            item["ui"] = _merge_content_into_layout(layout, component_content)
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
    # 容错键表：归一化键 -> 原始键（精确匹配优先，不覆盖）
    fuzzy_keys: Dict[str, str] = {}
    for key in component_content:
        normalized_key = _normalize_component_key(key)
        if normalized_key and normalized_key not in fuzzy_keys:
            fuzzy_keys[normalized_key] = key
    matched_names: set = set()

    def _lookup(name: str):
        if name in component_content:
            return component_content[name]
        normalized_name = _normalize_component_key(name)
        if normalized_name and normalized_name in fuzzy_keys:
            return component_content[fuzzy_keys[normalized_name]]
        return None

    def _merge(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                _merge(item)
            return
        if not isinstance(node, dict):
            return
        name = str(node.get("name") or "")
        content = _lookup(name) if name else None
        if name and content is not None:
            matched_names.add(name)
            if isinstance(content, str):
                _set_text_node_content(node, content)
            elif isinstance(content, dict):
                node_type = str(node.get("type") or "")
                if node_type == "text":
                    _set_text_node_content(
                        node,
                        content.get("text") or content.get("value") or content.get("content") or "",
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
                else:
                    node.update({k: v for k, v in content.items() if k != "name"})
        for key in ("components", "elements", "children"):
            if key in node:
                _merge(node[key])
        if "child" in node:
            _merge(node["child"])

    for key in ("components", "elements"):
        if key in result:
            _merge(result[key])

    if not matched_names:
        logger.warning(
            "componentContent keys %s matched no layout component; layout=%s",
            sorted(str(k) for k in component_content)[:10],
            sorted(matched_names),
        )
    else:
        _clear_template_placeholder_text(result, matched_names)
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
    return str(text or "")


def _is_template_placeholder_text(node: Mapping[str, Any]) -> bool:
    """识别模板自带的占位文本（模板原件不含中文：非中文文本即残留）。"""
    text = _node_display_text(node)
    return bool(text.strip()) and not re.search(r"[\u4e00-\u9fff]", text)


def _clear_template_placeholder_text(root: Dict[str, Any], matched_names: set) -> None:
    """合并后清空未命中组件上的英文模板占位文本。"""

    def _walk(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                _walk(item)
            return
        if not isinstance(node, dict):
            return
        if str(node.get("type") or "") == "text":
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


def _set_text_node_content(node: Dict[str, Any], content: Any) -> None:
    text = _compact_text(str(content or ""), node.get("max_length"))
    node["text"] = text
    runs = node.get("runs")
    if isinstance(runs, list) and runs:
        normalized_runs: List[Dict[str, Any]] = []
        for index, run in enumerate(runs):
            next_run = dict(run) if isinstance(run, Mapping) else {}
            next_run["text"] = text if index == 0 else ""
            normalized_runs.append(next_run)
        node["runs"] = normalized_runs


def _compact_text(value: str, max_length: Any = None) -> str:
    text = re.sub(r"[ \t]+", " ", str(value or "").replace("\r", "\n")).strip()
    text = re.sub(r"\n{3,}", "\n\n", text)
    try:
        limit = int(max_length or 0)
    except (TypeError, ValueError):
        limit = 0
    if limit <= 0 or len(text) <= limit:
        return text
    candidate = text[:limit].rstrip()
    sentence_end = max(candidate.rfind("。"), candidate.rfind("！"), candidate.rfind("？"), candidate.rfind("."), candidate.rfind(";"), candidate.rfind("；"))
    if sentence_end >= max(8, int(limit * 0.55)):
        return candidate[:sentence_end + 1].rstrip()
    punctuation = max(candidate.rfind("，"), candidate.rfind(","), candidate.rfind("、"), candidate.rfind(" "))
    if punctuation >= max(8, int(limit * 0.55)):
        return candidate[:punctuation].rstrip()
    if limit <= 1:
        return candidate[:limit]
    return f"{candidate[:limit - 1].rstrip()}…"


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
    """收集版式树中所有 text 类型节点。"""
    found: List[Dict[str, Any]] = []

    def _walk(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                _walk(item)
            return
        if not isinstance(node, dict):
            return
        if str(node.get("type") or "") == "text":
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

    规则：字号最大的文本位放标题，次大的放要点正文（• 分条），
    其余文本位清空英文模板占位。保证输出永远是用户资料内容，
    而不是模板原件。
    """
    result = copy.deepcopy(dict(layout))
    title = str(
        slide_item.get("title")
        or outline_item.get("title")
        or f"第 {slide_item.get('index') or outline_item.get('index') or ''} 页"
    ).strip()

    points = slide_item.get("content")
    if not isinstance(points, list) or not points:
        points = outline_item.get("keyPoints") or outline_item.get("content") or []
    if isinstance(points, str):
        points = [line.strip(" -*•") for line in points.splitlines() if line.strip(" -*•")]
    points = [str(point).strip() for point in points if str(point).strip()][:6]
    body_text = "\n".join(f"• {point}" for point in points) or str(slide_item.get("objective") or outline_item.get("objective") or "").strip()

    text_nodes = _collect_text_nodes(result)
    text_nodes.sort(key=_text_font_size, reverse=True)
    if text_nodes:
        _set_text_node_content(text_nodes[0], title)

    def _capacity(node: Mapping[str, Any]) -> int:
        try:
            return int(node.get("max_length") or 0)
        except (TypeError, ValueError):
            return 0

    # 容量过小的组件（如头像 initials）不参与正文分摊，直接清空占位
    body_nodes = [n for n in text_nodes[1:] if _capacity(n) == 0 or _capacity(n) >= 8]
    body_ids = {id(n) for n in body_nodes}
    tiny_nodes = [n for n in text_nodes[1:] if id(n) not in body_ids]
    for node in tiny_nodes:
        if _is_template_placeholder_text(node):
            _set_text_node_content(node, "")
    if len(body_nodes) == 1:
        if body_text:
            _set_text_node_content(body_nodes[0], body_text)
    elif body_nodes:
        # 多个正文位：要点逐个分摊，多余要点并入首个正文位
        for node, point in zip(body_nodes, points):
            _set_text_node_content(node, f"• {point}")
        if len(points) > len(body_nodes):
            extras = points[len(body_nodes) - 1:]
            merged = _node_display_text(body_nodes[0]) + "\n" + "\n".join(f"• {p}" for p in extras)
            _set_text_node_content(body_nodes[0], merged)
        elif not points and body_text:
            _set_text_node_content(body_nodes[0], body_text)
        # 未分摊到要点的正文位清掉模板占位
        for node in body_nodes[len(points):] if points else body_nodes[1:]:
            if _is_template_placeholder_text(node):
                _set_text_node_content(node, "")
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


def _retry_llm_call(fn, max_retries: int = PPT_LLM_MAX_RETRIES, base_delay: float = PPT_LLM_RETRY_BASE_DELAY):
    """Call fn() with exponential backoff on transient LLM errors."""
    last_exc: Optional[Exception] = None
    for attempt in range(max_retries):
        try:
            return fn()
        except HTTPException as exc:
            if exc.status_code in {429, 502, 503, 504} and attempt < max_retries - 1:
                delay = base_delay * (2 ** attempt)
                logger.warning("LLM call failed (attempt %d/%d), retrying in %.1fs: %s", attempt + 1, max_retries, delay, _safe_error_message(exc))
                time.sleep(delay)
                last_exc = exc
            else:
                raise
        except Exception as exc:
            if attempt < max_retries - 1:
                delay = base_delay * (2 ** attempt)
                logger.warning("LLM call failed (attempt %d/%d), retrying in %.1fs: %s", attempt + 1, max_retries, delay, _safe_error_message(exc))
                time.sleep(delay)
                last_exc = exc
            else:
                raise
    raise last_exc  # type: ignore[misc]


def _safe_error_message(error: Exception) -> str:
    message = re.sub(r"(?i)(api[-_ ]?key|authorization|token)\s*[:=]\s*\S+", r"\1=[已隐藏]", str(error or ""))
    message = re.sub(r"https?://[^\s]+", "[模型服务地址]", message)
    return (message.strip() or error.__class__.__name__)[:300]
