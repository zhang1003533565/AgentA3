from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field, field_validator


VideoMode = Literal["single", "batch"]
VideoStatus = Literal["pending", "running", "success", "partial_success", "failed"]


class VideoGenerationRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=4000)
    imageUrl: str = Field(default="", max_length=1000)
    size: str = Field(default="1280x720", max_length=32)
    duration: int = Field(default=5, ge=1, le=30)
    seed: Optional[int] = None
    model: Optional[str] = Field(default=None, max_length=64)
    provider: str = Field(default="qwen", max_length=64)
    baseUrl: str = Field(default="", max_length=256)
    apiKey: str = Field(default="", max_length=256)
    metadata: Dict[str, Any] = Field(default_factory=dict)

    @field_validator("size")
    @classmethod
    def validate_size(cls, value: str) -> str:
        normalized = value.strip().lower().replace("*", "x")
        allowed_sizes = {"1280x720", "720x1280", "960x960"}
        if normalized not in allowed_sizes:
            raise ValueError(f"不支持的视频尺寸：{value}，可选：{', '.join(sorted(allowed_sizes))}")
        return normalized


class VideoBatchRequest(VideoGenerationRequest):
    prompts: List[str] = Field(default_factory=list, max_length=8)

    @field_validator("prompts")
    @classmethod
    def validate_prompts(cls, value: List[str]) -> List[str]:
        cleaned = [item.strip() for item in value if item and item.strip()]
        if len(cleaned) > 8:
            raise ValueError("批量提示词最多支持 8 条")
        return cleaned


class VideoItem(BaseModel):
    index: int
    url: str = ""
    status: VideoStatus = "pending"
    seed: Optional[int] = None
    errorMessage: str = ""


class VideoGenerationResponse(BaseModel):
    taskId: str
    providerTaskId: str = ""
    mode: VideoMode
    status: VideoStatus
    prompt: str
    imageUrl: str = ""
    size: str = "1280x720"
    duration: int = 5
    seed: Optional[int] = None
    videos: List[VideoItem] = Field(default_factory=list)
    message: str = ""
    metadata: Dict[str, Any] = Field(default_factory=dict)
