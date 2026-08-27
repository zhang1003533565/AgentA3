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
    "www.yourwebsite.com",
    "our team",
    "timeline",
    "recommendations",
    "business model",
    "concise supporting text under the",
    "high-level execution plan and milestones",
)
_ELLIPSIS = re.compile(r"(?:…|\.\.\.)")
_INCOMPLETE_EXPRESSION = re.compile(r"(?:[\^=+\-/*(（\[【]|\b(?:O|o)\([^)]*)$")
_CJK = re.compile(r"[\u2e80-\u9fff\uf900-\ufaff]")

# Presenton's embedded templates use a 16:9 canvas.  These bounds are only
# used to recognize likely footer page numbers; they never participate in the
# export success decision.
_SLIDE_WIDTH = 12_192_000
_SLIDE_HEIGHT = 6_858_000


def validate_exported_pptx(path: Path) -> Dict[str, Any]:
    """Run deterministic checks on the actual PPTX package, not its source UI.

    The existing overflow helper only detects objects outside the slide canvas.
    This check catches the failures that matter after export: stale template
    copy, text-text collisions and text that was truncated before serialization.
    """
    errors: List[Dict[str, Any]] = []
    warnings: List[Dict[str, Any]] = []
    slides_checked = 0
    footer_page_numbers: Dict[int, int] = {}
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
            text_boxes: List[Dict[str, Any]] = []
            for shape in root.findall(".//p:sp", _NS):
                texts = [str(node.text or "") for node in shape.findall(".//a:t", _NS)]
                text = "".join(texts).strip()
                if not text:
                    continue
                lowered = text.casefold()
                marker = next(
                    (
                        value
                        for value in _PLACEHOLDER_MARKERS
                        if re.search(rf"(?<![a-z]){re.escape(value)}(?![a-z])", lowered)
                    ),
                    None,
                )
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
                if _INCOMPLETE_EXPRESSION.search(text):
                    warnings.append({
                        "slide": slide_number,
                        "kind": "INCOMPLETE_EXPRESSION",
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
                    if _is_footer_page_number(text, left, top, width, height):
                        try:
                            footer_page_numbers[slide_number] = int(text)
                        except ValueError:
                            pass
                    text_boxes.append(
                        {
                            "text": text,
                            "box": (left, top, width, height),
                            "alignment": _shape_alignment(shape),
                            "fontSize": _shape_font_size(shape),
                        }
                    )

            for index, first in enumerate(text_boxes):
                for second in text_boxes[index + 1:]:
                    first_box = first["box"]
                    second_box = second["box"]
                    overlap = _overlap_area(first_box, second_box)
                    smaller = min(
                        first_box[2] * first_box[3],
                        second_box[2] * second_box[3],
                    )
                    if smaller <= 0 or overlap / smaller < 0.15:
                        continue
                    if not _visible_texts_overlap(first, second):
                        continue
                    errors.append({
                        "slide": slide_number,
                        "kind": "TEXT_OVERLAP",
                        "first": str(first["text"])[:100],
                        "second": str(second["text"])[:100],
                        "overlapRatio": round(overlap / smaller, 3),
                    })
    if len(footer_page_numbers) >= max(3, slides_checked // 2):
        for slide_number, actual in sorted(footer_page_numbers.items()):
            if actual == slide_number:
                continue
            warnings.append({
                "slide": slide_number,
                "kind": "PAGE_NUMBER_MISMATCH",
                "expected": slide_number,
                "actual": actual,
            })
    return {
        "passed": not errors,
        "slides": slides_checked,
        "errors": errors,
        "warnings": warnings,
    }


def _is_footer_page_number(
    text: str,
    left: float,
    top: float,
    width: float,
    height: float,
) -> bool:
    """Recognize a numeric footer without treating card badges as page numbers."""
    if not re.fullmatch(r"\d{1,3}", str(text or "").strip()):
        return False
    right = left + width
    bottom = top + height
    return (
        left >= _SLIDE_WIDTH * 0.84
        and right <= _SLIDE_WIDTH * 1.02
        and top >= _SLIDE_HEIGHT * 0.86
        and bottom <= _SLIDE_HEIGHT * 1.02
    )


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


def _shape_alignment(shape: ElementTree.Element) -> str:
    paragraph = shape.find(".//a:p", _NS)
    properties = paragraph.find("a:pPr", _NS) if paragraph is not None else None
    value = properties.get("algn") if properties is not None else None
    return {"ctr": "center", "r": "right"}.get(str(value or ""), "left")


def _shape_font_size(shape: ElementTree.Element) -> float:
    """Read the first serialized run size, falling back to the OOXML default."""
    for node in shape.findall(".//a:rPr", _NS) + shape.findall(".//a:defRPr", _NS):
        value = node.get("sz")
        if value:
            try:
                return max(1.0, float(value) / 100.0)
            except (TypeError, ValueError):
                pass
    return 18.0


def _estimated_text_width_emu(text: str, font_size_pt: float) -> float:
    """Estimate visible glyph width without treating the whole text box as ink."""
    max_width_pt = 0.0
    current_pt = 0.0
    for character in str(text):
        if character in "\r\n":
            max_width_pt = max(max_width_pt, current_pt)
            current_pt = 0.0
            continue
        if _CJK.match(character):
            factor = 1.0
        elif character.isalnum() and character.isascii():
            factor = 0.55
        elif character.isspace():
            factor = 0.30
        else:
            factor = 0.50
        current_pt += font_size_pt * factor
    max_width_pt = max(max_width_pt, current_pt)
    return max_width_pt * 12700.0


def _visible_text_span(box: Dict[str, Any]) -> Tuple[float, float]:
    left, _top, width, _height = box["box"]
    visible_width = min(
        width,
        max(1.0, _estimated_text_width_emu(box["text"], box["fontSize"])),
    )
    alignment = box["alignment"]
    if alignment == "right":
        return left + width - visible_width, left + width
    if alignment == "center":
        inset = (width - visible_width) / 2.0
        return left + inset, left + inset + visible_width
    return left, left + visible_width


def _visible_texts_overlap(first: Dict[str, Any], second: Dict[str, Any]) -> bool:
    first_left, first_right = _visible_text_span(first)
    second_left, second_right = _visible_text_span(second)
    return min(first_right, second_right) > max(first_left, second_left)


__all__ = ["validate_exported_pptx"]
