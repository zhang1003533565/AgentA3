"""LayoutValidator: programmatic geometry/content checks for every slide.

Checks (spec §38-§53):
- OUT_OF_BOUNDS / GEOMETRY_CHANGED: elements inside the slide, template
  geometry untouched, locked elements untouched
- FONT_TOO_SMALL: font never below the per-role floor
- TEXT_OVERFLOW: estimated vertical/horizontal overflow + hard char cap
- ELEMENT_OVERLAP: cross-component content overlap (same-component nesting
  such as text-on-card is allowed)
- UNBALANCED_CARDS: sibling card bodies with extreme length differences
- CONTENT_TOO_DENSE: slide density level LOW/NORMAL/HIGH/OVERLOADED

This validator is programmatic and deterministic; visual QA is optional
and separate (see consistency_validator QA report).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Mapping, Optional, Tuple

from app.ppt_generation.template_model import (
    SLIDE_HEIGHT,
    SLIDE_WIDTH,
    SlideLayoutModel,
    TemplateElementModel,
    content_chars_for_element,
    estimate_lines,
)

DENSITY_LOW = 0.08
DENSITY_HIGH = 0.35
DENSITY_OVERLOADED = 0.50
CARD_BALANCE_RATIO = 3.0
OVERLAP_AREA_RATIO = 0.02
GEOMETRY_TOLERANCE = 0.5

# 参与重叠检测的元素类型（装饰/背景不参与）
_OVERLAP_TYPES = {"text", "text-list", "image", "table", "chart"}


@dataclass
class ValidationIssue:
    error_type: str
    element_id: str
    detail: str
    severity: str = "error"  # error | warning


@dataclass
class ValidationResult:
    issues: List[ValidationIssue] = field(default_factory=list)
    density_level: str = "NORMAL"
    fill_ratio: float = 0.0
    card_balance: Dict[str, float] = field(default_factory=dict)

    @property
    def has_errors(self) -> bool:
        return any(issue.severity == "error" for issue in self.issues)


def _node_text(node: Mapping[str, Any]) -> str:
    text = node.get("text")
    if isinstance(text, str) and text:
        return text
    runs = node.get("runs")
    if isinstance(runs, list) and runs and isinstance(runs[0], Mapping):
        return str(runs[0].get("text") or "")
    items = node.get("items")
    if isinstance(items, list):
        values = []
        for item in items:
            if isinstance(item, list):
                values.extend(str(run.get("text") or "") for run in item if isinstance(run, Mapping))
            elif isinstance(item, Mapping):
                values.append(str(item.get("text") or ""))
        return "\n".join(value for value in values if value)
    return ""


def _node_font_size(node: Mapping[str, Any]) -> float:
    font = node.get("font")
    if isinstance(font, Mapping):
        try:
            return float(font.get("size") or 0)
        except (TypeError, ValueError):
            pass
    runs = node.get("runs")
    if isinstance(runs, list) and runs and isinstance(runs[0], Mapping):
        run_font = runs[0].get("font")
        if isinstance(run_font, Mapping):
            try:
                return float(run_font.get("size") or 0)
            except (TypeError, ValueError):
                pass
    return 0.0


def _collect_named_nodes(root: Mapping[str, Any]) -> Dict[str, List[Tuple[Dict[str, Any], float, float]]]:
    """收集树中所有有 name 的节点（同名多次出现时按遍历顺序存列表），
    附带组件 position 偏移（用于绝对坐标）。节点为只读副本，修复引擎另行原地改树。
    """
    found: Dict[str, List[Tuple[Dict[str, Any], float, float]]] = {}

    def walk(node: Any, base_x: float, base_y: float) -> None:
        if isinstance(node, list):
            for item in node:
                walk(item, base_x, base_y)
            return
        if not isinstance(node, Mapping):
            return
        name = str(node.get("name") or "").strip()
        if name:
            found.setdefault(name, []).append((dict(node), base_x, base_y))
        position = node.get("position")
        if isinstance(position, Mapping):
            try:
                base_x = base_x + float(position.get("x") or 0)
                base_y = base_y + float(position.get("y") or 0)
            except (TypeError, ValueError):
                pass
        for key in ("components", "elements", "children"):
            if key in node:
                walk(node[key], base_x, base_y)
        if "child" in node:
            walk(node["child"], base_x, base_y)

    components = root.get("components") if isinstance(root, Mapping) else None
    if isinstance(components, list):
        for component in components:
            if not isinstance(component, Mapping):
                continue
            position = component.get("position")
            try:
                base_x = float(position.get("x") or 0) if isinstance(position, Mapping) else 0.0
                base_y = float(position.get("y") or 0) if isinstance(position, Mapping) else 0.0
            except (TypeError, ValueError):
                base_x = base_y = 0.0
            walk(component.get("elements") or [], base_x, base_y)
            walk(component.get("components") or [], base_x, base_y)
            walk(component.get("children") or [], base_x, base_y)
            walk(component.get("child"), base_x, base_y)
    else:
        elements = root.get("elements") if isinstance(root, Mapping) else None
        walk(elements or [], 0.0, 0.0)
    return found


def _absolute_box(node: Mapping[str, Any], base_x: float, base_y: float) -> Tuple[float, float, float, float]:
    """计算节点绝对包围盒（组件 position 偏移 + 节点 position/size）。"""
    position = node.get("position")
    size = node.get("size")
    try:
        x = base_x + (float(position.get("x") or 0) if isinstance(position, Mapping) else 0.0)
        y = base_y + (float(position.get("y") or 0) if isinstance(position, Mapping) else 0.0)
        width = float(size.get("width") or 0) if isinstance(size, Mapping) else 0.0
        height = float(size.get("height") or 0) if isinstance(size, Mapping) else 0.0
    except (TypeError, ValueError):
        return 0.0, 0.0, 0.0, 0.0
    return x, y, width, height


def _intersection_area(a: Tuple[float, float, float, float], b: Tuple[float, float, float, float]) -> float:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    if aw <= 0 or ah <= 0 or bw <= 0 or bh <= 0:
        return 0.0
    x_overlap = max(0.0, min(ax + aw, bx + bw) - max(ax, bx))
    y_overlap = max(0.0, min(ay + ah, by + bh) - max(ay, by))
    return x_overlap * y_overlap


def validate_slide(ui_tree: Mapping[str, Any], model: SlideLayoutModel) -> ValidationResult:
    result = ValidationResult()
    nodes = _collect_named_nodes(ui_tree)
    slide_w = model.width or SLIDE_WIDTH
    slide_h = model.height or SLIDE_HEIGHT

    for name, model_elements in model.elements.items():
        tree_entries = nodes.get(name) or []
        if not tree_entries:
            result.issues.append(ValidationIssue(
                error_type="GEOMETRY_CHANGED",
                element_id=name,
                detail="模板元素在合并后的树中缺失",
            ))
            continue
        if len(tree_entries) != len(model_elements):
            result.issues.append(ValidationIssue(
                error_type="GEOMETRY_CHANGED",
                element_id=name,
                detail=f"同名元素数量不一致 树={len(tree_entries)} 模板={len(model_elements)}",
            ))
        for index, element in enumerate(model_elements):
            entry = tree_entries[index] if index < len(tree_entries) else None
            if entry is None:
                continue
            node, base_x, base_y = entry
            _validate_element(node, base_x, base_y, element, model, slide_w, slide_h, result)

    _check_overlaps(nodes, model, result)
    _check_card_balance(nodes, model, result)
    _check_connector_targets(nodes, model, result)
    _check_density(nodes, model, result)
    return result


def _validate_element(
    node: Mapping[str, Any],
    base_x: float,
    base_y: float,
    element: TemplateElementModel,
    model: SlideLayoutModel,
    slide_w: float,
    slide_h: float,
    result: ValidationResult,
) -> None:
    name = element.name
    x, y, width, height = _absolute_box(node, base_x, base_y)

    # 边界检测。
    # 模板中的背景/装饰层可能使用出血（故意伸出画布），这属于模板原始
    # 几何，不应被质量门禁判为失败。仍然保留几何漂移检测：如果这些层
    # 被改动，下面的 GEOMETRY_CHANGED 会拦截；而模板内的可编辑元素一旦
    # 被移到画布外，仍按 OUT_OF_BOUNDS 处理。
    template_out_of_bounds = (
        element.x < -GEOMETRY_TOLERANCE
        or element.y < -GEOMETRY_TOLERANCE
        or element.x + element.width > slide_w + GEOMETRY_TOLERANCE
        or element.y + element.height > slide_h + GEOMETRY_TOLERANCE
    )
    if width > 0 and height > 0 and not template_out_of_bounds:
        if x < -GEOMETRY_TOLERANCE or y < -GEOMETRY_TOLERANCE \
                or x + width > slide_w + GEOMETRY_TOLERANCE \
                or y + height > slide_h + GEOMETRY_TOLERANCE:
            result.issues.append(ValidationIssue(
                error_type="OUT_OF_BOUNDS",
                element_id=name,
                detail=f"元素越界 box=({x:.0f},{y:.0f},{width:.0f}x{height:.0f})",
            ))

    # 几何漂移检测：模板快照对比
    if abs(x - element.x) > GEOMETRY_TOLERANCE or abs(y - element.y) > GEOMETRY_TOLERANCE \
            or abs(width - element.width) > GEOMETRY_TOLERANCE or abs(height - element.height) > GEOMETRY_TOLERANCE:
        result.issues.append(ValidationIssue(
            error_type="GEOMETRY_CHANGED",
            element_id=name,
            detail=f"几何与模板不一致 diff=(dx={x - element.x:.1f},dy={y - element.y:.1f},"
                   f"dw={width - element.width:.1f},dh={height - element.height:.1f})",
        ))

    if element.locked:
        font_size = _node_font_size(node)
        if element.font_size > 0 and abs(font_size - element.font_size) > GEOMETRY_TOLERANCE:
            result.issues.append(ValidationIssue(
                error_type="GEOMETRY_CHANGED",
                element_id=name,
                detail="锁定元素字号被修改",
            ))
        return

    # 可写文本元素：溢出与字号下限
    if element.element_type in {"text", "text-list"} and element.constraint is not None:
        text = _node_text(node)
        font_size = _node_font_size(node) or element.font_size
        if font_size > 0 and font_size < element.constraint.min_font_size - 0.05:
            result.issues.append(ValidationIssue(
                error_type="FONT_TOO_SMALL",
                element_id=name,
                detail=f"字号 {font_size:.1f} 低于下限 {element.constraint.min_font_size:.1f}",
            ))
        if text:
            if _looks_like_template_placeholder(text):
                # 模板自带占位文本（英文占位/缩写角标）会在合并时被 AI 内容替换，
                # 其本身可能超出几何容量（模板作者笔误），不参与溢出判定
                return
            # 字数上限按模板首选字号计算。标题在缩小字号后可以容纳更多
            # 中文字符；此时几何行数/宽度才是最终约束，不能再用首选字号
            # 下的硬上限把已经适配好的标题重新判成溢出。
            content_chars = content_chars_for_element(text, element)
            exceeds_char_cap = content_chars > element.constraint.hard_max_chars
            if (
                exceeds_char_cap
                and not (
                    font_size < element.constraint.preferred_font_size
                    and element.semantic_role in {"page_title", "section_title", "page_subtitle"}
                )
            ):
                result.issues.append(ValidationIssue(
                    error_type="TEXT_OVERFLOW",
                    element_id=name,
                    detail=f"内容字符数 {content_chars} 超过硬上限 {element.constraint.hard_max_chars}",
                ))
            # 内容修复可能已将字号压到角色下限；排版校验必须使用节点当前字号，
            # 否则校验仍按模板原字号测量，合法的缩字结果会被误判为溢出。
            lines, vertical, horizontal = estimate_lines(text, element, font_size=font_size)
            if vertical or horizontal:
                result.issues.append(ValidationIssue(
                    error_type="TEXT_OVERFLOW",
                    element_id=name,
                    detail=f"文本溢出 lines={lines} 上限={element.constraint.max_lines} "
                           f"垂直={'是' if vertical else '否'} 水平={'是' if horizontal else '否'}",
                    # 显式 max_length 不能覆盖真实几何：模板元数据可能比
                    # 实际字体度量更宽松。真实渲染仍会裁切/溢出，因此必须
                    # 进入 RepairEngine，而不是以 warning 继续导出。
                    severity="error",
                ))


def _check_overlaps(nodes: Dict[str, List[Tuple[Dict[str, Any], float, float]]], model: SlideLayoutModel, result: ValidationResult) -> None:
    candidates: List[Tuple[str, TemplateElementModel, Tuple[float, float, float, float]]] = []
    for name, model_elements in model.elements.items():
        tree_entries = nodes.get(name) or []
        for index, element in enumerate(model_elements):
            if element.locked or element.element_type not in _OVERLAP_TYPES:
                continue
            if index >= len(tree_entries):
                continue
            node, base_x, base_y = tree_entries[index]
            box = _absolute_box(node, base_x, base_y)
            if box[2] <= 0 or box[3] <= 0:
                continue
            candidates.append((name, element, box))
    for i in range(len(candidates)):
        for j in range(i + 1, len(candidates)):
            name_a, element_a, box_a = candidates[i]
            name_b, element_b, box_b = candidates[j]
            if element_a.component_id == element_b.component_id:
                continue  # 同组件内叠加（卡片上的文字/图标）属于模板设计
            area = _intersection_area(box_a, box_b)
            if area <= 0:
                continue
            smaller = min(box_a[2] * box_a[3], box_b[2] * box_b[3])
            if smaller <= 0:
                continue
            # 模板本身可能把标题压在卡片、图片或装饰层上，这是设计关系，
            # 不是内容生成后产生的碰撞。只要当前节点几何没有被改动，
            # GEOMETRY_CHANGED 会负责保护模板位置；这里跳过与模板快照
            # 已经存在的同类重叠，避免把正常版式误报为“无法排版”。
            template_box_a = (
                element_a.x,
                element_a.y,
                element_a.width,
                element_a.height,
            )
            template_box_b = (
                element_b.x,
                element_b.y,
                element_b.width,
                element_b.height,
            )
            template_area = _intersection_area(template_box_a, template_box_b)
            template_smaller = min(
                element_a.width * element_a.height,
                element_b.width * element_b.height,
            )
            if template_smaller > 0 and template_area / template_smaller > OVERLAP_AREA_RATIO:
                continue
            if area / smaller > OVERLAP_AREA_RATIO:
                result.issues.append(ValidationIssue(
                    error_type="ELEMENT_OVERLAP",
                    element_id=name_a,
                    detail=f"与 {name_b} 非法重叠 {area / smaller * 100:.0f}%",
                ))


def _check_card_balance(nodes: Dict[str, List[Tuple[Dict[str, Any], float, float]]], model: SlideLayoutModel, result: ValidationResult) -> None:
    for component_id, members in model.card_groups.items():
        lengths: List[Tuple[str, int]] = []
        for name, index in members:
            element = model.element(name, index)
            if element is None or element.element_type not in {"text", "text-list"}:
                continue
            entries = nodes.get(name) or []
            if index >= len(entries):
                continue
            text = _node_text(entries[index][0]).strip()
            if not text:
                continue
            if _looks_like_template_placeholder(text):
                # 模板自带占位文本（英文占位/数字角标）不参与平衡判定，
                # 避免在未填充的版式上误报 UNBALANCED_CARDS
                lengths = []
                break
            lengths.append((name, len(text)))
        if len(lengths) < 2:
            continue
        lengths.sort(key=lambda item: item[1])
        ratio = lengths[-1][1] / max(1, lengths[0][1])
        result.card_balance[component_id] = round(ratio, 2)
        if ratio > CARD_BALANCE_RATIO:
            result.issues.append(ValidationIssue(
                error_type="UNBALANCED_CARDS",
                element_id=lengths[-1][0],
                detail=f"卡片内容长度失衡 max/min={ratio:.1f} ({lengths[0][1]} vs {lengths[-1][1]} 字)",
                # 卡片长短不一致影响观感，但不会证明页面无法渲染；
                # 交给 QA/前端提示，不阻断 PPTX 生成。
                severity="warning",
            ))


def _check_connector_targets(
    nodes: Dict[str, List[Tuple[Dict[str, Any], float, float]]],
    model: SlideLayoutModel,
    result: ValidationResult,
) -> None:
    """Ensure every high-confidence connector target still has visible text.

    A line whose target heading was cleared is not a harmless empty slot: it
    becomes a floating visual fragment in both preview and exported PPTX. The
    relation is inferred once from the immutable template geometry, while the
    actual text is checked on the merged UI tree.
    """
    for relation in model.connector_targets:
        entries = nodes.get(relation.target_name) or []
        if relation.target_index >= len(entries):
            result.issues.append(ValidationIssue(
                error_type="CONNECTOR_TARGET_MISSING",
                element_id=f"{relation.target_name}[{relation.target_index}]",
                detail="连接线的目标文字槽位在合并后的页面树中缺失",
            ))
            continue
        node = entries[relation.target_index][0]
        if _node_text(node).strip():
            continue
        result.issues.append(ValidationIssue(
            error_type="CONNECTOR_TARGET_EMPTY",
            element_id=f"{relation.target_name}[{relation.target_index}]",
            detail=(
                f"连接线 #{relation.connector_index} 指向空文字槽位"
                f"（{relation.target_name}[{relation.target_index}]）"
            ),
        ))


def _looks_like_template_placeholder(text: str) -> bool:
    """模板占位识别：不含中文字符、去除标点后基本是 ASCII 字母数字的文本，
    视为模板自带占位（英文占位/数字角标），不参与卡片平衡判定。"""
    if not text:
        return True
    if re.search(r"[\u4e00-\u9fff]", text):
        return False  # 含中文 → AI 生成内容
    stripped = re.sub(r"[^A-Za-z0-9]", "", text)
    return bool(stripped) and len(stripped) >= len(text) * 0.6


def _check_density(nodes: Dict[str, List[Tuple[Dict[str, Any], float, float]]], model: SlideLayoutModel, result: ValidationResult) -> None:
    total_area = max(1.0, model.width * model.height)
    text_area = 0.0
    for name, model_elements in model.elements.items():
        tree_entries = nodes.get(name) or []
        for index, element in enumerate(model_elements):
            if element.element_type not in {"text", "text-list"} or element.locked or element.width <= 0 or element.height <= 0:
                continue
            if index >= len(tree_entries):
                continue
            text = _node_text(tree_entries[index][0])
            if not text:
                continue
            lines, _, _ = estimate_lines(text, element)
            if lines <= 0:
                continue
            line_h = element.font_size * (element.line_height if element.line_height >= 0.4 else 1.2)
            text_area += min(lines * line_h, element.height + 2.0) * element.width
    fill_ratio = text_area / total_area
    if fill_ratio >= DENSITY_OVERLOADED:
        result.density_level = "OVERLOADED"
        result.issues.append(ValidationIssue(
            error_type="CONTENT_TOO_DENSE",
            element_id="",
            detail=f"页面密度 {fill_ratio:.0%} 达到 OVERLOADED",
            # 密度是可读性提示，不等同于几何越界/文本溢出。
            # 将它作为硬错误会因为多个正常文本框面积叠加而直接阻断整份 PPT，
            # 但并不能证明页面无法渲染；真正的溢出仍由 TEXT_OVERFLOW 硬校验拦截。
            severity="warning",
        ))
    elif fill_ratio >= DENSITY_HIGH:
        result.density_level = "HIGH"
    elif fill_ratio < DENSITY_LOW:
        result.density_level = "LOW"
    else:
        result.density_level = "NORMAL"
    result.fill_ratio = round(fill_ratio, 4)


__all__ = ["ValidationIssue", "ValidationResult", "validate_slide", "DENSITY_LOW", "CARD_BALANCE_RATIO"]
