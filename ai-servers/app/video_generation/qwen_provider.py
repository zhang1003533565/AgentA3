import json
import os
import time
import urllib.error
import urllib.request
import uuid
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.models.video_generation import (
    VideoBatchRequest,
    VideoGenerationRequest,
    VideoGenerationResponse,
    VideoItem,
)


class QwenVideoProvider:
    """DashScope/Qwen video provider based on async Wan video synthesis tasks."""

    def __init__(self) -> None:
        self.api_key = os.getenv("DASHSCOPE_API_KEY") or os.getenv("QWEN_VIDEO_API_KEY") or ""
        self.base_url = os.getenv("QWEN_VIDEO_BASE_URL", "https://dashscope.aliyuncs.com").rstrip("/")
        self.model = os.getenv("QWEN_VIDEO_MODEL", "")
        self.timeout_seconds = float(os.getenv("QWEN_VIDEO_TIMEOUT_SECONDS", "30"))
        self.poll_interval_seconds = float(os.getenv("QWEN_VIDEO_POLL_INTERVAL_SECONDS", "2"))
        self.max_poll_attempts = int(os.getenv("QWEN_VIDEO_MAX_POLL_ATTEMPTS", "60"))
        self.tasks: Dict[str, Dict[str, Any]] = {}
        self.completed_tasks: Dict[str, VideoGenerationResponse] = {}

    def generate(self, request: VideoGenerationRequest) -> VideoGenerationResponse:
        task_id = self._local_task_id()
        provider_task_id = self._submit_task(request)
        self._remember_task(task_id, provider_task_id, request, "single")
        response = self.get_task(provider_task_id, request=request, task_id=task_id, mode="single")
        response.taskId = task_id
        return response

    def batch(self, request: VideoBatchRequest) -> VideoGenerationResponse:
        prompts = request.prompts or [request.prompt]
        if len(prompts) == 1:
            single = VideoGenerationRequest(**request.model_dump(exclude={"prompts"}))
            return self.generate(single)

        videos: List[VideoItem] = []
        provider_task_ids: List[str] = []
        first_error = ""
        for index, prompt in enumerate(prompts):
            payload = request.model_dump(exclude={"prompts"})
            payload["prompt"] = prompt
            try:
                single_response = self.generate(VideoGenerationRequest(**payload))
                provider_task_ids.append(single_response.providerTaskId)
                if single_response.videos:
                    video = single_response.videos[0]
                    video.index = index
                    videos.append(video)
                else:
                    videos.append(VideoItem(index=index, status="failed", seed=request.seed, errorMessage="未返回视频结果"))
            except Exception as exc:
                first_error = first_error or str(exc)
                videos.append(VideoItem(index=index, status="failed", seed=request.seed, errorMessage=str(exc)))

        success_count = sum(1 for item in videos if item.status == "success")
        status = "success" if success_count == len(videos) else ("partial_success" if success_count else "failed")
        task_id = self._local_task_id()
        response = VideoGenerationResponse(
            taskId=task_id,
            providerTaskId=",".join(provider_task_ids),
            mode="batch",
            status=status,
            prompt=request.prompt,
            imageUrl=request.imageUrl,
            size=request.size,
            duration=request.duration,
            seed=request.seed,
            videos=videos,
            message="批量生成完成" if success_count else (first_error or "批量生成失败"),
            metadata={**request.metadata, "prompts": prompts},
        )
        self.completed_tasks[task_id] = response
        return response

    def get_task(
        self,
        provider_task_id: str,
        request: Optional[VideoGenerationRequest] = None,
        task_id: Optional[str] = None,
        mode: str = "single",
    ) -> VideoGenerationResponse:
        if provider_task_id in self.completed_tasks:
            return self.completed_tasks[provider_task_id]

        task_record = self.tasks.get(provider_task_id)
        if task_record:
            task_id = task_id or provider_task_id
            provider_task_id = task_record["providerTaskId"]
            mode = task_record.get("mode", mode)
            if request is None:
                request = VideoGenerationRequest(**task_record["request"])

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

    def _submit_task(self, request: VideoGenerationRequest) -> str:
        self._ensure_configured(request.apiKey)
        if not (request.model or self.model):
            raise HTTPException(status_code=400, detail="Qwen 视频模型未配置，请在模型配置中填写视频模型 ID")

        input_payload: Dict[str, Any] = {"prompt": request.prompt}
        if request.imageUrl:
            input_payload["img_url"] = request.imageUrl

        parameters: Dict[str, Any] = {
            "size": request.size.replace("x", "*"),
            "duration": request.duration,
        }
        if request.seed is not None:
            parameters["seed"] = request.seed

        payload = {
            "model": request.model or self.model,
            "input": input_payload,
            "parameters": parameters,
        }
        response = self._request(
            "POST",
            "/api/v1/services/aigc/video-generation/video-synthesis",
            body=payload,
            api_key=request.apiKey,
            base_url=request.baseUrl,
        )
        provider_task_id = response.get("output", {}).get("task_id")
        if not provider_task_id:
            raise HTTPException(status_code=502, detail=f"Qwen 视频任务创建失败：{response}")
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
            raise HTTPException(status_code=exc.code, detail=f"Qwen 视频服务返回错误：{error_body}") from exc
        except urllib.error.URLError as exc:
            raise HTTPException(status_code=502, detail=f"Qwen 视频服务请求失败：{exc}") from exc

    def _build_response(
        self,
        provider_task_id: str,
        payload: Dict[str, Any],
        request: Optional[VideoGenerationRequest],
        task_id: Optional[str],
        mode: str,
        timeout: bool = False,
    ) -> VideoGenerationResponse:
        output = payload.get("output", {}) if payload else {}
        provider_status = str(output.get("task_status", "")).upper()
        message = output.get("message") or payload.get("message") or ""
        if timeout:
            status = "running"
            message = message or "视频任务仍在生成中"
        elif provider_status == "SUCCEEDED":
            status = "success"
            message = message or "生成完成"
        elif provider_status in {"FAILED", "CANCELED", "UNKNOWN"}:
            status = "failed"
            message = message or f"视频生成失败：{provider_status}"
        else:
            status = "running"
            message = message or "视频任务处理中"

        urls = self._extract_video_urls(output)
        videos = [
            VideoItem(index=index, url=url, status="success" if url else status, seed=request.seed if request else None)
            for index, url in enumerate(urls)
        ]
        if status == "failed" and not videos:
            videos = [VideoItem(index=0, status="failed", seed=request.seed if request else None, errorMessage=message)]

        return VideoGenerationResponse(
            taskId=task_id or provider_task_id,
            providerTaskId=provider_task_id,
            mode=mode,  # type: ignore[arg-type]
            status=status,  # type: ignore[arg-type]
            prompt=request.prompt if request else "",
            imageUrl=request.imageUrl if request else "",
            size=request.size if request else "1280x720",
            duration=request.duration if request else 5,
            seed=request.seed if request else None,
            videos=videos,
            message=message,
            metadata=request.metadata if request else {},
        )

    def _extract_video_urls(self, output: Dict[str, Any]) -> List[str]:
        urls: List[str] = []
        for key in ("video_url", "url"):
            value = output.get(key)
            if isinstance(value, str) and value:
                urls.append(value)
        for item in output.get("results") or []:
            if isinstance(item, dict):
                value = item.get("url") or item.get("video_url")
                if value:
                    urls.append(str(value))
        return urls

    def _ensure_configured(self, api_key: str = "") -> None:
        active_api_key = self._active_api_key(api_key)
        if not active_api_key or active_api_key.startswith("your-"):
            raise HTTPException(status_code=500, detail="未配置 DASHSCOPE_API_KEY 或 QWEN_VIDEO_API_KEY，无法调用 Qwen 视频生成服务")

    def _active_api_key(self, api_key: str = "") -> str:
        return str(api_key or self.api_key)

    def _active_base_url(self, base_url: str = "") -> str:
        return str(base_url or self.base_url).rstrip("/")

    def _remember_task(self, task_id: str, provider_task_id: str, request: VideoGenerationRequest, mode: str) -> None:
        self.tasks[task_id] = {
            "providerTaskId": provider_task_id,
            "request": request.model_dump(),
            "mode": mode,
            "createdAt": time.time(),
        }

    @staticmethod
    def _local_task_id() -> str:
        return f"vid_{time.strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"


_qwen_video_provider: Optional[QwenVideoProvider] = None


def get_qwen_video_provider() -> QwenVideoProvider:
    global _qwen_video_provider
    if _qwen_video_provider is None:
        _qwen_video_provider = QwenVideoProvider()
    return _qwen_video_provider
