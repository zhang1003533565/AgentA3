"""Leader-callable PPT orchestration aligned with the App Presenton pipeline."""

from __future__ import annotations

import re
from typing import Any, Dict, Mapping, Optional

from fastapi import HTTPException

from app.ppt_generation.service import ppt_generation_service
from app.request_context import get_request_user_id

PPT_OUTLINE_AGENT_NAME = "ppt_outline_agent"
LEADER_PPT_DEFAULT_PAGE_COUNT = 12
LEADER_PPT_MAX_PAGE_COUNT = 30


def _resolve_user_id(metadata: Optional[Mapping[str, Any]]) -> str:
    if isinstance(metadata, Mapping):
        for key in ("userId", "user_id"):
            value = str(metadata.get(key) or "").strip()
            if value:
                return value
    context_user_id = get_request_user_id()
    if context_user_id:
        return context_user_id
    raise HTTPException(status_code=401, detail="PPT 生成缺少可信用户身份")


def _derive_topic(input_text: str) -> str:
    text = str(input_text or "").strip()
    if not text:
        return "演示文稿"
    first_line = next((line.strip() for line in text.splitlines() if line.strip()), text)
    cleaned = re.sub(r"^(请|帮我|帮忙|生成|制作|做一份|做一个)\s*", "", first_line).strip()
    return (cleaned or first_line or "演示文稿")[:120]


def start_leader_ppt_generation(
    input_text: str,
    llm_config: Any,
    metadata: Optional[Mapping[str, Any]] = None,
    *,
    template_id: str = "general",
    page_count: Optional[int] = None,
) -> Dict[str, Any]:
    source_content = str(input_text or "").strip()
    if not source_content:
        raise HTTPException(status_code=400, detail="PPT 生成缺少输入内容")
    user_id = _resolve_user_id(metadata)
    topic = _derive_topic(source_content)
    requested_pages = page_count if page_count is not None else LEADER_PPT_DEFAULT_PAGE_COUNT
    bounded_pages = max(3, min(LEADER_PPT_MAX_PAGE_COUNT, int(requested_pages)))
    settings = {
        "templateId": str(template_id or "general").strip() or "general",
        "imageMode": "placeholder",
    }
    if isinstance(metadata, Mapping):
        raw_settings = metadata.get("pptSettings")
        if isinstance(raw_settings, Mapping):
            settings.update({str(key): value for key, value in raw_settings.items()})
    pipeline_request = {
        "sourceName": topic,
        "sourceContent": source_content[:200_000],
        "topic": topic,
        "pageCount": bounded_pages,
        "outlineMode": "ai_outline",
        "settings": settings,
        "sharedPrompt": str((metadata or {}).get("pptSharedPrompt") or "").strip(),
    }
    task = ppt_generation_service.create_leader_pipeline_task(
        user_id,
        pipeline_request,
        llm_config,
    )
    return {
        **task,
        "title": topic,
        "pageCount": bounded_pages,
        "templateId": settings["templateId"],
        "boundAgent": PPT_OUTLINE_AGENT_NAME,
    }
