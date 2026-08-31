"""PPT template selection helpers for Leader and admin tool tests."""

from __future__ import annotations

import re
from typing import Any, Dict, List, Mapping, Optional

from app.ppt_generation.service import ppt_generation_service

PPT_TEMPLATE_SELECTION_ANSWER_TYPE = "ppt_template_selection"

_TEMPLATE_CATALOG = {
    "general": ("简约通用", ("general", "默认模板", "通用模板", "简约通用")),
    "dynamic": ("活力校园", ("dynamic", "活力校园", "活力校园模板")),
    "editorial": ("编辑风格", ("editorial", "编辑风格", "编辑风格模板", "杂志风格")),
    "executive": ("深色专注", ("executive", "深色专注", "深色专注模板")),
    "modern": ("现代几何", ("modern", "现代几何", "现代几何模板")),
    "momentum": ("动势表达", ("momentum", "动势表达", "动势表达模板")),
    "standard": ("标准教学", ("standard", "标准教学", "标准教学模板")),
    "swift": ("轻快简报", ("swift", "轻快简报", "轻快简报模板")),
}

_DEFAULT_TEMPLATE_ID = "general"


def parse_ppt_template_from_text(text: str) -> Optional[str]:
    normalized = re.sub(r"\s+", "", str(text or "")).lower()
    if not normalized:
        return None
    if any(token in normalized for token in ("默认模板", "默认就好", "随便", "通用模板", "不用选", "你决定")):
        return _DEFAULT_TEMPLATE_ID
    if "默认" in normalized and ("模板" in normalized or len(normalized) <= 6):
        return _DEFAULT_TEMPLATE_ID
    for template_id, (_label, aliases) in _TEMPLATE_CATALOG.items():
        for alias in aliases:
            alias_norm = re.sub(r"\s+", "", alias).lower()
            if alias_norm and alias_norm in normalized:
                return template_id
    explicit = re.search(r"(?:模板|template)[:：]?\s*([a-zA-Z0-9_-]{2,32})", str(text or ""), flags=re.IGNORECASE)
    if explicit:
        candidate = explicit.group(1).strip().lower()
        if candidate in _TEMPLATE_CATALOG:
            return candidate
    return None


def resolve_ppt_template_id(metadata: Optional[Mapping[str, Any]], input_text: str = "") -> Optional[str]:
    meta = metadata if isinstance(metadata, Mapping) else {}
    if meta.get("pptTemplateConfirmed") is True or meta.get("pptTemplateSelectionSkipped") is True:
        settings = meta.get("pptSettings") if isinstance(meta.get("pptSettings"), Mapping) else {}
        template_id = str(settings.get("templateId") or meta.get("pptTemplateId") or "").strip()
        if template_id:
            return template_id
    settings = meta.get("pptSettings") if isinstance(meta.get("pptSettings"), Mapping) else {}
    template_id = str(settings.get("templateId") or meta.get("pptTemplateId") or "").strip()
    if template_id and meta.get("pptTemplateConfirmed") is True:
        return template_id
    parsed = parse_ppt_template_from_text(input_text)
    if parsed:
        return parsed
    return None


def parse_ppt_page_count(text: str, default: int = 12) -> int:
    match = re.search(r"(\d{1,2})\s*页", str(text or ""))
    if not match:
        return default
    try:
        return max(3, min(30, int(match.group(1))))
    except (TypeError, ValueError):
        return default


def parse_ppt_topic(text: str) -> str:
    raw = str(text or "").strip()
    if not raw:
        return "演示文稿"
    patterns = (
        r"主题是[「\"“']?(.+?)[」\"”']?(?:[，,。；;]|$)",
        r"关于[「\"“']?(.+?)[」\"”']?(?:的)?(?:PPT|ppt|课件|演示文稿|幻灯片)",
        r"(?:PPT|ppt|课件|演示文稿|幻灯片)[：:，,\s]+[「\"“']?(.+?)[」\"”']?(?:[，,。；;]|$)",
    )
    for pattern in patterns:
        match = re.search(pattern, raw, flags=re.IGNORECASE)
        if match:
            topic = match.group(1).strip(" ：:，,。；;")
            if topic:
                return topic[:120]
    first_line = next((line.strip() for line in raw.splitlines() if line.strip()), raw)
    cleaned = re.sub(
        r"^(请|帮我|帮忙|生成|制作|做一份|做一个|给我)\s*",
        "",
        first_line,
    ).strip()
    cleaned = re.sub(r"^(一份|一个)\s*", "", cleaned).strip()
    cleaned = re.sub(r"^\d+\s*页\s*", "", cleaned).strip()
    cleaned = re.sub(r"^(PPT|ppt|课件|演示文稿|幻灯片)\s*", "", cleaned).strip()
    cleaned = cleaned.strip("，,。；; ")
    return (cleaned or first_line or "演示文稿")[:120]


def resolve_ppt_generation_source_content(
    input_text: str,
    metadata: Optional[Mapping[str, Any]] = None,
    conversation_context: Optional[Mapping[str, Any]] = None,
) -> str:
    meta = metadata if isinstance(metadata, Mapping) else {}
    draft = meta.get("pptGenerationDraft") if isinstance(meta.get("pptGenerationDraft"), Mapping) else {}
    draft_content = str(draft.get("sourceContent") or meta.get("pptSourceContent") or "").strip()
    current = str(input_text or "").strip()
    if draft_content and (not current or _looks_like_template_only_input(current)):
        return draft_content
    if draft_content and parse_ppt_template_from_text(current) and len(re.sub(r"\s+", "", current)) <= 24:
        return draft_content
    if current:
        return current
    context = conversation_context if isinstance(conversation_context, Mapping) else {}
    for turn in reversed(context.get("turns") or []):
        if not isinstance(turn, Mapping):
            continue
        user_text = str(turn.get("user") or turn.get("input") or "").strip()
        if user_text and not _looks_like_template_only_input(user_text):
            return user_text
    return draft_content or current


def _looks_like_template_only_input(text: str) -> bool:
    compact = re.sub(r"\s+", "", str(text or ""))
    if not compact:
        return True
    if parse_ppt_template_from_text(text):
        return len(compact) <= 32
    template_tokens = ("模板", "template", "使用", "选", "就用", "确认")
    return len(compact) <= 18 and any(token in compact for token in template_tokens)


def build_ppt_template_catalog() -> List[Dict[str, Any]]:
    options = ppt_generation_service.get_options()
    templates = options.get("templates") if isinstance(options, Mapping) else []
    if not isinstance(templates, list):
        return []
    catalog: List[Dict[str, Any]] = []
    for item in templates:
        if not isinstance(item, Mapping):
            continue
        template_id = str(item.get("id") or "").strip()
        if not template_id:
            continue
        catalog.append({
            "id": template_id,
            "name": str(item.get("name") or template_id),
            "description": str(item.get("description") or ""),
            "layoutCount": int(item.get("layoutCount") or 0),
            "default": bool(item.get("default")),
            "thumbnailUrl": str(item.get("thumbnailUrl") or f"/api/app/ai/ppt/templates/{template_id}/thumbnail"),
        })
    return catalog


def build_ppt_generation_draft(source_content: str, topic: str = "") -> Dict[str, Any]:
    content = str(source_content or "").strip()
    resolved_topic = str(topic or "").strip() or parse_ppt_topic(content)
    return {
        "sourceContent": content,
        "topic": resolved_topic,
        "pageCount": parse_ppt_page_count(content),
    }
