from __future__ import annotations

from typing import Any, Mapping


_STYLE_TEMPLATE_MAP = {
    "simple": "general",
    "campus": "dynamic",
    "focus": "executive",
}


def template_for_settings(settings: Mapping[str, Any], default_template: str) -> str:
    explicit = str(settings.get("templateId") or settings.get("template") or "").strip()
    if explicit:
        return _STYLE_TEMPLATE_MAP.get(explicit.lower(), explicit)
    style = str(settings.get("pptStyle") or "").strip().lower()
    return _STYLE_TEMPLATE_MAP.get(style, default_template)


__all__ = ["template_for_settings"]
