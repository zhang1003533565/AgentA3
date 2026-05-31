from fastapi import APIRouter, Header, HTTPException
from typing import Optional

from app.models.video_generation import VideoBatchRequest, VideoGenerationRequest, VideoGenerationResponse
from app.video_generation import get_qwen_video_provider

router = APIRouter(prefix="/internal/videos", tags=["internal-videos"])


@router.post("/generate", response_model=VideoGenerationResponse)
def generate_video(
    request: VideoGenerationRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> VideoGenerationResponse:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    return get_qwen_video_provider().generate(request)


@router.post("/batch", response_model=VideoGenerationResponse)
def generate_videos_batch(
    request: VideoBatchRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> VideoGenerationResponse:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    return get_qwen_video_provider().batch(request)


@router.get("/tasks/{task_id}", response_model=VideoGenerationResponse)
def get_video_task(
    task_id: str,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> VideoGenerationResponse:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    return get_qwen_video_provider().get_task(task_id)
