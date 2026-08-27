"""PresentationConsistencyValidator + QA report (spec §62-§64, §78-§79).

Cross-page checks:
- same semantic slot (element name) on different slides must keep the same
  font size unless the template itself varies the role
- per-slide density levels are aggregated for the QA report

The QA report is deterministic markdown written when
PPT_QA_REPORT_DIR is set, and always summarized in the debug log.
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any, Dict, List, Mapping

from app.ppt_generation.layout_validator import _node_font_size, _node_text, validate_slide
from app.ppt_generation.template_model import SlideLayoutModel

logger = logging.getLogger(__name__)

FONT_DIFF_TOLERANCE = 1.5
_FONT_CHECK_ROLES = {"title", "subtitle", "body", "card"}


def validate_presentation(
    slides: List[Mapping[str, Any]],
    models: Dict[str, SlideLayoutModel],
) -> List[Dict[str, Any]]:
    """跨页一致性：同名同角色元素的字号漂移检测。"""
    issues: List[Dict[str, Any]] = []
    slots: Dict[str, List[Dict[str, Any]]] = {}
    for slide_index, slide in enumerate(slides, start=1):
        ui = slide.get("ui")
        layout_id = str(slide.get("templateLayoutId") or slide.get("layout") or "")
        model = models.get(layout_id)
        if not isinstance(ui, Mapping) or model is None:
            continue
        for name, model_elements in model.elements.items():
            for element in model_elements:
                if element.role not in _FONT_CHECK_ROLES or element.element_type not in {"text", "text-list"}:
                    continue
                node = _find_named_node(ui, name)
                if node is None:
                    continue
                font_size = _node_font_size(node)
                if font_size <= 0:
                    continue
                slots.setdefault(name, []).append({
                    "slide": slide_index,
                    "fontSize": font_size,
                    "role": element.role,
                })
    for name, entries in slots.items():
        if len(entries) < 2:
            continue
        sizes = [entry["fontSize"] for entry in entries]
        diff = max(sizes) - min(sizes)
        if diff > FONT_DIFF_TOLERANCE:
            issues.append({
                "kind": "FONT_INCONSISTENT",
                "element": name,
                "detail": f"同名槽位字号不一致 diff={diff:.1f}pt",
                "slides": [entry["slide"] for entry in entries],
                "sizes": [round(size, 1) for size in sizes],
            })
    return issues


def _find_named_node(root: Mapping[str, Any], name: str) -> Dict[str, Any]:
    found: List[Dict[str, Any]] = []

    def walk(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if not isinstance(node, Mapping):
            return
        if str(node.get("name") or "") == name:
            found.append(dict(node))
        for key in ("components", "elements", "children"):
            if key in node:
                walk(node[key])
        if "child" in node:
            walk(node["child"])

    for key in ("components", "elements"):
        if key in root:
            walk(root[key])
    return found[0] if found else {}


def build_qa_report(
    slides: List[Mapping[str, Any]],
    presentation_issues: List[Dict[str, Any]],
    models: Dict[str, SlideLayoutModel],
    template_id: str,
    content_quality: Mapping[str, Any] | None = None,
) -> str:
    lines = [
        "# PPT QA Report",
        "",
        f"- template: {template_id}",
        f"- slides: {len(slides)}",
        "",
    ]
    for index, slide in enumerate(slides, start=1):
        qa = slide.get("_qa") if isinstance(slide.get("_qa"), Mapping) else {}
        layout_id = str(qa.get("layoutId") or slide.get("templateLayoutId") or "")
        status = str(qa.get("finalStatus") or "unknown")
        errors = qa.get("validationErrors") or []
        repairs = qa.get("repairCount") or 0
        density = str(qa.get("densityLevel") or "NORMAL")
        content_length = qa.get("contentLength") or 0
        marker = "PASS" if status == "clean" else "REPAIRED" if status == "repaired" else "FAIL"
        lines.append(f"## Slide {index} [{marker}]")
        lines.append(f"- layout: {layout_id} | status: {status} | density: {density}")
        lines.append(f"- contentLength: {content_length} | repairCount: {repairs}")
        if errors:
            lines.append(f"- validationErrors: {', '.join(str(value) for value in errors)}")
        history = qa.get("repairHistory")
        if isinstance(history, list) and history:
            lines.append("- repairHistory:")
            for entry in history[-3:]:
                lines.append(
                    f"  - round {entry.get('round')}: {entry.get('error')} "
                    f"({entry.get('element')}) -> {entry.get('strategy')}"
                )
        lines.append("")
    if presentation_issues:
        lines.append("## Presentation consistency")
        for issue in presentation_issues:
            lines.append(
                f"- {issue['kind']} {issue['element']}: {issue['detail']} "
                f"slides={issue.get('slides')} sizes={issue.get('sizes')}"
            )
        lines.append("")
    if isinstance(content_quality, Mapping):
        lines.append("## Content quality")
        lines.append(
            f"- status: {content_quality.get('status') or 'unknown'} | "
            f"errors: {content_quality.get('errorCount') or 0} | "
            f"warnings: {content_quality.get('warningCount') or 0}"
        )
        source_trace = content_quality.get("sourceTrace")
        if isinstance(source_trace, Mapping):
            lines.append(
                f"- source: {source_trace.get('sourceName') or ''} | "
                f"sha256: {str(source_trace.get('sha256') or '')[:16]} | "
                f"chars: {source_trace.get('charCount') or 0}"
            )
        for issue in content_quality.get("issues") or []:
            if isinstance(issue, Mapping):
                slide = f" slide={issue.get('slide')}" if issue.get("slide") else ""
                lines.append(
                    f"- [{issue.get('severity') or 'warning'}] {issue.get('code') or 'QUALITY'}{slide}: "
                    f"{issue.get('message') or ''}"
                )
        lines.append("")
    return "\n".join(lines)


def write_qa_report(
    report: str,
    template_id: str,
    presentation_id: str = "",
) -> Optional[str]:
    root = os.getenv("PPT_QA_REPORT_DIR")
    if not root:
        return None
    directory = Path(root)
    try:
        directory.mkdir(parents=True, exist_ok=True)
        safe_presentation = (presentation_id or "presentation").replace(":", "-")[:80]
        path = directory / f"qa-{template_id}-{safe_presentation}.md"
        path.write_text(report, encoding="utf-8")
        return str(path)
    except OSError as exc:
        logger.warning("PPT QA report write failed: %s", exc)
        return None


__all__ = ["validate_presentation", "build_qa_report", "write_qa_report", "validate_slide"]
