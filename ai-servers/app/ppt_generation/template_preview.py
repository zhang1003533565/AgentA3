"""Render real layout previews for embedded Presenton templates.

The template picker shows every layout of a template as a real 1280x720
rendered page (background, fonts, decorations included) instead of text-slot
placeholders. Each template is rendered once through the vendored Node
runtime, then cached on disk and served as static PNG bytes.
"""

from __future__ import annotations

import json
import logging
import os
import subprocess
import threading
import uuid
from copy import deepcopy
from pathlib import Path
from typing import Any, Dict, List, Mapping, Optional, Tuple

logger = logging.getLogger(__name__)

_ROOT = Path(__file__).resolve().parents[2]
_RUNTIME = _ROOT / "presenton_runtime"
_SCRIPT = _RUNTIME / "src" / "render.mjs"
_TEMPLATES = Path(__file__).resolve().parent / "assets" / "templates"
_PREVIEW_ROOT = Path(os.getenv("AI_EXPORT_ROOT", str(_ROOT / "data" / "ai-exports"))) / "presenton-runtime" / "template-previews"

_RENDER_TIMEOUT_SECONDS = max(60, int(os.getenv("PPT_TEMPLATE_PREVIEW_TIMEOUT_SECONDS") or "180"))

# Sample text injected into empty text slots so previews look like a real
# presentation. Rules are matched against the component name; the first hit
# wins, so keep specific keywords before generic ones.
_SAMPLE_TEXT_RULES: List[Tuple[str, str]] = [
    ("quote_mark", "\u201d"),
    ("quote_accent", "\u201d"),
    ("metric_unit", "%"),
    ("marker", "•"),
    ("dots", "•••"),
    ("folio", "01"),
    ("pagination", "01"),
    ("number", "01"),
    ("index", "01"),
    ("initials", "李"),
    ("card_accent", "★"),
    ("square_mark", "•"),
    ("avatar", "李"),
    ("badge_label", "考点"),
    ("badge_primary", "核心考点"),
    ("badge_secondary", "易错提醒"),
    ("person_name", "李同学"),
    ("presenter_name", "李同学"),
    ("profile_name", "李同学"),
    ("author", "李同学 · 软件工程"),
    ("attribution_name", "李同学"),
    ("attribution_detail", "计算机学院 · 软件工程"),
    ("attribution_text", "李同学 · 软件工程"),
    ("review_attribution", "李同学 · 软件工程"),
    ("person_role", "软件工程 · 大一"),
    ("profile_role", "软件工程 · 大一"),
    ("profile_bio", "喜欢把复杂知识拆成简单框架，擅长整理复习资料。"),
    ("presenter_label", "汇报人"),
    ("presenter_metadata", "2026年8月 · 复习资料"),
    ("profile_metadata", "计算机学院 · 软件工程"),
    ("year", "2026"),
    ("date_label", "2026"),
    ("date_value", "2026年8月"),
    ("date", "2026"),
    ("duration", "40分钟"),
    ("price", "免费"),
    ("plan_name", "基础方案"),
    ("legend", "系列一"),
    ("table_header", "对比维度"),
    ("table_row", "示例条目"),
    ("footer_callout", "本资料由 AI 根据上传的学习材料自动整理生成，仅供参考。"),
    ("footer_left", "复习资料"),
    ("footer_page_label", "页面"),
    ("footer_page_marker", "第 3 页"),
    ("footer_page_value", "03"),
    ("footer_value", "03"),
    ("footer_number", "03"),
    ("footer_index", "01"),
    ("footer_marker", "•"),
    ("footer_slide_marker", "•"),
    ("footer_marker_text", "•"),
    ("footer_pagination", "01"),
    ("footer_text", "复习资料"),
    ("footer", "复习资料"),
    ("agenda_index", "01"),
    ("agenda_item_label", "01"),
    ("agenda_item_description", "线性表、栈与队列的核心概念梳理"),
    ("agenda_label", "目录 · CONTENTS"),
    ("timeline_marker", "•"),
    ("timeline_label", "第一阶段"),
    ("timeline_heading", "梳理知识框架"),
    ("timeline_body", "先通读教材目录，把章节结构画成思维导图，标记薄弱环节。"),
    ("milestone_label", "已完成"),
    ("milestone_title", "完成第一轮通读"),
    ("milestone_description", "通读教材并整理章节结构，标记需要重点复习的小节。"),
    ("stage_label", "STEP 01"),
    ("stage_title", "课前预习"),
    ("stage_description", "浏览本章小结与思考题，带着问题进入精读环节。"),
    ("step_marker", "01"),
    ("step_heading", "理解核心概念"),
    ("step_title", "理解核心概念"),
    ("step_description", "结合课堂例题理解定义，动手推导一遍关键公式。"),
    ("entry_number", "01"),
    ("entry_heading", "线性表与顺序存储"),
    ("entry_title", "线性表与顺序存储"),
    ("entry_description", "掌握顺序表与链表的结构差异、插入删除的时间复杂度。"),
    ("entry_summary", "掌握顺序表与链表的结构差异、插入删除的时间复杂度。"),
    ("cover_title", "数据结构期末复习"),
    ("cover_subtitle", "线性表 · 树 · 图 · 排序与查找全梳理"),
    ("gauge_value", "92%"),
    ("gauge_caption", "章节自测平均正确率"),
    ("metric_value", "92%"),
    ("large_metric_value", "92%"),
    ("lower_metric_value", "88"),
    ("inner_metric_value", "88"),
    ("outer_metric_value", "88"),
    ("middle_metric_value", "88"),
    ("large_value", "92%"),
    ("large_metric_caption", "最近一次章节自测的正确率"),
    ("metric_caption", "最近一次章节自测的正确率"),
    ("metric_detail", "较上次提升 12%"),
    ("metric_label", "正确率"),
    ("metric_heading", "自测成绩"),
    ("metric_description", "根据章节自测结果自动统计，可用于安排复习优先级。"),
    ("inner_metric_label", "平均分"),
    ("outer_metric_label", "平均分"),
    ("middle_metric_label", "平均分"),
    ("chart_panel_heading", "章节自测成绩对比"),
    ("quote_statement", "把零散的知识点串成体系，复习效率翻倍。"),
    ("quote_body", "把零散的知识点串成体系，复习效率翻倍。"),
    ("review_quote", "把零散的知识点串成体系，复习效率翻倍。"),
    ("testimonial_body", "把零散的知识点串成体系，复习效率翻倍。"),
    ("caption", "配图：知识结构示意"),
    ("slide_title", "课程复习要点精讲"),
    ("slide_heading", "核心考点"),
    ("slide_headline", "构建清晰的知识框架"),
    ("slide_subtitle", "第二单元 · 核心概念梳理"),
    ("slide_intro", "本页先把本章的知识结构串一遍，再逐个突破重点难点。"),
    ("slide_description", "围绕课堂讲义与历年真题整理的复习要点，适合考前快速回顾。"),
    ("main_title", "课程复习要点精讲"),
    ("main_heading", "本章知识地图"),
    ("main_headline", "构建清晰的知识框架"),
    ("section_title", "核心考点"),
    ("section_heading", "核心考点"),
    ("section_description", "围绕高频考点整理的复习要点与典型例题。"),
    ("section_body", "把每个考点的定义、适用场景和易错点整理成卡片，反复记忆。"),
    ("section_paragraph", "把每个考点的定义、适用场景和易错点整理成卡片，反复记忆。"),
    ("primary_title", "课程复习要点精讲"),
    ("primary_heading", "核心考点"),
    ("primary_headline", "构建清晰的知识框架"),
    ("header_title", "课程复习要点精讲"),
    ("header_subtitle", "第二单元 · 核心概念梳理"),
    ("header_description", "围绕课堂讲义整理的复习要点，适合考前快速回顾。"),
    ("header_text", "第二单元 · 核心概念梳理"),
    ("cover", "数据结构期末复习"),
    ("headline", "构建清晰的知识框架"),
    ("subtitle", "第二单元 · 核心概念梳理"),
    ("heading", "核心考点"),
    ("title", "课程复习要点精讲"),
    ("column_header", "重点概念"),
    ("column_heading", "重点概念"),
    ("column_body", "先理解定义，再做课后练习巩固。"),
    ("callout_title", "重点概念梳理"),
    ("callout_heading", "重点概念"),
    ("callout_label", "核心考点"),
    ("callout_body", "正文示例：先理解概念，再动手练习，最后总结易错点。"),
    ("callout_text", "正文示例：先理解概念，再动手练习，最后总结易错点。"),
    ("callout_caption", "配图：知识结构示意"),
    ("callout_description", "正文示例：先理解概念，再动手练习，最后总结易错点。"),
    ("card_title", "重点概念梳理"),
    ("card_heading", "重点概念"),
    ("card_label", "核心考点"),
    ("card_index", "01"),
    ("card_body", "正文示例：先理解概念，再动手练习，最后总结易错点。"),
    ("card_description", "正文示例：先理解概念，再动手练习，最后总结易错点。"),
    ("card_caption", "配图：知识结构示意"),
    ("card_detail_row", "适用场景：选择题、简答题"),
    ("card_supporting", "正文示例：先理解概念，再动手练习，最后总结易错点。"),
    ("card_primary", "核心要点"),
    ("card_secondary", "拓展说明"),
    ("upper_card_title", "重点概念梳理"),
    ("lower_card_title", "重点概念梳理"),
    ("upper_card_body", "正文示例：先理解概念，再动手练习，最后总结易错点。"),
    ("lower_card_body", "正文示例：先理解概念，再动手练习，最后总结易错点。"),
    ("item_label", "考点一"),
    ("item_heading", "重点概念"),
    ("item_body", "正文示例：先理解概念，再动手练习。"),
    ("item_description", "正文示例：先理解概念，再动手练习。"),
    ("list_item", "先通读教材目录，再逐章整理笔记"),
    ("feature_title", "自动生成复习框架"),
    ("feature_heading", "自动生成"),
    ("feature_description", "上传课堂资料后自动提炼章节结构与复习要点。"),
    ("features_label", "核心功能"),
    ("feature_badge", "新"),
    ("stack_heading", "本章小结"),
    ("stack_body", "本章小结：先梳理结构，再逐个突破重点难点，最后用真题检验复习效果。"),
    ("stacked_label", "本章小结"),
    ("summary_text", "本章小结：先梳理结构，再逐个突破重点难点。"),
    ("summary_paragraph", "本章小结：先梳理结构，再逐个突破重点难点，最后用真题检验复习效果。"),
    ("supporting_caption", "配图：知识结构示意"),
    ("supporting_header_text", "补充说明"),
    ("supporting_heading", "补充说明"),
    ("supporting_subheading", "使用建议"),
    ("supporting_subtitle", "把要点整理成卡片，反复记忆"),
    ("supporting_copy", "补充说明：示例文字用于展示版式效果，生成时会替换为真实内容。"),
    ("supporting_description", "补充说明：示例文字用于展示版式效果，生成时会替换为真实内容。"),
    ("supporting_paragraph", "补充说明：示例文字用于展示版式效果，生成时会替换为真实内容。"),
    ("supporting_text", "补充说明：示例文字用于展示版式效果，生成时会替换为真实内容。"),
    ("supporting_note", "补充说明：示例文字用于展示版式效果，生成时会替换为真实内容。"),
    ("supporting_summary", "补充说明：示例文字用于展示版式效果，生成时会替换为真实内容。"),
    ("supporting_statement", "补充说明：示例文字用于展示版式效果，生成时会替换为真实内容。"),
    ("supporting_sentence", "补充说明：示例文字用于展示版式效果，生成时会替换为真实内容。"),
    ("supporting_tagline", "补充说明：示例文字仅用于展示版式效果。"),
    ("intro_heading", "复习目标"),
    ("intro_headline", "明确本单元的复习目标"),
    ("intro_body", "本单元复习围绕线性表、栈与队列展开，目标是掌握结构差异与典型操作的时间复杂度。"),
    ("intro_description", "围绕课堂讲义整理的复习要点，适合考前快速回顾。"),
    ("intro_paragraph", "本单元复习围绕线性表、栈与队列展开，先梳理结构，再做课后练习巩固。"),
    ("intro_statement", "本单元复习围绕线性表、栈与队列展开，先梳理结构，再做课后练习巩固。"),
    ("intro_subtitle", "第二单元 · 核心概念梳理"),
    ("intro_summary", "本单元复习围绕线性表、栈与队列展开，掌握结构差异与典型操作。"),
    ("intro_text", "本单元复习围绕线性表、栈与队列展开，先梳理结构，再做课后练习巩固。"),
    ("detail_label", "章节"),
    ("detail_value", "第二章 线性表"),
    ("category_label", "核心章节"),
    ("center_topic_label", "线性表复习"),
    ("first_row_title", "顺序表"),
    ("second_row_title", "链表"),
    ("third_row_title", "循环链表"),
    ("fourth_row_title", "双向链表"),
    ("row_label", "线性表"),
    ("row_heading_text", "顺序表"),
    ("row_body_text", "支持随机访问，插入删除需要移动大量元素。"),
    ("row_title", "顺序表"),
    ("row_description", "支持随机访问，插入删除需要移动大量元素。"),
    ("row_primary_value", "O(1)"),
    ("row_secondary_value", "O(n)"),
    ("two_line_label", "核心考点"),
    ("note_label", "注"),
    ("note_description", "示例说明：以上内容生成时会替换为真实的学习资料。"),
    ("panel_heading", "知识结构总览"),
    ("panel_caption", "配图：本章知识结构示意，生成时会替换为真实内容。"),
    ("person_role", "软件工程 · 大一"),
    ("body", "这里是正文示例内容，用于展示该版式的排版效果；生成时会自动替换为真实的学习资料内容。"),
    ("paragraph", "这里是正文示例内容，用于展示该版式的排版效果；生成时会自动替换为真实的学习资料内容。"),
    ("description", "补充说明文字会显示在这里，帮助理解页面重点。"),
    ("copy", "这里是正文示例内容，用于展示该版式的排版效果。"),
    ("text", "这里是正文示例内容，用于展示该版式的排版效果。"),
    ("label", "核心考点"),
    ("value", "92%"),
    ("name", "李同学"),
]
_DEFAULT_SAMPLE_TEXT = "这里是示例文字，用于展示该版式的排版效果。"

# When the same text shows up on several cards within one layout, rotate
# through these variants so the preview reads like a real deck instead of
# copy-pasted placeholders.
_BODY_TEXT_VARIANTS = [
    "先理解概念定义，再结合例题巩固，最后总结易错点。",
    "对比两种结构的差异，记忆典型操作的时间复杂度。",
    "结合课堂例题理解定义，动手推导一遍关键公式。",
    "把知识点整理成卡片，利用碎片时间反复记忆。",
]
_TITLE_TEXT_VARIANTS = [
    "重点概念梳理", "典型例题解析", "易错点提醒",
    "高频考点速记", "章节结构导览", "自测与巩固",
]
_CHART_CATEGORIES = ["第一周", "第二周", "第三周", "第四周", "第五周", "第六周"]
_CHART_SERIES_NAMES = ["自测正确率", "练习得分", "复习时长"]

_locks_guard = threading.Lock()
_render_locks: Dict[str, threading.Lock] = {}


def _lock_for(template_id: str) -> threading.Lock:
    with _locks_guard:
        lock = _render_locks.get(template_id)
        if lock is None:
            lock = threading.Lock()
            _render_locks[template_id] = lock
        return lock


def _compact_sample_text(value: str, max_length: Any = None) -> str:
    """Trim sample text to the slot's max_length, cutting at punctuation."""
    text = str(value or "").strip()
    try:
        limit = int(max_length or 0)
    except (TypeError, ValueError):
        limit = 0
    if limit <= 0 or len(text) <= limit:
        return text
    candidate = text[:limit].rstrip()
    sentence_end = max(
        candidate.rfind("。"), candidate.rfind("，"),
        candidate.rfind("；"), candidate.rfind(" "),
    )
    if sentence_end >= max(4, int(limit * 0.5)):
        return candidate[:sentence_end + 1].rstrip("，； ")
    if limit <= 1:
        return candidate[:limit]
    return f"{candidate[:limit - 1].rstrip()}…"


def _sample_text_for(name: str, max_length: Any = None) -> str:
    lowered = name.lower()
    for keyword, text in _SAMPLE_TEXT_RULES:
        if keyword in lowered:
            return _compact_sample_text(text, max_length)
    try:
        limit = int(max_length or 0)
    except (TypeError, ValueError):
        limit = 0
    if 0 < limit <= 4:
        return "01"
    if 0 < limit <= 12:
        return "示例标题"
    return _compact_sample_text(_DEFAULT_SAMPLE_TEXT, max_length)


def _inject_sample_texts(node: Any, usage: Optional[Dict[str, int]] = None) -> None:
    if usage is None:
        usage = {}
    if isinstance(node, list):
        for item in node:
            _inject_sample_texts(item, usage)
        return
    if not isinstance(node, dict):
        return
    if str(node.get("type") or "").strip() == "text" and str(node.get("name") or "").strip():
        base = _sample_text_for(str(node["name"]), node.get("max_length"))
        seen = usage.get(base, 0)
        text = base
        if seen > 0 and base.isdigit() and len(base) <= 2:
            text = f"{seen + 1:02d}"
        elif seen > 0 and len(base) >= 6:
            variants = _BODY_TEXT_VARIANTS if len(base) > 12 else _TITLE_TEXT_VARIANTS
            text = _compact_sample_text(variants[(seen - 1) % len(variants)], node.get("max_length"))
        usage[base] = seen + 1
        node["text"] = text
        runs = node.get("runs")
        if isinstance(runs, list) and runs:
            node["runs"] = [
                {**run, "text": text if index == 0 else ""}
                if isinstance(run, dict) else run
                for index, run in enumerate(runs)
            ]
    for key in ("components", "elements", "children"):
        _inject_sample_texts(node.get(key), usage)
    _inject_sample_texts(node.get("child"), usage)


def _inject_chart_samples(node: Any) -> None:
    if isinstance(node, list):
        for item in node:
            _inject_chart_samples(item)
        return
    if not isinstance(node, dict):
        return
    if str(node.get("type") or "").strip() == "chart":
        categories = node.get("categories")
        if isinstance(categories, list) and categories:
            node["categories"] = [
                _CHART_CATEGORIES[i % len(_CHART_CATEGORIES)]
                if isinstance(item, str) else item
                for i, item in enumerate(categories)
            ]
        series = node.get("series")
        if isinstance(series, list):
            for index, item in enumerate(series):
                if isinstance(item, dict) and isinstance(item.get("name"), str):
                    item["name"] = _CHART_SERIES_NAMES[index % len(_CHART_SERIES_NAMES)]
    for key in ("components", "elements", "children"):
        _inject_chart_samples(node.get(key))
    _inject_chart_samples(node.get("child"))


def _sample_image_path(template_dir: Path) -> Optional[str]:
    static_dir = template_dir / "static"
    if not static_dir.is_dir():
        return None
    # Prefer real photos (jpeg assets shipped with the template). Chart-shaped
    # PNG samples (donut/line demo images) look wrong inside photo slots.
    photos = [
        p for p in static_dir.glob("*.jp*g")
        if p.stat().st_size > 0
    ]
    if photos:
        return str(max(photos, key=lambda p: p.stat().st_size).resolve())
    candidates = [
        p for p in static_dir.glob("*.png")
        if p.name != "thumbnail.png" and p.stat().st_size > 0
    ]
    if not candidates:
        return None
    return str(max(candidates, key=lambda p: p.stat().st_size).resolve())


def ensure_template_previews(template_id: str) -> List[Path]:
    """Render all layouts of a template once and cache PNGs on disk."""
    template_dir = (_TEMPLATES / template_id).resolve()
    template_path = template_dir / "template.json"
    if not template_dir.is_relative_to(_TEMPLATES.resolve()) or not template_path.is_file():
        raise FileNotFoundError(f"PPT 模板不存在: {template_id}")
    if not _SCRIPT.is_file():
        raise RuntimeError("Presenton HTML runtime is not built; run npm run build in presenton_runtime")

    payload = json.loads(template_path.read_text(encoding="utf-8"))
    layouts = [l for l in (payload.get("layouts") or []) if isinstance(l, dict)]
    if not layouts:
        raise ValueError(f"PPT 模板 {template_id} 没有可渲染的版式")

    cache_dir = (_PREVIEW_ROOT / template_id).resolve()
    expected = [cache_dir / f"{index}.png" for index in range(1, len(layouts) + 1)]
    if all(path.is_file() for path in expected):
        return expected

    with _lock_for(template_id):
        if all(path.is_file() for path in expected):
            return expected
        task_id = f"tplprev_{uuid.uuid4().hex}"
        scratch_root = _PREVIEW_ROOT.parent
        scratch_root.mkdir(parents=True, exist_ok=True)
        input_path = scratch_root / f"{task_id}.json"
        image_path = _sample_image_path(template_dir)
        slides = []
        for index, layout in enumerate(layouts, 1):
            ui = deepcopy(layout)
            usage: Dict[str, int] = {}
            _inject_sample_texts(ui.get("components"), usage)
            _inject_sample_texts(ui.get("elements"), usage)
            _inject_chart_samples(ui.get("components"))
            _inject_chart_samples(ui.get("elements"))
            slides.append({"index": index, "ui": ui, "imagePath": image_path})
        render_payload = {
            "taskId": task_id,
            "title": str(payload.get("name") or template_id),
            "templateRoot": str(template_dir),
            "template": payload,
            "slides": slides,
            "outputRoot": str(scratch_root),
            "pngOnly": True,
        }
        input_path.write_text(json.dumps(render_payload, ensure_ascii=False), encoding="utf-8")
        try:
            completed = subprocess.run(
                ["node", str(_SCRIPT), str(input_path)],
                cwd=str(_RUNTIME),
                check=True,
                capture_output=True,
                text=True,
                timeout=_RENDER_TIMEOUT_SECONDS,
            )
            result = json.loads(completed.stdout.strip().splitlines()[-1])
            slide_count = int(result.get("slideCount") or 0)
        except (OSError, subprocess.SubprocessError, ValueError) as exc:
            raise RuntimeError(f"模板版式渲染失败 ({template_id}): {exc}") from exc
        finally:
            input_path.unlink(missing_ok=True)

        cache_dir.mkdir(parents=True, exist_ok=True)
        saved: List[Path] = []
        try:
            for index in range(1, len(layouts) + 1):
                source = scratch_root / f"{task_id}-{index}.png"
                if not source.is_file():
                    raise RuntimeError(f"模板版式渲染不完整 ({template_id})，缺少第 {index} 页")
                target = expected[index - 1]
                source.replace(target)
                saved.append(target)
        finally:
            for scratch_path in scratch_root.glob(f"{task_id}*"):
                try:
                    scratch_path.unlink(missing_ok=True)
                except OSError:
                    pass
        logger.info("template layout previews ready: %s (%d pages)", template_id, len(saved))
        return saved


def get_template_layout_preview(template_id: str, slide_index: int) -> Tuple[bytes, str]:
    paths = ensure_template_previews(template_id)
    if not isinstance(slide_index, int) or slide_index < 1 or slide_index > len(paths):
        raise IndexError("版式页码超出范围")
    path = paths[slide_index - 1]
    if not path.is_file():
        raise FileNotFoundError("模板版式预览图不存在")
    return path.read_bytes(), "image/png"


def warm_up_previews(template_ids: List[str]) -> None:
    """Best-effort background warm-up; failures never break the service."""
    for template_id in template_ids:
        try:
            ensure_template_previews(template_id)
        except Exception:
            logger.exception("template preview warm-up failed: %s", template_id)


__all__ = ["ensure_template_previews", "get_template_layout_preview", "warm_up_previews"]
