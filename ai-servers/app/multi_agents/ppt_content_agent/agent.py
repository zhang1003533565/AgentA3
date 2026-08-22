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
    if not isinstance(slides, list) or not slides:
        raise HTTPException(status_code=502, detail="ppt_content_agent 未返回 slides")
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
            "speakerNote": str(item.get("speakerNote") or item.get("__speaker_note__") or "").strip()[:500],
            "ui": _normalize_ui(item.get("ui")),
            # 组件内容映射（组件name->文本/表格/图表数据），由 service 合并进模板版式。
            # 之前白名单漏掉该字段导致 ui 永远是模板原件。
            "componentContent": item.get("componentContent") if isinstance(item.get("componentContent"), dict) else None,
            # 版式拒绝信号（第 94 节）：内容与当前版式不兼容时由 AI 主动上报，
            # service 记录到 QA 并继续用压缩后的内容填充，不重新设计版式。
            "layoutMismatch": item.get("layoutMismatch")
            if isinstance(item.get("layoutMismatch"), dict)
            else None,
        })
    return {"slides": normalized}


def _normalize_ui(value: Any, depth: int = 0, budget: List[int] | None = None) -> Any:
    """Return the model's UI JSON unchanged.

    The response has already been decoded from JSON, so it is safe to pass
    through as-is.  Deliberately avoiding truncation, key rewriting, or tree
    rebuilding is important here: Presenton's renderer relies on the exact
    component hierarchy and all of its style/asset metadata.  The PPT service
    performs the immutable-tree validation before rendering.
    """
    return value


ppt_content_agent = PptContentAgent()

__all__ = ["ppt_content_agent"]
