import asyncio
import base64
import binascii
import json
import time
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import Response, StreamingResponse
from pydantic import BaseModel, Field

from app.model_providers.runtime_config import build_llm_runtime_config
from app.ppt_generation.service import ppt_generation_service


router = APIRouter(prefix="/internal/rag/ppt-generation", tags=["internal-ppt-generation"])


class OutlineRequest(BaseModel):
    sourceName: str = Field(min_length=1, max_length=255)
    sourceContent: str = Field(default="", max_length=200000)
    sourceFileId: str = Field(default="", max_length=80)
    outlineMode: str = Field(default="ai_outline", max_length=32)
    pageCount: int = Field(default=15, ge=3, le=50)
    scene: str = Field(default="review", max_length=32)
    topic: str = Field(default="", max_length=200)


class SlidesRequest(BaseModel):
    outline: Dict[str, Any]
    sourceContent: str = Field(default="", max_length=200000)
    sourceFileId: str = Field(default="", max_length=80)
    settings: Dict[str, Any] = Field(default_factory=dict)
    sharedPrompt: str = Field(default="", max_length=2000)


class TaskRequest(BaseModel):
    sourceName: str = Field(min_length=1, max_length=255)
    outline: Dict[str, Any]
    slides: List[Dict[str, Any]] = Field(min_length=2, max_length=50)
    sharedPrompt: str = Field(default="", max_length=2000)
    settings: Dict[str, Any] = Field(default_factory=dict)
    exportFormats: List[str] = Field(default_factory=lambda: ["pptx"])


class FileUploadRequest(BaseModel):
    fileName: str = Field(min_length=1, max_length=255)
    contentType: str = Field(default="application/octet-stream", max_length=120)
    contentBase64: str = Field(min_length=1, max_length=36_000_000)


class SlideImageRequest(BaseModel):
    imageBase64: str = Field(min_length=1, max_length=12_000_000)
    extension: str = Field(default="png", max_length=8)


def _identity(authorization: Optional[str], user_id: Optional[str]) -> str:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或Token无效")
    value = str(user_id or "").strip()
    if not value:
        raise HTTPException(status_code=401, detail="缺少可信用户身份")
    return value


def _llm_config(provider, base_url, api_key, model):
    return build_llm_runtime_config(provider=provider, base_url=base_url, api_key=api_key, model=model)


@router.get("/options")
def get_options(authorization: Optional[str] = Header(None),
                x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    _identity(authorization, x_user_id)
    return ppt_generation_service.get_options()


@router.get("/templates/{template_id}/thumbnail")
def get_template_thumbnail(template_id: str, authorization: Optional[str] = Header(None),
                           x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    _identity(authorization, x_user_id)
    if not template_id or len(template_id) > 120:
        raise HTTPException(status_code=400, detail="模板编号无效")
    content, content_type = ppt_generation_service.get_template_thumbnail(template_id)
    return Response(
        content=content,
        media_type=content_type,
        headers={"Cache-Control": "private, max-age=300"},
    )


@router.post("/files")
def upload_source_file(request: FileUploadRequest,
                       authorization: Optional[str] = Header(None),
                       x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    user_id = _identity(authorization, x_user_id)
    try:
        content = base64.b64decode(request.contentBase64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(status_code=422, detail="上传文件编码无效") from exc
    return ppt_generation_service.upload_source_file(
        user_id,
        request.fileName,
        request.contentType,
        content,
    )


@router.post("/outlines")
def generate_outline(request: OutlineRequest, authorization: Optional[str] = Header(None),
                     x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
                     provider: Optional[str] = Header(None, alias="X-AI-Provider"),
                     base_url: Optional[str] = Header(None, alias="X-AI-Base-Url"),
                     api_key: Optional[str] = Header(None, alias="X-AI-Api-Key"),
                     model: Optional[str] = Header(None, alias="X-AI-Model")):
    user_id = _identity(authorization, x_user_id)
    return ppt_generation_service.generate_outline(
        request.model_dump(),
        _llm_config(provider, base_url, api_key, model),
        user_id,
    )


@router.post("/slides")
def generate_slides(request: SlidesRequest, authorization: Optional[str] = Header(None),
                    x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
                    provider: Optional[str] = Header(None, alias="X-AI-Provider"),
                    base_url: Optional[str] = Header(None, alias="X-AI-Base-Url"),
                    api_key: Optional[str] = Header(None, alias="X-AI-Api-Key"),
                    model: Optional[str] = Header(None, alias="X-AI-Model")):
    user_id = _identity(authorization, x_user_id)
    return ppt_generation_service.generate_slides(
        request.model_dump(),
        _llm_config(provider, base_url, api_key, model),
        user_id,
    )


@router.post("/slides/tasks")
def create_slides_task(request: SlidesRequest, authorization: Optional[str] = Header(None),
                       x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
                       provider: Optional[str] = Header(None, alias="X-AI-Provider"),
                       base_url: Optional[str] = Header(None, alias="X-AI-Base-Url"),
                       api_key: Optional[str] = Header(None, alias="X-AI-Api-Key"),
                       model: Optional[str] = Header(None, alias="X-AI-Model")):
    user_id = _identity(authorization, x_user_id)
    return ppt_generation_service.create_slides_task(
        user_id,
        request.model_dump(),
        _llm_config(provider, base_url, api_key, model),
    )


@router.post("/tasks")
def create_task(request: TaskRequest, authorization: Optional[str] = Header(None),
                x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
                provider: Optional[str] = Header(None, alias="X-AI-Provider"),
                base_url: Optional[str] = Header(None, alias="X-AI-Base-Url"),
                api_key: Optional[str] = Header(None, alias="X-AI-Api-Key"),
                model: Optional[str] = Header(None, alias="X-AI-Model")):
    user_id = _identity(authorization, x_user_id)
    return ppt_generation_service.create_task(user_id, request.model_dump(), _llm_config(provider, base_url, api_key, model))


@router.get("/tasks/{task_id}")
def get_task(task_id: str, authorization: Optional[str] = Header(None),
             x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    return ppt_generation_service.get_task(_identity(authorization, x_user_id), task_id)


@router.post("/tasks/{task_id}/cancel")
def cancel_task(task_id: str, authorization: Optional[str] = Header(None),
                x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    return ppt_generation_service.cancel_task(_identity(authorization, x_user_id), task_id)


@router.post("/tasks/{task_id}/retry")
def retry_task(task_id: str, authorization: Optional[str] = Header(None),
               x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
               provider: Optional[str] = Header(None, alias="X-AI-Provider"),
               base_url: Optional[str] = Header(None, alias="X-AI-Base-Url"),
               api_key: Optional[str] = Header(None, alias="X-AI-Api-Key"),
               model: Optional[str] = Header(None, alias="X-AI-Model")):
    return ppt_generation_service.retry_task(
        _identity(authorization, x_user_id),
        task_id,
        _llm_config(provider, base_url, api_key, model),
    )


@router.post("/tasks/{task_id}/slides/{slide_index}/image")
def replace_slide_image(task_id: str, slide_index: int, request: SlideImageRequest,
                        authorization: Optional[str] = Header(None),
                        x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    user_id = _identity(authorization, x_user_id)
    try:
        encoded = request.imageBase64.split(",", 1)[-1]
        content = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(status_code=422, detail="图片编码无效") from exc
    return ppt_generation_service.replace_slide_image(
        user_id,
        task_id,
        slide_index,
        content,
        request.extension,
    )


@router.get("/tasks/{task_id}/stream")
async def stream_task(task_id: str, authorization: Optional[str] = Header(None),
                      x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    user_id = _identity(authorization, x_user_id)

    async def events():
        last = None
        last_heartbeat = time.monotonic()
        while True:
            task = ppt_generation_service.get_task(user_id, task_id)
            marker = (
                task["status"],
                task["progress"],
                task["stage"],
                task.get("currentSlide"),
                task.get("completedSlides"),
                task.get("remainingSlides"),
                tuple(task.get("processingSlides") or []),
            )
            if marker != last:
                yield f"event: {task['stage']}\ndata: {json.dumps(task, ensure_ascii=False)}\n\n"
                last = marker
                last_heartbeat = time.monotonic()
            elif time.monotonic() - last_heartbeat >= 15:
                yield ": keepalive\n\n"
                last_heartbeat = time.monotonic()
            if task["status"] in {"completed", "failed", "cancelled"}:
                break
            await asyncio.sleep(0.4)
    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache"})


def _artifact_response(export_file):
    def chunks():
        try:
            for chunk in iter(lambda: export_file.stream.read(1024 * 1024), b""):
                yield chunk
        finally:
            export_file.stream.close()
    return StreamingResponse(chunks(), media_type=export_file.mime_type, headers={
        "Content-Length": str(export_file.size),
        "Content-Disposition": f'attachment; filename="{export_file.storage_key}"',
        "Cache-Control": "private, no-store",
    })


@router.get("/tasks/{task_id}/files/{file_format}")
def download_file(task_id: str, file_format: str, authorization: Optional[str] = Header(None),
                  x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    if file_format not in {"pptx", "pdf"}:
        raise HTTPException(status_code=400, detail="仅支持下载 pptx 或 pdf")
    return _artifact_response(ppt_generation_service.open_artifact(_identity(authorization, x_user_id), task_id, file_format))


@router.get("/tasks/{task_id}/previews/{slide_index}")
def download_preview(task_id: str, slide_index: int, authorization: Optional[str] = Header(None),
                     x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    if slide_index < 1:
        raise HTTPException(status_code=400, detail="预览页码必须大于 0")
    return _artifact_response(ppt_generation_service.open_artifact(_identity(authorization, x_user_id), task_id, "preview", slide_index))
