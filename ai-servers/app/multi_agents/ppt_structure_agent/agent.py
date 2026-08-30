from __future__ import annotations

import json
import re
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


class PptStructureAgent:
    name = "ppt_structure_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        request = input_text
        for attempt in range(2):
            try:
                answer = complete_agent_or_raise(self.name, request, evidence or [], model_provider=chat_service)
                return json.dumps(normalize_structure_answer(answer), ensure_ascii=False)
            except HTTPException as exc:
                detail = str(getattr(exc, "detail", "") or "")
                if attempt or int(getattr(exc, "status_code", 0) or 0) != 502 or "LLM 返回内容为空" in detail:
                    raise
                request = (
                    f"{input_text}\n\n上一轮响应未通过 PPT 布局 JSON 校验。"
                    "请只返回一个可直接 json.loads 的 JSON 对象，禁止 Markdown、代码围栏、解释文字；"
                    "必须包含非空 layouts 数组，每项必须包含正整数 slideIndex 和有效 layoutId。"
                )
        raise RuntimeError("ppt_structure_agent structured retry did not execute")


def normalize_structure_answer(value: str) -> Dict[str, Any]:
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
        raise HTTPException(status_code=502, detail="ppt_structure_agent 未返回有效 JSON") from exc
    layouts = payload.get("layouts") if isinstance(payload, dict) else None
    if not isinstance(layouts, list) or not layouts:
        raise HTTPException(status_code=502, detail="ppt_structure_agent 未返回 layouts")
    normalized = []
    for raw in layouts:
        if not isinstance(raw, dict):
            continue
        try:
            index = int(raw.get("slideIndex") or raw.get("index"))
        except (TypeError, ValueError):
            continue
        layout_id = str(raw.get("layoutId") or raw.get("layout") or "").strip()
        if index > 0 and layout_id:
            normalized.append({"slideIndex": index, "layoutId": layout_id})
    if not normalized:
        raise HTTPException(status_code=502, detail="ppt_structure_agent layouts 格式无效")
    return {"layouts": normalized}


ppt_structure_agent = PptStructureAgent()

__all__ = ["ppt_structure_agent", "normalize_structure_answer"]
