from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException

from app.image_generation import get_qwen_image_provider
from app.models.image_generation import ImageBatchRequest, ImageGenerationRequest, ImageGenerationResponse
from app.security.internal_auth import require_internal_token

router = APIRouter(
    prefix="/internal/images",
    tags=["internal-images"],
    dependencies=[Depends(require_internal_token)],
)


@router.post("/generate", response_model=ImageGenerationResponse)
def generate_image(
    request: ImageGenerationRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> ImageGenerationResponse:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    return get_qwen_image_provider().generate(request)


@router.post("/batch", response_model=ImageGenerationResponse)
def generate_images_batch(
    request: ImageBatchRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> ImageGenerationResponse:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    return get_qwen_image_provider().batch(request)


@router.get("/tasks/{task_id}", response_model=ImageGenerationResponse)
def get_image_task(
    task_id: str,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> ImageGenerationResponse:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    return get_qwen_image_provider().get_task(task_id)
