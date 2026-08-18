from __future__ import annotations

from typing import Any, Mapping


_STYLE_TEMPLATE_MAP = {
    "simple": "general",
    "campus": "dynamic",
    "focus": "executive",
}
_VERBOSITY_MAP = {
    "concise": "concise",
    "standard": "standard",
    "detailed": "text-heavy",
}


def template_for_settings(settings: Mapping[str, Any], default_template: str) -> str:
    explicit = str(settings.get("templateId") or settings.get("template") or "").strip()
    if explicit:
        return _STYLE_TEMPLATE_MAP.get(explicit.lower(), explicit)
    style = str(settings.get("pptStyle") or "").strip().lower()
    return _STYLE_TEMPLATE_MAP.get(style, default_template)


def verbosity_for_settings(settings: Mapping[str, Any]) -> str:
    value = str(settings.get("contentLevel") or "standard").strip().lower()
    return _VERBOSITY_MAP.get(value, "standard")


def build_instructions(request: Mapping[str, Any]) -> str:
    settings = request.get("settings") if isinstance(request.get("settings"), Mapping) else {}
    parts = [
        "生成中文学习复习演示文稿，所有事实必须来自上传资料。",
        "建立清晰的信息层级，控制单页文字密度，使每一页都能独立表达一个重点。",
    ]
    if settings.get("includeSection"):
        parts.append("主题变化时使用章节过渡页。")
    if settings.get("includeSummary"):
        parts.append("结尾增加知识关系、易错点和复习动作总结。")
    shared = str(request.get("sharedPrompt") or "").strip()
    if shared:
        parts.append(shared)
    return "\n".join(parts)


__all__ = ["build_instructions", "template_for_settings", "verbosity_for_settings"]
