from __future__ import annotations

import json
import re
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


class PptContentAgent:
    name = "ppt_content_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        answer = complete_agent_or_raise(
            self.name,
            input_text,
            evidence or [],
            model_provider=chat_service,
        )
        return json.dumps(_normalize(answer), ensure_ascii=False)


def _normalize(value: str) -> Dict[str, Any]:
    cleaned = str(value or "").strip()
    fenced = re.search(r"```(?:json)?\s*(\{.*\})\s*```", cleaned, flags=re.DOTALL | re.IGNORECASE)
    if fenced:
        cleaned = fenced.group(1)
    else:
        start, end = cleaned.find("{"), cleaned.rfind("}")
        if start >= 0 and end > start:
            cleaned = cleaned[start:end + 1]
    try:
        payload = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=502, detail="ppt_content_agent 未返回有效 JSON") from exc
    slides = payload.get("slides") if isinstance(payload, dict) else None
    if not isinstance(slides, list) or len(slides) < 2:
        raise HTTPException(status_code=502, detail="ppt_content_agent 返回的 slides 数量不足")
    normalized = []
    for index, raw in enumerate(slides, start=1):
        item = raw if isinstance(raw, dict) else {}
        content = item.get("content")
        if isinstance(content, str):
            content = [line.strip(" -*•") for line in content.splitlines() if line.strip(" -*•")]
        if not isinstance(content, list):
            content = []
        normalized.append({
            "index": index,
            "type": str(item.get("type") or ("cover" if index == 1 else "content")),
            "title": str(item.get("title") or f"第 {index} 页").strip()[:100],
            "content": [str(point).strip()[:260] for point in content if str(point).strip()][:6],
            "objective": str(item.get("objective") or "").strip()[:300],
            "visualPrompt": str(item.get("visualPrompt") or "").strip()[:500],
        })
    return {"slides": normalized}


ppt_content_agent = PptContentAgent()

__all__ = ["ppt_content_agent"]
