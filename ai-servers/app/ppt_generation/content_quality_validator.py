"""Deterministic quality checks for AI-generated presentation content.

This module deliberately does not judge visual aesthetics and does not call an
LLM.  It records enough evidence to answer whether a page is grounded in the
source and whether the generated deck is more than a copy of its outline.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Sequence


_HEADING_RE = re.compile(
    r"^\s*(?:#{1,6}\s*|第\s*\d+\s*[章节篇]\s*[：:]?\s*)([^#\r\n]+?)\s*$",
    re.IGNORECASE,
)
_CHAPTER_RE = re.compile(r"(?:第\s*[0-9一二三四五六七八九十百]+\s*[章节篇]|章\s*[0-9一二三四五六七八九十百]+)")
_PUNCT_RE = re.compile(r"[\s\u3000，。、“”‘’：:；;,.!?！？（）()\[\]{}<>《》/\\|_—–-]+")
_META_RE = re.compile(
    r"本页(?:介绍|将|主要|重点|围绕|聚焦)|"
    r"(?:梳理|概述|说明|了解|掌握).{0,18}(?:范围|目标|要点|内容|概念)"
)
_PLACEHOLDERS = (
    "Metric", "Last Year", "This Year", "Growth", "Revenue", "Customers",
    "Conversion Rate", "Retention", "CEO", "CTO", "COO", "CMO", "John Doe",
    "Juliana Silva", "Daniel Gallego", "Ketut Susilo", "Anna Robertson",
    "www.yourwebsite.com", "December 2025", "Jan 1, 2025",
)


def _text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, (int, float, bool)):
        return str(value)
    return ""


def _values(value: Any) -> List[str]:
    if isinstance(value, str):
        return [line.strip(" -*•\t") for line in value.splitlines() if line.strip(" -*•\t")]
    if isinstance(value, (list, tuple)):
        result: List[str] = []
        for entry in value:
            if isinstance(entry, Mapping):
                result.extend(_values(entry.get("text") or entry.get("content") or entry.get("value")))
            else:
                item = _text(entry)
                if item:
                    result.append(item)
        return result
    item = _text(value)
    return [item] if item else []


def _compact(value: str) -> str:
    return _PUNCT_RE.sub("", _text(value)).lower()


def _walk_text(value: Any) -> Iterable[str]:
    if isinstance(value, str):
        if value.strip():
            yield value.strip()
        return
    if isinstance(value, Mapping):
        for key, child in value.items():
            if key in {"text", "content", "value", "label", "title", "description", "subtitle"}:
                yield from _walk_text(child)
            elif key in {"components", "elements", "children", "child", "rows", "columns", "series", "categories"}:
                yield from _walk_text(child)
        return
    if isinstance(value, (list, tuple)):
        for child in value:
            yield from _walk_text(child)


def visible_texts(slide: Mapping[str, Any]) -> List[str]:
    """Return human-visible text candidates from both content and UI data."""
    values: List[str] = []
    for key in ("title", "content", "objective", "speakerNote", "componentContent", "ui"):
        values.extend(_walk_text(slide.get(key)))
    result: List[str] = []
    seen: set[str] = set()
    for value in values:
        value = value.strip()
        if value and value not in seen:
            seen.add(value)
            result.append(value)
    return result


def extract_source_chapters(source: str) -> List[str]:
    """Extract explicit source headings without inventing chapters."""
    chapters: List[str] = []
    for raw_line in str(source or "").splitlines():
        line = raw_line.strip().strip("#*- ")
        if not line or len(line) > 120:
            continue
        match = _HEADING_RE.match(raw_line)
        is_nested_markdown_heading = bool(re.match(r"^\s*#{2,6}\s+", raw_line))
        if not match and not _CHAPTER_RE.search(line):
            continue
        if match and raw_line.lstrip().startswith("# ") and not _CHAPTER_RE.search(line):
            # A lone level-one heading is normally the document title, not a
            # chapter that every slide must cover.
            continue
        if match and not is_nested_markdown_heading and not _CHAPTER_RE.search(line):
            continue
        title = (match.group(1) if match else line).strip(" ：:")
        title = re.sub(r"^第\s*\d+\s*[章节篇]\s*[：:]?\s*", "", title)
        if len(title) >= 2 and title not in chapters:
            chapters.append(title)
    return chapters


def build_source_trace(source_name: str, source: str, source_file_id: str = "") -> Dict[str, Any]:
    text = str(source or "")
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest() if text else ""
    trace: Dict[str, Any] = {
        "version": 1,
        "sourceName": str(source_name or "演示文稿"),
        "sourceFileId": str(source_file_id or ""),
        "sha256": digest,
        "charCount": len(text),
        "chapterTitles": extract_source_chapters(text),
        "snapshotStored": False,
        "snapshotPath": "",
    }
    # Full source is written only when the existing opt-in QA directory is set;
    # API responses retain the hash and structural metadata, not user material.
    root = os.getenv("PPT_QA_REPORT_DIR")
    if root and digest and text:
        try:
            directory = Path(root)
            directory.mkdir(parents=True, exist_ok=True)
            path = directory / f"source-{digest[:20]}.txt"
            if not path.exists():
                path.write_text(text, encoding="utf-8")
            trace["snapshotStored"] = True
            trace["snapshotPath"] = str(path)
        except OSError:
            pass
    return trace


def _issue(code: str, severity: str, message: str, slide: int = 0, **details: Any) -> Dict[str, Any]:
    result: Dict[str, Any] = {
        "code": code,
        "severity": severity,
        "message": message,
    }
    if slide:
        result["slide"] = slide
    if details:
        result["details"] = details
    return result


def _outline_points(item: Mapping[str, Any]) -> List[str]:
    return _values(item.get("keyPoints") or item.get("content") or item.get("nodes"))


def _title_matches(left: str, right: str) -> bool:
    a, b = _compact(left), _compact(right)
    if not a or not b:
        return False
    if a in b or b in a:
        return True
    shorter = min(len(a), len(b))
    if shorter < 4:
        return False
    tokens_a = {a[i:i + 2] for i in range(0, len(a) - 1)}
    tokens_b = {b[i:i + 2] for i in range(0, len(b) - 1)}
    return len(tokens_a & tokens_b) / max(1, len(tokens_a | tokens_b)) >= 0.35


def assess_outline_quality(source: str, outline: Mapping[str, Any]) -> Dict[str, Any]:
    items = [item for item in outline.get("items") or [] if isinstance(item, Mapping)]
    chapters = extract_source_chapters(source)
    titles = [_text(item.get("title")) for item in items]
    issues: List[Dict[str, Any]] = []
    missing = [chapter for chapter in chapters if not any(_title_matches(chapter, title) for title in titles)]
    if missing:
        issues.append(_issue(
            "SOURCE_CHAPTER_MISSING", "error", "大纲未覆盖资料中的明确章节：" + "、".join(missing),
            details={"chapters": missing},
        ))
    matched_chapter_positions = [
        next((position for position, chapter in enumerate(chapters) if _title_matches(chapter, title)), None)
        for title in titles
    ]
    ordered_positions = [position for position in matched_chapter_positions if position is not None]
    if ordered_positions != sorted(ordered_positions):
        issues.append(_issue(
            "SOURCE_CHAPTER_ORDER_MISMATCH", "error", "大纲页面顺序与资料章节顺序不一致",
            details={"matchedChapterPositions": ordered_positions},
        ))
    duplicate_titles = sorted({title for title in titles if title and titles.count(title) > 1})
    if duplicate_titles:
        issues.append(_issue(
            "DUPLICATE_OUTLINE_TITLE", "warning", "大纲存在重复页标题：" + "、".join(duplicate_titles),
            details={"titles": duplicate_titles},
        ))
    thin_pages: List[int] = []
    for index, item in enumerate(items, start=1):
        if len(_outline_points(item)) < 2 and str(item.get("type") or "").lower() not in {"cover", "catalog", "toc"}:
            thin_pages.append(index)
    if thin_pages:
        issues.append(_issue(
            "THIN_OUTLINE_PAGE", "warning", "大纲页缺少可展开的具体要点：" + ",".join(map(str, thin_pages)),
            details={"slides": thin_pages},
        ))
    chapter_coverage = 1.0 if not chapters else (len(chapters) - len(missing)) / len(chapters)
    return {
        "status": "failed" if any(i["severity"] == "error" for i in issues) else "partial" if issues else "complete",
        "slideCount": len(items),
        "sourceChapterCount": len(chapters),
        "coveredChapterCount": len(chapters) - len(missing),
        "chapterCoverage": round(chapter_coverage, 3),
        "issues": issues,
    }


def _slide_body_values(slide: Mapping[str, Any]) -> List[str]:
    values = _values(slide.get("content"))
    if not values:
        values = _values(slide.get("componentContent"))
    return [value for value in values if value]


def assess_content_quality(
    source: str,
    outline: Mapping[str, Any],
    slides: Sequence[Mapping[str, Any]],
    source_trace: Mapping[str, Any] | None = None,
) -> Dict[str, Any]:
    items = [item for item in outline.get("items") or [] if isinstance(item, Mapping)]
    slides_list = [slide for slide in slides if isinstance(slide, Mapping)]
    trace = dict(source_trace or build_source_trace("", source))
    issues: List[Dict[str, Any]] = []
    per_slide: List[Dict[str, Any]] = []
    if len(items) != len(slides_list):
        issues.append(_issue(
            "OUTLINE_SLIDE_COUNT_MISMATCH", "error",
            f"大纲页数 {len(items)} 与生成页数 {len(slides_list)} 不一致",
            details={"outlineCount": len(items), "slideCount": len(slides_list)},
        ))
    source_compact = _compact(source)
    seen_body: Dict[str, int] = {}
    for index, slide in enumerate(slides_list, start=1):
        item = items[index - 1] if index <= len(items) else {}
        kind = (_text(slide.get("type")) or _text(item.get("type"))).lower()
        title = _text(slide.get("title")) or _text(item.get("title"))
        body = _slide_body_values(slide)
        body_text = " ".join(body)
        page_issues: List[Dict[str, Any]] = []
        skip_density = any(token in kind for token in ("cover", "封面", "catalog", "目录", "toc"))
        if not skip_density:
            if not body:
                page_issues.append(_issue("MISSING_CONTENT", "error", "页面没有可见正文", index))
            elif len(body) < 2 or len(body_text) < 45:
                page_issues.append(_issue(
                    "THIN_PAGE_CONTENT", "warning", "页面正文过薄，可能只是大纲骨架", index,
                    pointCount=len(body), contentChars=len(body_text),
                ))
        outline_values = _outline_points(item)
        compact_body = [_compact(value) for value in body]
        compact_outline = [_compact(value) for value in outline_values]
        if compact_outline and compact_body and all(
            value in compact_outline or any(point and value.startswith(point) for point in compact_outline)
            for value in compact_body
        ):
            page_issues.append(_issue("OUTLINE_ONLY_CONTENT", "error", "页面正文基本等同于大纲要点，未形成讲解内容", index))
        if any(_META_RE.search(value) for value in body):
            page_issues.append(_issue("META_LANGUAGE", "warning", "页面出现本页目标/内容梳理式元话语", index))
        if "..." in body_text or "…" in body_text:
            page_issues.append(_issue("CONTENT_TRUNCATED", "warning", "页面正文含省略号，疑似被截断", index))
        leaked = [placeholder for placeholder in _PLACEHOLDERS if placeholder.lower() in " ".join(visible_texts(slide)).lower() and placeholder.lower() not in source.lower()]
        if leaked:
            page_issues.append(_issue(
                "TEMPLATE_PLACEHOLDER_LEAK", "error", "页面混入模板示例占位内容：" + "、".join(leaked), index,
                placeholders=leaked,
            ))
        evidence = _text(slide.get("sourceMaterial") or slide.get("sourceExcerpt"))
        if source and not evidence:
            page_issues.append(_issue("MISSING_SOURCE_EVIDENCE", "warning", "页面没有保留对应资料证据片段", index))
        if index <= len(items) and not _title_matches(title, _text(item.get("title"))):
            page_issues.append(_issue(
                "TITLE_OUTLINE_MISMATCH", "warning", "页面标题与确认大纲标题无法对应", index,
                slideTitle=title, outlineTitle=_text(item.get("title")),
            ))
        normalized_body = [_compact(value) for value in body if _compact(value)]
        duplicates = sorted({value for value in normalized_body if normalized_body.count(value) > 1})
        if duplicates:
            page_issues.append(_issue("DUPLICATE_PAGE_CONTENT", "error", "同页正文重复出现", index))
        for value in normalized_body:
            if len(value) >= 10:
                seen_body[value] = seen_body.get(value, 0) + 1
        page_record = {
            "slide": index,
            "title": title,
            "contentChars": len(body_text),
            "pointCount": len(body),
            "evidenceChars": len(evidence),
            "sourceEvidenceHash": hashlib.sha256(evidence.encode("utf-8")).hexdigest()[:16] if evidence else "",
            "issues": page_issues,
        }
        per_slide.append(page_record)
        issues.extend(page_issues)
    repeated_across_pages = [value for value, count in seen_body.items() if count > 1]
    if repeated_across_pages:
        issues.append(_issue(
            "REPEATED_CONTENT_ACROSS_PAGES", "warning", "相同正文跨页重复，可能存在槽位回填或批次复制",
            details={"count": len(repeated_across_pages)},
        ))
    outline_quality = assess_outline_quality(source, outline)
    issues.extend(outline_quality["issues"])
    chapters = extract_source_chapters(source)
    slide_positions = [
        next((position for position, chapter in enumerate(chapters) if _title_matches(chapter, record["title"])), None)
        for record in per_slide
    ]
    ordered_slide_positions = [position for position in slide_positions if position is not None]
    if ordered_slide_positions != sorted(ordered_slide_positions):
        issues.append(_issue(
            "SOURCE_CHAPTER_ORDER_MISMATCH", "error", "生成页面顺序与资料章节顺序不一致",
            details={"matchedChapterPositions": ordered_slide_positions},
        ))
    error_count = sum(1 for issue in issues if issue.get("severity") == "error")
    warning_count = sum(1 for issue in issues if issue.get("severity") == "warning")
    return {
        "status": "failed" if error_count else "partial" if warning_count else "complete",
        "sourceTrace": trace,
        "outline": outline_quality,
        "slides": per_slide,
        "issues": issues,
        "errorCount": error_count,
        "warningCount": warning_count,
        "contentChars": sum(record["contentChars"] for record in per_slide),
        "sourceEvidenceChars": sum(record["evidenceChars"] for record in per_slide),
    }


def quality_warning_messages(report: Mapping[str, Any]) -> List[str]:
    messages: List[str] = []
    for issue in report.get("issues") or []:
        if not isinstance(issue, Mapping):
            continue
        message = _text(issue.get("message"))
        if message:
            prefix = f"第{issue['slide']}页：" if issue.get("slide") else ""
            messages.append(prefix + message)
    return messages


__all__ = [
    "assess_content_quality",
    "assess_outline_quality",
    "build_source_trace",
    "extract_source_chapters",
    "quality_warning_messages",
    "visible_texts",
]
