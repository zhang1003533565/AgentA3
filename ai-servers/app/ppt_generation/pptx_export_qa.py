from __future__ import annotations

import re
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Tuple
from xml.etree import ElementTree


_NS = {
    "p": "http://schemas.openxmlformats.org/presentationml/2006/main",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
}
_PLACEHOLDER_MARKERS = (
    "www.yourwebsite.com",
    "our team",
    "timeline",
    "recommendations",
    "business model",
    "concise supporting text under the",
    "high-level execution plan and milestones",
)
_ELLIPSIS = re.compile(r"(?:…|\.\.\.)")


def validate_exported_pptx(path: Path) -> Dict[str, Any]:
    """Run deterministic checks on the actual PPTX package, not its source UI.

    The existing overflow helper only detects objects outside the slide canvas.
    This check catches the failures that matter after export: stale template
    copy, text-text collisions and text that was truncated before serialization.
    """
    errors: List[Dict[str, Any]] = []
    warnings: List[Dict[str, Any]] = []
    slides_checked = 0
    with zipfile.ZipFile(path) as archive:
        slide_names = sorted(
            (
                name for name in archive.namelist()
                if name.startswith("ppt/slides/slide") and name.endswith(".xml")
            ),
            key=_slide_sort_key,
        )
        for slide_number, name in enumerate(slide_names, start=1):
            slides_checked += 1
            root = ElementTree.fromstring(archive.read(name))
            text_boxes: List[Tuple[str, float, float, float, float]] = []
            for shape in root.findall(".//p:sp", _NS):
                texts = [str(node.text or "") for node in shape.findall(".//a:t", _NS)]
                text = "".join(texts).strip()
                if not text:
                    continue
                lowered = text.casefold()
                marker = next((value for value in _PLACEHOLDER_MARKERS if value in lowered), None)
                if marker:
                    errors.append({
                        "slide": slide_number,
                        "kind": "TEMPLATE_PLACEHOLDER",
                        "text": text[:160],
                        "marker": marker,
                    })
                if _ELLIPSIS.search(text):
                    warnings.append({
                        "slide": slide_number,
                        "kind": "TEXT_ELLIPSIS",
                        "text": text[:160],
                    })
                xfrm = shape.find("p:spPr/a:xfrm", _NS)
                offset = xfrm.find("a:off", _NS) if xfrm is not None else None
                extent = xfrm.find("a:ext", _NS) if xfrm is not None else None
                if offset is None or extent is None:
                    continue
                try:
                    left = float(offset.get("x") or 0)
                    top = float(offset.get("y") or 0)
                    width = float(extent.get("cx") or 0)
                    height = float(extent.get("cy") or 0)
                except (TypeError, ValueError):
                    continue
                if width > 0 and height > 0:
                    text_boxes.append((text, left, top, width, height))

            for index, first in enumerate(text_boxes):
                for second in text_boxes[index + 1:]:
                    overlap = _overlap_area(first[1:], second[1:])
                    smaller = min(first[3] * first[4], second[3] * second[4])
                    if smaller <= 0 or overlap / smaller < 0.15:
                        continue
                    errors.append({
                        "slide": slide_number,
                        "kind": "TEXT_OVERLAP",
                        "first": first[0][:100],
                        "second": second[0][:100],
                        "overlapRatio": round(overlap / smaller, 3),
                    })
    return {
        "passed": not errors,
        "slides": slides_checked,
        "errors": errors,
        "warnings": warnings,
    }


def _slide_sort_key(name: str) -> int:
    match = re.search(r"slide(\d+)\.xml$", name)
    return int(match.group(1)) if match else 0


def _overlap_area(first: Tuple[float, float, float, float], second: Tuple[float, float, float, float]) -> float:
    first_x, first_y, first_width, first_height = first
    second_x, second_y, second_width, second_height = second
    right = min(first_x + first_width, second_x + second_width)
    bottom = min(first_y + first_height, second_y + second_height)
    left = max(first_x, second_x)
    top = max(first_y, second_y)
    return max(0.0, right - left) * max(0.0, bottom - top)


__all__ = ["validate_exported_pptx"]
