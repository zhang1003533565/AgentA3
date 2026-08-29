"""Shared template sample markers for PPT merge cleanup and export QA."""

from __future__ import annotations

import re
from typing import Optional, Tuple

# Keep this list aligned across merge cleanup, content QA, and exported PPTX QA.
PLACEHOLDER_MARKERS: Tuple[str, ...] = (
    "metric",
    "last year",
    "this year",
    "growth",
    "revenue",
    "customers",
    "conversion rate",
    "retention",
    "ceo",
    "cto",
    "coo",
    "cmo",
    "john doe",
    "juliana silva",
    "daniel gallego",
    "ketut susilo",
    "anna robertson",
    "www.yourwebsite.com",
    "december 2025",
    "jan 1, 2025",
    "our team",
    "timeline",
    "recommendations",
    "business model",
    "concise supporting text under the",
    "high-level execution plan and milestones",
)

_MARKER_PATTERN = re.compile(
    "|".join(
        rf"(?<![a-z]){re.escape(marker)}(?![a-z])"
        for marker in sorted(PLACEHOLDER_MARKERS, key=len, reverse=True)
    )
)


def find_template_marker(text: str) -> Optional[str]:
    """Return the first known template marker found in ``text``."""
    lowered = str(text or "").casefold().strip()
    if not lowered:
        return None
    match = _MARKER_PATTERN.search(lowered)
    if not match:
        return None
    return match.group(0)


def contains_template_marker(text: str) -> bool:
    return find_template_marker(text) is not None


__all__ = ["PLACEHOLDER_MARKERS", "contains_template_marker", "find_template_marker"]
