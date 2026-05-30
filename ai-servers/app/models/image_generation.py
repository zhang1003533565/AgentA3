from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field, field_validator


ImageMode = Literal["single", "batch"]
ImageStatus = Literal["pending", "running", "success", "partial_success", "failed"]
ImageResultType = Literal["url", "base64", "url_and_base64"]


class ImageGenerationRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=4000)
    style: str = Field(default="", max_length=64)
    size: str = Field(default="1664x928", max_length=32)
    count: int = Field(default=1, ge=1, le=8)
    seed: Optional[int] = None
    negativePrompt: str = Field(default="", max_length=1000)
    returnType: ImageResultType = "url"
    promptExtend: bool = True
    watermark: bool = False
    model: Optional[str] = Field(default=None, max_length=64)
    metadata: Dict[str, Any] = Field(default_factory=dict)

    @field_validator("size")
    @classmethod
    def validate_size(cls, value: str) -> str:
        normalized = value.strip().lower().replace("*", "x")
        allowed_sizes = {"1664x928", "1472x1104", "1328x1328", "1104x1472", "928x1664"}
        if normalized not in allowed_sizes:
            raise ValueError(f"不支持的图片尺寸：{value}，可选：{', '.join(sorted(allowed_sizes))}")
        return normalized


class ImageBatchRequest(ImageGenerationRequest):
    prompts: List[str] = Field(default_factory=list, max_length=8)

    @field_validator("prompts")
    @classmethod
    def validate_prompts(cls, value: List[str]) -> List[str]:
        cleaned = [item.strip() for item in value if item and item.strip()]
        if len(cleaned) > 8:
            raise ValueError("批量提示词最多支持 8 条")
        return cleaned


class ImageItem(BaseModel):
    index: int
    url: str = ""
    base64: str = ""
    status: ImageStatus = "pending"
    seed: Optional[int] = None
    errorMessage: str = ""


class ImageGenerationResponse(BaseModel):
    taskId: str
    providerTaskId: str = ""
    mode: ImageMode
    status: ImageStatus
    prompt: str
    style: str = ""
    size: str = "1024x1024"
    count: int = 1
    seed: Optional[int] = None
    negativePrompt: str = ""
    images: List[ImageItem] = Field(default_factory=list)
    message: str = ""
    metadata: Dict[str, Any] = Field(default_factory=dict)
