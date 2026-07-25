from typing import Any, Dict, List, Optional
import json
import urllib.error
import urllib.request
from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, Field

from app.model_providers.catalog import get_model_provider_catalog
from app.security.internal_auth import require_internal_token

router = APIRouter(
    prefix="/internal/models",
    tags=["internal-models"],
    dependencies=[Depends(require_internal_token)],
)


@router.get("/providers")
def list_model_providers(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    return get_model_provider_catalog()


class VisionTestRequest(BaseModel):
    provider: str = Field(default="")
    baseUrl: str
    apiKey: str
    model: str
    prompt: str = Field(default="")
    mediaType: str = Field(default="image")
    mediaUrl: str = Field(default="")
    mediaBase64: str = Field(default="")
    mediaMimeType: str = Field(default="")


@router.post("/vision/test")
def vision_test(
    req: VisionTestRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")

    modality = (req.mediaType or "image").strip().lower()
    if modality not in {"image", "video"}:
        raise HTTPException(status_code=400, detail="mediaType 仅支持 image 或 video")

    base_url = (req.baseUrl or "").rstrip("/")
    if not base_url:
        raise HTTPException(status_code=400, detail="baseUrl 不能为空")
    if not req.apiKey.strip():
        raise HTTPException(status_code=400, detail="apiKey 不能为空")
    if not req.model.strip():
        raise HTTPException(status_code=400, detail="model 不能为空")

    media_ref = (req.mediaUrl or "").strip()
    if not media_ref and req.mediaBase64:
        mime = (req.mediaMimeType or "").strip() or ("image/jpeg" if modality == "image" else "video/mp4")
        media_ref = f"data:{mime};base64,{req.mediaBase64.strip()}"

    prompt = (req.prompt or "").strip() or ("请描述图片内容。" if modality == "image" else "请概括视频内容。")
    content: List[Dict[str, Any]] = [{"type": "text", "text": prompt}]
    if media_ref:
        if modality == "image":
            content.append({"type": "image_url", "image_url": {"url": media_ref}})
        else:
            content.append({"type": "video_url", "video_url": {"url": media_ref}})

    payload = {
        "model": req.model.strip(),
        "messages": [{"role": "user", "content": content}],
        "temperature": 0.1,
        "max_tokens": 512,
    }

    target = f"{base_url}/chat/completions"
    headers = {
        "Authorization": f"Bearer {req.apiKey.strip()}",
        "api-key": req.apiKey.strip(),
        "Content-Type": "application/json",
    }
    try:
        req_obj = urllib.request.Request(
            target,
            data=json.dumps(payload).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        with urllib.request.urlopen(req_obj, timeout=90) as response:
            raw_bytes = response.read()
            raw = json.loads(raw_bytes.decode("utf-8") or "{}")
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="ignore")
        raise HTTPException(status_code=502, detail=f"视觉模型调用失败: {exc.code} {error_body[:800]}")
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"视觉模型调用失败: {exc}")

    text = ""
    try:
        text = str(raw.get("choices", [{}])[0].get("message", {}).get("content", "")).strip()
    except Exception:
        text = ""
    if not text:
        text = str(raw)[:800]
    return {
        "success": True,
        "detail": f"模型返回：{text}",
        "raw": raw,
        "target": target,
        "modality": "vision",
        "mediaType": modality,
        "model": req.model.strip(),
        "prompt": prompt,
    }
