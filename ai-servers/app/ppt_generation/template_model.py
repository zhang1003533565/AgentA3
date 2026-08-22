"""Template element model for the embedded PPT engine.

Parses a Presenton layout JSON into a flat element model with explicit
geometry, roles, and text constraints. The model is the single source of
truth for capacity estimation, overflow measurement, and layout validation:

    CONTENT MUST ADAPT TO TEMPLATE. TEMPLATE MUST NOT ADAPT TO CONTENT.

The renderer/validator never mutates template geometry; the model records
the original geometry so any drift can be detected and restored.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Mapping, Optional, Tuple

SLIDE_WIDTH = 1280.0
SLIDE_HEIGHT = 720.0

# 代码级声明（第 85 节）：系统默认禁止任何模板几何突变。
# 所有内容写入只允许替换文本/表格/图表数据；几何只允许"还原"到模板快照。
ALLOW_TEMPLATE_GEOMETRY_MUTATION = False

# 角色分类关键字：名称命中即归类（装饰/Logo/页码属于 LOCKED，任何 AI 输出不得触碰）
_TITLE_PATTERN = re.compile(r"(?i)title|headline|heading|subtitle|subheading|subject")
_BODY_PATTERN = re.compile(r"(?i)body|paragraph|description|content|details|text$|summary|intro|conclusion|main")
_CARD_PATTERN = re.compile(r"(?i)card|item|feature|point|metric|stat|highlight")
_LABEL_PATTERN = re.compile(r"(?i)label|tag|badge|caption|note|kicker|eyebrow")
_LOGO_PATTERN = re.compile(r"(?i)logo|brand|icon")
_PAGE_PATTERN = re.compile(r"(?i)page|pagenum|footer|slide_number")
# 组件名按 token 判断装饰，不能用简单子串：headline/headline_text_block
# 都包含 "line"，但它们是可编辑内容区，不是线条装饰。
_DECORATION_PATTERN = re.compile(
    r"(?i)(?:^|[_\-\s])(?:decoration|decor|accent|divider|shape|line|vector|"
    r"bg|background|canvas|overlay|shadow)(?:$|[_\-\s])"
)
_LOCKED_TYPES = {"vector", "shape", "line", "divider", "accent", "polygon", "rect", "circle"}

# 同角色最小字号缩水比：中文标题不能沿用“只缩小 10%”的英文版式假设。
# 标题槽位通常只有两行，先按实际字符宽度缩到可读范围，再决定是否截断。
# 正文/卡片仍保持原有的保守下限。
_ROLE_MIN_FONT_RATIO = {
    "title": 0.60,
    "subtitle": 0.75,
    "page_title": 0.60,
    "section_title": 0.65,
    "page_subtitle": 0.75,
    "body": 0.85,
    "card": 0.85,
    "card_title": 0.85,
    "card_body": 0.85,
    "bullet_body": 0.85,
    "metric_value": 0.85,
    "metric_label": 0.85,
    "metric_description": 0.85,
    "card_value": 0.85,
    "card_value_label": 0.85,
    "label": 0.85,
    "default": 0.85,
}

# 中文/全角字符宽 ≈ 字号；拉丁/数字 ≈ 0.55 字号；空格 0.3；其他标点折中
_CJK_RE = re.compile(r"[\u2e80-\u9fff\uff00-\uffef\u3000-\u303f\u2018-\u201f\u2026\u00b7]")
_LATIN_RE = re.compile(r"[A-Za-z0-9]")
# 内容字符统计：去掉行首项目符号/编号前缀与换行
_CONTENT_ONLY_RE = re.compile(r"(?m)^[•·\-*\d.、]+\s*|\n")


def count_content_chars(text: str) -> int:
    """内容字符数：项目符号前缀（'• '）与换行是版式结构，不算内容。"""
    return len(_CONTENT_ONLY_RE.sub("", str(text or "")))


@dataclass
class TextConstraint:
    preferred_font_size: float
    min_font_size: float
    max_font_size: float
    chars_per_line: float
    max_lines: int
    recommended_chars: int
    hard_max_chars: int
    allow_font_shrink: bool = True
    max_shrink_ratio: float = 0.85
    allow_ellipsis: bool = True


@dataclass
class TemplateElementModel:
    name: str
    element_type: str
    role: str
    x: float
    y: float
    width: float
    height: float
    font_size: float = 0.0
    font_family: str = ""
    color: str = ""
    bold: bool = False
    line_height: float = 0.0
    max_length: int = 0
    min_length: int = 0
    decorative: bool = False
    locked: bool = False
    mutable_text: bool = False
    component_id: str = ""
    original_text: str = ""
    parent_x: float = 0.0
    parent_y: float = 0.0
    constraint: Optional[TextConstraint] = None
    semantic_role: str = "body"


@dataclass
class SlideLayoutModel:
    layout_id: str
    width: float = SLIDE_WIDTH
    height: float = SLIDE_HEIGHT
    # 同名元素可能多次出现（模板里 4 组卡片共用 item_number/item_body 等名字），
    # 因此按名存列表，顺序与树的深度优先遍历一致（第 9 节稳定 ID 的折中：
    # 模板没有唯一 ID 时用「名 + 出现序号」定位）
    elements: Dict[str, List[TemplateElementModel]] = field(default_factory=dict)
    # 卡片组件 id -> [(元素名, 出现序号), ...]（用于卡片平衡检测）
    card_groups: Dict[str, List[Tuple[str, int]]] = field(default_factory=dict)

    def element(self, name: str, index: int = 0) -> Optional[TemplateElementModel]:
        values = self.elements.get(name)
        if values and index < len(values):
            return values[index]
        return None

    def occurrences(self, name: str) -> List[TemplateElementModel]:
        return self.elements.get(name) or []


def _char_width(ch: str, font_size: float) -> float:
    if _CJK_RE.match(ch):
        return font_size
    if _LATIN_RE.match(ch):
        return font_size * 0.55
    if ch in " \t":
        return font_size * 0.30
    return font_size * 0.50


def measure_text(text: str, font_size: float, box_width: float, line_height: float = 0.0) -> Tuple[int, float]:
    """近似测量：返回 (行数, 最宽行像素)。

    按字符宽度贪心换行：中文任意断行，西文在空格处换行（长单词不折行）。
    行高取模板 line_height，缺省 1.2 倍字号。
    """
    if not text:
        return 0, 0.0
    effective_line_height = line_height if line_height and line_height >= 0.4 else 1.2
    lines = 1
    current_width = 0.0
    max_width = 0.0
    for ch in text:
        if ch == "\n":
            lines += 1
            max_width = max(max_width, current_width)
            current_width = 0.0
            continue
        char_w = _char_width(ch, font_size)
        if current_width + char_w > box_width and current_width > 0:
            # 贪心换行：中文任意断行；西文同样按字符宽度估计（略保守，宁可多报溢出）
            lines += 1
            max_width = max(max_width, current_width)
            current_width = char_w
        else:
            current_width += char_w
    max_width = max(max_width, current_width)
    return lines, max_width


def estimate_lines(
    text: str,
    element: TemplateElementModel,
    font_size: Optional[float] = None,
) -> Tuple[int, bool, bool]:
    """返回 (行数, 垂直溢出, 水平溢出)。几何为 0 时视为单行且不溢出。"""
    if not text:
        return 0, False, False
    if element.width <= 0:
        return 1, False, False
    effective_font_size = font_size or element.font_size or 12.0
    lines, max_width = measure_text(text, effective_font_size, element.width, element.line_height)
    line_h = effective_font_size * (element.line_height if element.line_height >= 0.4 else 1.2)
    # Chromium/PPT 字体实际绘制的字形边界会比 line-height 略紧，模板中常见
    # 1~2px 的取整误差不应把已经缩到下限的单行标题判成溢出。
    vertical = element.height > 0 and lines * line_h > element.height + 2.0
    horizontal = max_width > element.width + 1.0
    return lines, vertical, horizontal


def _role_for(name: str, element_type: str, decorative: bool) -> str:
    if decorative or element_type in _LOCKED_TYPES:
        return "decoration"
    if _PAGE_PATTERN.search(name):
        return "page_number"
    if _LOGO_PATTERN.search(name):
        return "logo"
    if element_type == "chart":
        return "chart"
    if element_type == "table":
        return "table"
    if element_type == "image":
        return "image"
    if _TITLE_PATTERN.search(name):
        return "title"
    if _CARD_PATTERN.search(name):
        return "card"
    if _LABEL_PATTERN.search(name):
        return "label"
    if _BODY_PATTERN.search(name):
        return "body"
    return "body"


def _semantic_role_for(
    name: str,
    component_id: str,
    element_type: str,
    font_size: float,
    x: float,
    y: float,
    width: float,
    height: float,
    decorative: bool,
) -> str:
    """Infer the content contract from role, geometry and container semantics.

    ``role`` remains the coarse compatibility role used by the existing fitter.
    This second layer prevents metric slots and card bodies from being treated
    as interchangeable text boxes while keeping the first-round guards intact.
    """
    if decorative or element_type in _LOCKED_TYPES:
        return "decorative_text"
    key = str(name or "").strip().lower()
    component = str(component_id or "").strip().lower()
    if _PAGE_PATTERN.search(key):
        return "page_number"
    if _LOGO_PATTERN.search(key):
        return "logo"
    if element_type == "text-list":
        return "bullet_body"

    # Metric/value slots are checked before the generic card/title patterns.
    if re.search(r"(?:^|[_-])(metric|kpi|stat)(?:[_-]|$)", key):
        if re.search(r"(?:^|[_-])(value|number|amount|price|count)(?:[_-]|$)", key):
            return "metric_value"
        if re.search(r"(?:^|[_-])(label|name|title)(?:[_-]|$)", key):
            return "metric_label"
        if re.search(r"(?:^|[_-])(detail|description|delta|change|note)(?:[_-]|$)", key):
            return "metric_description"
        return "metric_description"
    if re.search(r"(?:^|[_-])card[_-]value[_-]label(?:[_-]|$)", key):
        return "card_value_label"
    if re.search(r"(?:^|[_-])card[_-](?:large[_-])?(?:value|price|number)(?:[_-]|$)", key):
        return "card_value"

    # Repeated callout/card headings are local card titles, not the page title.
    # This check must precede the generic ``heading`` rule below; otherwise a
    # three-card layout receives the full page title in every small callout.
    if re.search(r"(?:^|[_-])(?:card|callout|feature|item|point|step)[_-](?:title|heading|name|label)(?:[_-]|$)", key):
        return "card_title"
    if re.search(r"(?:^|[_-])(?:card|feature|item)[_-](?:title|heading|name|label)(?:[_-]|$)", key):
        return "card_title"
    if re.search(r"(?:^|[_-])(?:card|feature|item)[_-](?:body|description|detail|copy|text)(?:[_-]|$)", key):
        return "card_body"
    if "attribution" in component or re.search(r"(?:author|presenter|person[_-]?name|profile[_-]?name)", key):
        return "author"
    if re.search(r"(?:date|person[_-]?role|profile[_-]?(?:role|metadata))", key):
        return "date"
    if re.search(r"(?:badge|tag|kicker|eyebrow)", key):
        return "badge"
    if y > 540 and re.search(r"(?:supporting|note|caption|footer)", key):
        return "footer"
    if re.search(r"(?:section[_-]?title|chapter[_-]?title)", key):
        return "section_title"
    if re.search(r"(?:subtitle|subheading)", key) or ("supporting" in key and y <= 300):
        return "page_subtitle"
    if re.search(r"(?:^|[_-])(?:title|headline|heading|subject)(?:[_-]|$)", key):
        return "page_title"
    # Some real templates use names such as main_header_text. A large, high
    # text box near the top is a page title unless it belongs to a card.
    if font_size >= 38 and y <= 250 and not re.search(r"(?:card|metric|item|feature)", key + component):
        return "page_title"
    if font_size >= 28 and y <= 250 and "header" in key:
        return "page_title"
    return "body"


def semantic_content_contract(role: str) -> Dict[str, Any]:
    """Return the small, model-facing contract for a semantic slot."""
    contracts = {
        "page_title": {"kind": "title", "maxLines": 2, "bullet": False, "short": False},
        "section_title": {"kind": "title", "maxLines": 2, "bullet": False, "short": False},
        "page_subtitle": {"kind": "subtitle", "maxLines": 2, "bullet": False, "short": True},
        "card_title": {"kind": "short_title", "maxLines": 2, "bullet": False, "short": True},
        "card_body": {"kind": "body", "maxLines": 5, "bullet": True, "short": False},
        "bullet_body": {"kind": "bullet_body", "maxLines": 6, "bullet": True, "short": False},
        "metric_value": {"kind": "metric_value", "maxLines": 1, "bullet": False, "short": True},
        "metric_label": {"kind": "metric_label", "maxLines": 2, "bullet": False, "short": True},
        "metric_description": {"kind": "metric_description", "maxLines": 2, "bullet": False, "short": True},
        "card_value": {"kind": "card_value", "maxLines": 1, "bullet": False, "short": True},
        "card_value_label": {"kind": "card_label", "maxLines": 2, "bullet": False, "short": True},
        "badge": {"kind": "badge", "maxLines": 1, "bullet": False, "short": True},
        "author": {"kind": "author", "maxLines": 1, "bullet": False, "short": True},
        "date": {"kind": "metadata", "maxLines": 1, "bullet": False, "short": True},
    }
    return dict(contracts.get(role, {"kind": "body", "maxLines": 6, "bullet": True, "short": False}))


def parse_slide_layout(layout_json: Mapping[str, Any]) -> SlideLayoutModel:
    """把 Presenton layout JSON 解析成平面元素模型（绝对坐标）。

    元素绝对位置 = 组件 position + 元素 position；装饰元素通常没有 name，
    有 name 但类型为装饰（vector/shape 等）或名称命中装饰关键字也判为锁定。
    """
    model = SlideLayoutModel(layout_id=str(layout_json.get("id") or ""))
    components = layout_json.get("components") or []
    for component in components:
        if not isinstance(component, Mapping):
            continue
        component_id = str(component.get("id") or "")
        position = component.get("position") or {}
        base_x = _to_float(position.get("x")) if isinstance(position, Mapping) else 0.0
        base_y = _to_float(position.get("y")) if isinstance(position, Mapping) else 0.0
        component_locked = bool(_DECORATION_PATTERN.search(component_id))
        _collect_elements(
            component.get("elements") or [],
            model,
            component_id=component_id,
            base_x=base_x,
            base_y=base_y,
            component_locked=component_locked,
        )
    _normalize_card_groups(model)
    return model


def _collect_elements(
    nodes: Any,
    model: SlideLayoutModel,
    component_id: str,
    base_x: float,
    base_y: float,
    component_locked: bool,
) -> None:
    if isinstance(nodes, list):
        for node in nodes:
            _collect_elements(node, model, component_id, base_x, base_y, component_locked)
        return
    if not isinstance(nodes, Mapping):
        return
    element_type = str(nodes.get("type") or "")
    name = str(nodes.get("name") or "").strip()
    position = nodes.get("position")
    size = nodes.get("size")
    x = base_x + (_to_float(position.get("x")) if isinstance(position, Mapping) else 0.0)
    y = base_y + (_to_float(position.get("y")) if isinstance(position, Mapping) else 0.0)
    width = _to_float(size.get("width")) if isinstance(size, Mapping) else 0.0
    height = _to_float(size.get("height")) if isinstance(size, Mapping) else 0.0

    decorative = bool(nodes.get("decorative"))
    role = _role_for(name, element_type, decorative)
    locked = component_locked or decorative or role in {"decoration", "logo", "page_number"}
    mutable_text = role in {"title", "subtitle", "body", "card", "label"} and not locked

    font = nodes.get("font")
    if isinstance(font, Mapping):
        font_size = _to_float(font.get("size"))
        font_family = str(font.get("family") or "")
        color = str(font.get("color") or "")
        bold = bool(font.get("bold"))
        line_height = _to_float(font.get("line_height"))
    else:
        font_size = font_family = color = 0.0
        bold = False
        line_height = 0.0
    if font_size <= 0:
        runs = nodes.get("runs")
        if isinstance(runs, list) and runs and isinstance(runs[0], Mapping):
            run_font = runs[0].get("font")
            if isinstance(run_font, Mapping):
                font_size = _to_float(run_font.get("size"))
                font_family = str(run_font.get("family") or font_family)
                color = str(run_font.get("color") or color)
                bold = bool(run_font.get("bold")) or bold
                line_height = _to_float(run_font.get("line_height"))

    semantic_role = _semantic_role_for(
        name, component_id, element_type, font_size, x, y, width, height, decorative
    )
    if semantic_role in {"page_title", "section_title"}:
        role = "title"
    elif semantic_role == "page_subtitle":
        role = "subtitle"
    elif semantic_role in {"metric_label", "card_value_label", "badge"}:
        role = "label"
    elif semantic_role in {"metric_value", "metric_description", "card_value", "card_title", "card_body", "bullet_body"}:
        role = "card" if semantic_role not in {"card_title"} else "title"

    if name:
        element = TemplateElementModel(
            name=name,
            element_type=element_type,
            role=role,
            semantic_role=semantic_role,
            x=x,
            y=y,
            width=width,
            height=height,
            font_size=font_size,
            font_family=font_family,
            color=color,
            bold=bold,
            line_height=line_height,
            max_length=_to_int(nodes.get("max_length")),
            min_length=_to_int(nodes.get("min_length")),
            decorative=decorative,
            locked=locked,
            mutable_text=mutable_text,
            component_id=component_id,
            parent_x=base_x,
            parent_y=base_y,
        )
        if element_type in {"text", "text-list"} and element.font_size > 0:
            element.constraint = _build_text_constraint(element)
        if element_type in {"text", "text-list"}:
            runs = nodes.get("runs")
            if isinstance(runs, list) and runs and isinstance(runs[0], Mapping):
                element.original_text = str(runs[0].get("text") or "")
            elif isinstance(nodes.get("text"), str):
                element.original_text = nodes["text"]
            elif element_type == "text-list":
                items = nodes.get("items") or []
                values = []
                for item in items:
                    if isinstance(item, list):
                        values.extend(str(run.get("text") or "") for run in item if isinstance(run, Mapping))
                element.original_text = "\n".join(value for value in values if value)
        if element_type in {"text", "text-list"} and mutable_text and component_id and semantic_role in {
            "card_title", "card_body", "bullet_body", "metric_value", "metric_label", "metric_description", "card_value", "card_value_label"
        }:
            # 先按组件+语义层级收集；parse_slide_layout 最后只保留同类可比较组。
            occurrence_index = len(model.elements.get(name) or [])
            model.card_groups.setdefault(component_id, []).append((name, occurrence_index))
        model.elements.setdefault(name, []).append(element)

    # 子节点基准 = 当前节点绝对位置（嵌套 group 的子元素相对父元素定位）
    child_base_x, child_base_y = base_x, base_y
    if isinstance(position, Mapping):
        child_base_x = base_x + _to_float(position.get("x"))
        child_base_y = base_y + _to_float(position.get("y"))
    for key in ("elements", "components", "children"):
        if key in nodes:
            _collect_elements(nodes[key], model, component_id, child_base_x, child_base_y, component_locked)
    if "child" in nodes:
        _collect_elements(nodes["child"], model, component_id, child_base_x, child_base_y, component_locked)


def _normalize_card_groups(model: SlideLayoutModel) -> None:
    normalized: Dict[str, List[Tuple[str, int]]] = {}
    for component_id, members in model.card_groups.items():
        by_role: Dict[str, List[Tuple[str, int]]] = {}
        for name, index in members:
            element = model.element(name, index)
            if element is None:
                continue
            by_role.setdefault(element.semantic_role, []).append((name, index))
        for semantic_role, same_role in by_role.items():
            if len(same_role) >= 2:
                normalized[f"{component_id}:{semantic_role}"] = same_role
    model.card_groups = normalized


def _build_text_constraint(element: TemplateElementModel) -> TextConstraint:
    preferred = element.font_size or 12.0
    ratio = _ROLE_MIN_FONT_RATIO.get(
        element.semantic_role,
        _ROLE_MIN_FONT_RATIO.get(element.role, _ROLE_MIN_FONT_RATIO["default"]),
    )
    min_font = round(preferred * ratio, 1)
    line_h = element.line_height if element.line_height >= 0.4 else 1.2
    # 与 measure_text 的字符宽度口径一致（中文≈字号），且向下取整，
    # 保证「容量内」的文本按测量必能放下（否则容量与校验互相矛盾）
    chars_per_line = max(1.0, float(int(element.width / preferred))) if element.width > 0 else 1.0
    max_lines = max(1, int(element.height / (preferred * line_h))) if element.height > 0 else 1
    geometry_hard = max(1, int(chars_per_line * max_lines))
    explicit = element.max_length
    if explicit > 0:
        # 模板声明的 max_length 是字符数硬顶，但几何容量更严格时取小者：
        # 避免"字数达标但视觉上已溢出"（视觉 QA 实测 60px 标题 25 字需 3 行，
        # 超出模板 2 行版式意图）
        hard = min(explicit, geometry_hard)
        recommended = max(1, int(hard * 0.7))
    else:
        hard = geometry_hard
        recommended = max(1, int(hard * 0.7))
    return TextConstraint(
        preferred_font_size=preferred,
        min_font_size=min_font,
        max_font_size=preferred * 1.05,
        chars_per_line=chars_per_line,
        max_lines=max_lines,
        recommended_chars=recommended,
        hard_max_chars=hard,
        allow_font_shrink=True,
        max_shrink_ratio=ratio,
        # Formal page/section titles must be adapted semantically or reported
        # as unfit; an ellipsis is not acceptable user-facing PPT copy.
        allow_ellipsis=(max_lines <= 1 and element.semantic_role not in {"page_title", "section_title", "page_subtitle"}),
    )


def _to_float(value: Any) -> float:
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0.0


def _to_int(value: Any) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def text_fits(
    text: str,
    element: TemplateElementModel,
    font_size: Optional[float] = None,
) -> bool:
    """字符数与几何双重判定：超容量或溢出都视为不匹配。

    容量模型已按几何口径收紧（硬上限 = min(模板声明, 几何容量)），
    因此几何估算与字符数检查始终一致。字符预算按内容字计算：
    项目符号前缀（"• "）与换行不算内容，避免要点化结果被误判超限。
    """
    if not element.constraint:
        return True
    lines, vertical, horizontal = estimate_lines(text, element, font_size=font_size)
    if vertical or horizontal:
        return False
    # hard_max_chars is measured at the template's preferred字号.  A title
    # that has been deliberately shrunk can legitimately contain more
    # characters than that preferred-size budget, as long as its actual
    # measured lines still fit the box.  Keeping the original character cap
    # here caused the fitter to truncate valid Chinese titles after shrinking.
    if (
        font_size is not None
        and font_size < element.constraint.preferred_font_size
        and element.semantic_role in {"page_title", "section_title", "page_subtitle"}
    ):
        return True
    return count_content_chars(text) <= element.constraint.hard_max_chars


__all__ = [
    "SLIDE_WIDTH",
    "SLIDE_HEIGHT",
    "TextConstraint",
    "TemplateElementModel",
    "SlideLayoutModel",
    "parse_slide_layout",
    "measure_text",
    "estimate_lines",
    "text_fits",
    "count_content_chars",
]
