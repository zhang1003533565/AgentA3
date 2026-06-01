import base64
import json
import os
import time
import urllib.error
import urllib.request
import uuid
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.models.image_generation import (
    ImageBatchRequest,
    ImageGenerationRequest,
    ImageGenerationResponse,
    ImageItem,
)


class QwenImageProvider:
    """DashScope/Qwen text-to-image provider based on async image synthesis tasks."""

    def __init__(self) -> None:
        self.api_key = os.getenv("DASHSCOPE_API_KEY") or os.getenv("QWEN_IMAGE_API_KEY") or ""
        self.base_url = os.getenv("QWEN_IMAGE_BASE_URL", "https://dashscope.aliyuncs.com").rstrip("/")
        self.model = os.getenv("QWEN_IMAGE_MODEL", "qwen-image-plus")
        self.timeout_seconds = float(os.getenv("QWEN_IMAGE_TIMEOUT_SECONDS", "30"))
        self.poll_interval_seconds = float(os.getenv("QWEN_IMAGE_POLL_INTERVAL_SECONDS", "2"))
        self.max_poll_attempts = int(os.getenv("QWEN_IMAGE_MAX_POLL_ATTEMPTS", "60"))
        self.tasks: Dict[str, Dict[str, Any]] = {}
        self.completed_tasks: Dict[str, ImageGenerationResponse] = {}

    def generate(self, request: ImageGenerationRequest) -> ImageGenerationResponse:
        if request.count > 1:
            batch_request = ImageBatchRequest(**request.model_dump(), prompts=[request.prompt] * request.count)
            return self.batch(batch_request)
        task_id = self._local_task_id()
        provider_task_id = self._submit_task(request)
        self._remember_task(task_id, provider_task_id, request, "single")
        response = self.get_task(provider_task_id, request=request, task_id=task_id, mode="single")
        response.taskId = task_id
        return response

    def batch(self, request: ImageBatchRequest) -> ImageGenerationResponse:
        prompts = request.prompts or [request.prompt]
        if len(prompts) == 1:
            single = ImageGenerationRequest(**request.model_dump(exclude={"prompts"}))
            single.count = 1
            response = self.generate(single)
            response.mode = "single"
            return response

        images: List[ImageItem] = []
        provider_task_ids: List[str] = []
        first_error = ""
        for index, prompt in enumerate(prompts):
            single_payload = request.model_dump(exclude={"prompts"})
            single_payload["prompt"] = prompt
            single_payload["count"] = 1
            try:
                single_response = self.generate(ImageGenerationRequest(**single_payload))
                provider_task_ids.append(single_response.providerTaskId)
                if single_response.images:
                    image = single_response.images[0]
                    image.index = index
                    images.append(image)
                else:
                    images.append(ImageItem(index=index, status="failed", seed=request.seed, errorMessage="未返回图片结果"))
            except Exception as exc:
                first_error = first_error or str(exc)
                images.append(ImageItem(index=index, status="failed", seed=request.seed, errorMessage=str(exc)))

        success_count = sum(1 for item in images if item.status == "success")
        status = "success" if success_count == len(images) else ("partial_success" if success_count else "failed")
        task_id = self._local_task_id()
        response = ImageGenerationResponse(
            taskId=task_id,
            providerTaskId=",".join(provider_task_ids),
            mode="batch",
            status=status,
            prompt=request.prompt,
            style=request.style,
            size=request.size,
            count=len(prompts),
            seed=request.seed,
            negativePrompt=request.negativePrompt,
            images=images,
            message="批量生成完成" if success_count else (first_error or "批量生成失败"),
            metadata={**request.metadata, "prompts": prompts},
        )
        self.completed_tasks[task_id] = response
        return response

    def get_task(
        self,
        provider_task_id: str,
        request: Optional[ImageGenerationRequest] = None,
        task_id: Optional[str] = None,
        mode: str = "single",
    ) -> ImageGenerationResponse:
        if provider_task_id in self.completed_tasks:
            return self.completed_tasks[provider_task_id]

        task_record = self.tasks.get(provider_task_id)
        if task_record:
            task_id = task_id or provider_task_id
            provider_task_id = task_record["providerTaskId"]
            mode = task_record.get("mode", mode)
            if request is None:
                request = ImageGenerationRequest(**task_record["request"])

        self._ensure_configured(request.apiKey if request else "")
        last_payload: Dict[str, Any] = {}
        for _ in range(self.max_poll_attempts):
            payload = self._request(
                "GET",
                f"/api/v1/tasks/{provider_task_id}",
                api_key=request.apiKey if request else "",
                base_url=request.baseUrl if request else "",
            )
            last_payload = payload
            task_status = str(payload.get("output", {}).get("task_status", "")).upper()
            if task_status in {"SUCCEEDED", "FAILED", "CANCELED", "UNKNOWN"}:
                return self._build_response(provider_task_id, payload, request=request, task_id=task_id, mode=mode)
            time.sleep(self.poll_interval_seconds)
        return self._build_response(provider_task_id, last_payload, request=request, task_id=task_id, mode=mode, timeout=True)

    def _submit_task(self, request: ImageGenerationRequest) -> str:
        self._ensure_configured(request.apiKey)
        final_prompt = self._compose_prompt(request)
        model_id = request.model or self.model
        if self._is_wan_image_model(model_id):
            wan_size = self._normalize_wan_size(request.size, model_id)
            payload = {
                "model": model_id,
                "input": {
                    "messages": [
                        {
                            "role": "user",
                            "content": [
                                {"text": final_prompt},
                            ],
                        }
                    ],
                },
                "parameters": {
                    "size": wan_size,
                    "n": 1,
                    "enable_interleave": True,
                    "watermark": request.watermark,
                },
            }
            endpoint = "/api/v1/services/aigc/image-generation/generation"
        else:
            payload = {
                "model": model_id,
                "input": {
                    "prompt": final_prompt,
                },
                "parameters": {
                    "size": request.size.replace("x", "*"),
                    "n": 1,
                    "prompt_extend": request.promptExtend,
                    "watermark": request.watermark,
                },
            }
            endpoint = "/api/v1/services/aigc/text2image/image-synthesis"
        if request.seed is not None:
            payload["parameters"]["seed"] = request.seed
        if request.negativePrompt:
            payload["parameters"]["negative_prompt"] = request.negativePrompt

        response = self._request(
            "POST",
            endpoint,
            body=payload,
            api_key=request.apiKey,
            base_url=request.baseUrl,
        )
        provider_task_id = response.get("output", {}).get("task_id")
        if not provider_task_id:
            raise HTTPException(status_code=502, detail=f"Qwen 图片任务创建失败：{response}")
        return provider_task_id

    def _request(
        self,
        method: str,
        path: str,
        body: Optional[Dict[str, Any]] = None,
        api_key: str = "",
        base_url: str = "",
    ) -> Dict[str, Any]:
        headers = {
            "Authorization": f"Bearer {self._active_api_key(api_key)}",
            "Content-Type": "application/json",
            "X-DashScope-Async": "enable",
        }
        url = f"{self._active_base_url(base_url)}{path}"
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            error_body = exc.read().decode("utf-8", errors="ignore")
            raise HTTPException(status_code=exc.code, detail=f"Qwen 图片服务返回错误：{error_body}") from exc
        except urllib.error.URLError as exc:
            raise HTTPException(status_code=502, detail=f"Qwen 图片服务请求失败：{exc}") from exc

    def _build_response(
        self,
        provider_task_id: str,
        payload: Dict[str, Any],
        request: Optional[ImageGenerationRequest],
        task_id: Optional[str],
        mode: str,
        timeout: bool = False,
    ) -> ImageGenerationResponse:
        output = payload.get("output", {}) if payload else {}
        provider_status = str(output.get("task_status", "")).upper()
        message = output.get("message") or payload.get("message") or ""
        if timeout:
            status = "running"
            message = message or "图片任务仍在生成中"
        elif provider_status == "SUCCEEDED":
            status = "success"
            message = message or "生成完成"
        elif provider_status in {"FAILED", "CANCELED", "UNKNOWN"}:
            status = "failed"
            message = message or f"图片生成失败：{provider_status}"
        else:
            status = "running"
            message = message or "图片任务处理中"

        results = output.get("results") or []
        if not results:
            results = self._extract_results_from_choices(output)
        images = [
            ImageItem(
                index=index,
                url=item.get("url", ""),
                base64=self._download_base64(item.get("url", "")) if request and request.returnType in {"base64", "url_and_base64"} else "",
                status="success" if item.get("url") else status,
                seed=request.seed if request else None,
                errorMessage="" if item.get("url") else message,
            )
            for index, item in enumerate(results)
        ]
        if status == "failed" and not images:
            images = [ImageItem(index=0, status="failed", seed=request.seed if request else None, errorMessage=message)]

        return ImageGenerationResponse(
            taskId=task_id or provider_task_id,
            providerTaskId=provider_task_id,
            mode=mode,  # type: ignore[arg-type]
            status=status,  # type: ignore[arg-type]
            prompt=self._compose_prompt(request) if request else "",
            style=request.style if request else "",
            size=request.size if request else "1664x928",
            count=len(images) if images else (request.count if request else 0),
            seed=request.seed if request else None,
            negativePrompt=request.negativePrompt if request else "",
            images=images,
            message=message,
            metadata=request.metadata if request else {},
        )

    def _compose_prompt(self, request: Optional[ImageGenerationRequest]) -> str:
        if not request:
            return ""
        parts = [request.prompt.strip()]
        if request.style:
            parts.append(f"风格：{request.style.strip()}")
        if request.metadata.get("topic"):
            parts.append(f"主题：{request.metadata['topic']}")
        if request.metadata.get("usage"):
            parts.append(f"用途：{request.metadata['usage']}")
        return "；".join(parts)

    def _download_base64(self, url: str) -> str:
        if not url:
            return ""
        try:
            with urllib.request.urlopen(url, timeout=self.timeout_seconds) as response:
                return base64.b64encode(response.read()).decode("ascii")
        except (urllib.error.HTTPError, urllib.error.URLError):
            return ""

    def _ensure_configured(self, api_key: str = "") -> None:
        active_api_key = self._active_api_key(api_key)
        if not active_api_key or active_api_key.startswith("your-"):
            raise HTTPException(status_code=500, detail="未配置 DASHSCOPE_API_KEY 或 QWEN_IMAGE_API_KEY，无法调用 Qwen 图片生成服务")

    def _active_api_key(self, api_key: str = "") -> str:
        return str(api_key or self.api_key)

    def _active_base_url(self, base_url: str = "") -> str:
        return str(base_url or self.base_url).rstrip("/")

    def _remember_task(self, task_id: str, provider_task_id: str, request: ImageGenerationRequest, mode: str) -> None:
        self.tasks[task_id] = {
            "providerTaskId": provider_task_id,
            "request": request.model_dump(),
            "mode": mode,
            "createdAt": time.time(),
        }

    @staticmethod
    def _local_task_id() -> str:
        return f"img_{time.strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"

    @staticmethod
    def _is_wan_image_model(model_id: str) -> bool:
        value = (model_id or "").strip().lower()
        return value.startswith("wan2.6-image") or value.startswith("wan2.7-image")

    @staticmethod
    def _extract_results_from_choices(output: Dict[str, Any]) -> List[Dict[str, str]]:
        extracted: List[Dict[str, str]] = []
        choices = output.get("choices") or []
        for choice in choices:
            message = (choice or {}).get("message") or {}
            content_list = message.get("content") or []
            for content in content_list:
                if not isinstance(content, dict):
                    continue
                image_url = str(content.get("image") or "").strip()
                if image_url:
                    extracted.append({"url": image_url})
        return extracted

    @staticmethod
    def _normalize_wan_size(size: str, model_id: str) -> str:
        value = (size or "").strip().upper().replace("X", "*")
        if value in {"1K", "2K", "4K"}:
            if value == "4K" and not model_id.lower().startswith("wan2.7-image-pro"):
                return "2K"
            return value
        if "*" in value:
            try:
                width, height = value.split("*", 1)
                w = int(width)
                h = int(height)
                if w >= 3000 or h >= 3000:
                    return "4K" if model_id.lower().startswith("wan2.7-image-pro") else "2K"
                if w >= 1700 or h >= 1700:
                    return "2K"
                return "1K"
            except Exception:
                pass
        return "2K"


_qwen_image_provider: Optional[QwenImageProvider] = None


def get_qwen_image_provider() -> QwenImageProvider:
    global _qwen_image_provider
    if _qwen_image_provider is None:
        _qwen_image_provider = QwenImageProvider()
    return _qwen_image_provider
