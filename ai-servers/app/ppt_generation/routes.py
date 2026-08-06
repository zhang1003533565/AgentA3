import asyncio
import json
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.model_providers.runtime_config import build_llm_runtime_config
from app.ppt_generation.service import ppt_generation_service


router = APIRouter(prefix="/internal/rag/ppt-generation", tags=["internal-ppt-generation"])


class OutlineRequest(BaseModel):
    sourceName: str = Field(min_length=1, max_length=255)
    sourceContent: str = Field(min_length=1, max_length=200000)
    outlineMode: str = Field(default="ai_outline", max_length=32)
    pageCount: int = Field(default=15, ge=3, le=50)
    scene: str = Field(default="review", max_length=32)
    topic: str = Field(default="", max_length=200)


class SlidesRequest(BaseModel):
    outline: Dict[str, Any]
    sourceContent: str = Field(default="", max_length=200000)
    settings: Dict[str, Any] = Field(default_factory=dict)
    sharedPrompt: str = Field(default="", max_length=2000)


class TaskRequest(BaseModel):
    sourceName: str = Field(min_length=1, max_length=255)
    outline: Dict[str, Any]
    slides: List[Dict[str, Any]] = Field(min_length=2, max_length=50)
    sharedPrompt: str = Field(default="", max_length=2000)
    settings: Dict[str, Any] = Field(default_factory=dict)
    exportFormats: List[str] = Field(default_factory=lambda: ["pptx"])


def _identity(authorization: Optional[str], user_id: Optional[str]) -> str:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或Token无效")
    value = str(user_id or "").strip()
    if not value:
        raise HTTPException(status_code=401, detail="缺少可信用户身份")
    return value


def _llm_config(provider, base_url, api_key, model):
    return build_llm_runtime_config(provider=provider, base_url=base_url, api_key=api_key, model=model)


@router.post("/outlines")
def generate_outline(request: OutlineRequest, authorization: Optional[str] = Header(None),
                     x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
                     provider: Optional[str] = Header(None, alias="X-AI-Provider"),
                     base_url: Optional[str] = Header(None, alias="X-AI-Base-Url"),
                     api_key: Optional[str] = Header(None, alias="X-AI-Api-Key"),
                     model: Optional[str] = Header(None, alias="X-AI-Model")):
    _identity(authorization, x_user_id)
    return ppt_generation_service.generate_outline(request.model_dump(), _llm_config(provider, base_url, api_key, model))


@router.post("/slides")
def generate_slides(request: SlidesRequest, authorization: Optional[str] = Header(None),
                    x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
                    provider: Optional[str] = Header(None, alias="X-AI-Provider"),
                    base_url: Optional[str] = Header(None, alias="X-AI-Base-Url"),
                    api_key: Optional[str] = Header(None, alias="X-AI-Api-Key"),
                    model: Optional[str] = Header(None, alias="X-AI-Model")):
    _identity(authorization, x_user_id)
    return ppt_generation_service.generate_slides(request.model_dump(), _llm_config(provider, base_url, api_key, model))


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


@router.get("/tasks/{task_id}/stream")
async def stream_task(task_id: str, authorization: Optional[str] = Header(None),
                      x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    user_id = _identity(authorization, x_user_id)

    async def events():
        last = None
        while True:
            task = ppt_generation_service.get_task(user_id, task_id)
            marker = (task["status"], task["progress"], task["stage"])
            if marker != last:
                yield f"event: {task['stage']}\ndata: {json.dumps(task, ensure_ascii=False)}\n\n"
                last = marker
            if task["status"] in {"completed", "failed"}:
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
