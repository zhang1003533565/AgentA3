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
_PAGE_PATTERN = re.compile(
    r"(?i)(?:^|[_-])(?:page|pagenum|page[_-]?(?:number|num)|"
    r"slide[_-]?(?:number|num)|footer[_-]?(?:number|value))(?:$|[_-])"
)
# 组件名按 token 判断装饰，不能用简单子串：headline/headline_text_block
# 都包含 "line"，但它们是可编辑内容区，不是线条装饰。
_DECORATION_PATTERN = re.compile(
    r"(?i)(?:^|[_\-\s])(?:decoration|decor|accent|divider|shape|line|vector|"
    r"bg|background|canvas|overlay|shadow)(?:$|[_\-\s])"
)
_LOCKED_TYPES = {"vector", "shape", "line", "divider", "accent", "polygon", "rect", "circle"}

# Repeated local blocks (sections, timelines, callouts, etc.) have their own
# heading/body hierarchy. They must not be mistaken for the one page title
# merely because their slot name contains ``heading`` or ``title``.
_LOCAL_HEADING_SLOT_PATTERN = re.compile(
    r"(?i)(?:^|[_-])(?:section|callout|feature|item|point|step|timeline|milestone|panel|detail)"
    r"[_-](?:title|heading|label|name)(?:[_-]|$)"
)
_LOCAL_BODY_SLOT_PATTERN = re.compile(
    r"(?i)(?:^|[_-])(?:section|callout|feature|item|point|step|timeline|milestone|panel|detail)"
    r"[_-](?:body|description|detail|copy|text|content)(?:[_-]|$)"
)
_CONNECTOR_BODY_SLOT_PATTERN = re.compile(
    r"(?i)(?:body|description|detail|copy|content|text|paragraph|explanation|callout)"
)
_CONNECTOR_NON_CONTENT_PATTERN = re.compile(
    r"(?i)(?:marker|legend|index|number|icon|badge|value|unit)"
)

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
    # Card/step headings are short-title roles.  Their boxes are often only
    # one or two lines high, so the generic body floor (0.85) is too strict
    # for a bounded, geometry-verified shrink on CJK text.
    "card_title": 0.75,
    "card_body": 0.85,
    "bullet_body": 0.85,
    "metric_value": 0.85,
    "metric_label": 0.75,
    "metric_description": 0.85,
    "card_value": 0.85,
    "card_value_label": 0.75,
    "label": 0.75,
    "badge": 0.75,
    "default": 0.85,
}

# These roles may exceed the preferred-font character budget after a bounded
# shrink.  The final authority is still the measured geometry at the node's
# current font size; this set only prevents a preferred-size budget from
# re-failing an otherwise fitting title/label.
ADAPTIVE_TEXT_ROLES = frozenset({
    "page_title",
    "section_title",
    "page_subtitle",
    "card_title",
    "metric_label",
    "card_value_label",
    "label",
    "badge",
})

# 中文/全角字符宽 ≈ 字号；拉丁/数字 ≈ 0.55 字号；空格 0.3；其他标点折中
_CJK_RE = re.compile(r"[\u2e80-\u9fff\uff00-\uffef\u3000-\u303f\u2018-\u201f\u2026\u00b7]")
_LATIN_RE = re.compile(r"[A-Za-z0-9]")
# 内容字符统计：去掉行首项目符号/编号前缀与换行
_CONTENT_ONLY_RE = re.compile(r"(?m)^[•·\-*\d.、]+\s*|\n")


def count_content_chars(text: str) -> int:
    """内容字符数：项目符号前缀（'• '）与换行是版式结构，不算内容。"""
    return len(_CONTENT_ONLY_RE.sub("", str(text or "")))


def content_chars_for_element(text: str, element: "TemplateElementModel") -> int:
    """Count content units using the semantic contract of one slot.

    Compact metric/complexity notation uses punctuation as visual syntax. The
    geometry check still measures the punctuation's real width, but the
    template's character budget should not reject ``O(1)`` merely because the
    two parentheses were counted as extra content units.
    """
    value = count_content_chars(text)
    if element.semantic_role in {"metric_value", "card_value"}:
        return len(re.sub(r"[()\[\]{}]", "", str(text or "")))
    return value


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
    # Kept as a compatibility field for serialized constraints; content
    # fitting must never use ellipsis as a lossy fallback.
    allow_ellipsis: bool = False


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


@dataclass(frozen=True)
class ConnectorRelation:
    """A stable, inferred relation between a line endpoint and a text slot.

    Presenton templates currently store many connectors as anonymous vectors,
    so there is no template-authored ``target`` field to follow.  We only keep
    relations whose endpoint is geometrically adjacent to an editable heading
    or label.  Decorative vectors remain intentionally unbound.
    """

    connector_index: int
    component_id: str
    endpoint_index: int
    endpoint_x: float
    endpoint_y: float
    target_name: str
    target_index: int
    target_semantic_role: str
    distance: float


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
    # 匿名线条/向量到文本槽位的语义关系。未能可靠推断的装饰向量不进入此表。
    connector_targets: List[ConnectorRelation] = field(default_factory=list)
    # flex/group 渲染后的有效盒子；保留原始 x/y 不变，避免把动态布局误报成
    # 模板几何突变。键为 (name, occurrence)。
    effective_boxes: Dict[Tuple[str, int], Tuple[float, float, float, float]] = field(default_factory=dict)
    # 名称位于 grid/flex 重复组内时，填充器可以按实际内容裁剪组数量；
    # 这不是坐标漂移。几何校验仍会逐个检查保留下来的实例。
    dynamic_names: set[str] = field(default_factory=set)

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
    _refine_repeated_local_semantics(model)
    _normalize_card_groups(model)
    model.effective_boxes = _resolve_effective_boxes(layout_json)
    model.dynamic_names = _collect_dynamic_names(layout_json)
    model.connector_targets = _infer_connector_targets(layout_json, model)
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
    by_component_role: Dict[Tuple[str, str], List[Tuple[str, int]]] = {}
    for name, elements in model.elements.items():
        for index, element in enumerate(elements):
            if not element.mutable_text:
                continue
            if element.semantic_role not in {
                "card_title",
                "card_body",
                "card_value_label",
                "metric_value",
                "metric_label",
                "metric_description",
            }:
                continue
            by_component_role.setdefault(
                (element.component_id, element.semantic_role),
                [],
            ).append((name, index))
    for (component_id, semantic_role), members in by_component_role.items():
        if len(members) >= 2:
            normalized[f"{component_id}:{semantic_role}"] = members
    model.card_groups = normalized


def _refine_repeated_local_semantics(model: SlideLayoutModel) -> None:
    """Promote repeated local heading/body slots to card semantics.

    The template JSON frequently uses generic names such as
    ``section_heading`` and ``section_body`` inside a repeated component. A
    name-only classifier sees ``heading`` and assigns the page-title contract,
    which prevents the content filler from pairing the heading with its body.
    Repetition within the same component is the stable, template-independent
    signal that this is a local block rather than the page title.
    """
    component_name_counts: Dict[Tuple[str, str], int] = {}
    for name, elements in model.elements.items():
        for element in elements:
            if element.mutable_text:
                key = (element.component_id, name.lower())
                component_name_counts[key] = component_name_counts.get(key, 0) + 1

    for name, elements in model.elements.items():
        key_name = name.lower()
        for element in elements:
            if not element.mutable_text:
                continue
            if component_name_counts.get((element.component_id, key_name), 0) < 2:
                continue
            if _LOCAL_HEADING_SLOT_PATTERN.search(key_name):
                element.semantic_role = "card_title"
                element.role = "title"
            elif _LOCAL_BODY_SLOT_PATTERN.search(key_name):
                element.semantic_role = "card_body"
                element.role = "card"


def _iter_layout_nodes(
    nodes: Any,
    base_x: float,
    base_y: float,
    component_id: str,
):
    """Yield raw nodes with the same absolute-coordinate rules as the parser."""
    if isinstance(nodes, list):
        for node in nodes:
            yield from _iter_layout_nodes(node, base_x, base_y, component_id)
        return
    if not isinstance(nodes, Mapping):
        return
    position = nodes.get("position")
    node_x = base_x + (_to_float(position.get("x")) if isinstance(position, Mapping) else 0.0)
    node_y = base_y + (_to_float(position.get("y")) if isinstance(position, Mapping) else 0.0)
    yield nodes, node_x, node_y, component_id
    for key in ("elements", "components", "children"):
        if key in nodes:
            yield from _iter_layout_nodes(nodes[key], node_x, node_y, component_id)
    if "child" in nodes:
        yield from _iter_layout_nodes(nodes["child"], node_x, node_y, component_id)


def _connector_points(node: Mapping[str, Any], x: float, y: float) -> List[Tuple[float, float]]:
    node_type = str(node.get("type") or "").strip().lower()
    points = node.get("points")
    if node_type == "vector" and isinstance(points, list) and len(points) == 2 and not node.get("closed"):
        values: List[Tuple[float, float]] = []
        for point in points:
            if not isinstance(point, Mapping):
                return []
            values.append((x + _to_float(point.get("x")), y + _to_float(point.get("y"))))
        return values
    if node_type in {"line", "divider"}:
        size = node.get("size")
        if not isinstance(size, Mapping):
            return []
        width = _to_float(size.get("width"))
        height = _to_float(size.get("height"))
        if width <= 0 and height <= 0:
            return []
        if width >= height:
            center_y = y + height / 2.0
            return [(x, center_y), (x + width, center_y)]
        center_x = x + width / 2.0
        return [(center_x, y), (center_x, y + height)]
    return []


def _collect_dynamic_names(layout_json: Mapping[str, Any]) -> set[str]:
    """Collect named descendants of renderer-managed repeat containers.

    Presenton keeps the full set of template children in a grid/flex group,
    while content binding may remove trailing groups to match the actual
    number of items. Those missing occurrences are an allowed cardinality
    change; any occurrence that remains is still checked against its original
    geometry by ``LayoutValidator``.
    """
    names: set[str] = set()

    def walk(value: Any, dynamic: bool = False) -> None:
        if isinstance(value, list):
            for item in value:
                walk(item, dynamic)
            return
        if not isinstance(value, Mapping):
            return
        node_type = str(value.get("type") or "").lower()
        children = value.get("children")
        dynamic_here = dynamic or (
            node_type in {"grid", "flex"} and isinstance(children, list) and bool(children)
        )
        name = str(value.get("name") or "").strip()
        if name and dynamic_here:
            names.add(name)
        for key in ("components", "elements", "children"):
            if key in value:
                walk(value[key], dynamic_here)
        if "child" in value:
            walk(value["child"], dynamic_here)

    walk(layout_json.get("components") or [])
    return names


def _resolve_effective_boxes(layout_json: Mapping[str, Any]) -> Dict[Tuple[str, int], Tuple[float, float, float, float]]:
    """Resolve the small subset of flex geometry needed for connector QA.

    The source JSON keeps repeated flex children at ``position: 0`` and lets
    the renderer apply direction/gap at export time.  Reading those raw
    positions makes every repeated heading appear to share one y coordinate.
    This resolver records effective boxes separately; the immutable template
    snapshot used for geometry validation remains untouched.
    """
    boxes: Dict[Tuple[str, int], Tuple[float, float, float, float]] = {}
    occurrences: Dict[str, int] = {}

    def number(value: Any) -> float:
        return _to_float(value)

    def size_of(node: Mapping[str, Any]) -> Tuple[float, float]:
        size = node.get("size")
        if not isinstance(size, Mapping):
            return 0.0, 0.0
        return number(size.get("width")), number(size.get("height"))

    def distribute_children(node: Mapping[str, Any], node_x: float, node_y: float) -> None:
        children = node.get("children")
        if not isinstance(children, list):
            return
        width, height = size_of(node)
        direction = str(node.get("direction") or "row").lower()
        is_column = direction == "column"
        gap = number(node.get("gap"))
        sizes = [size_of(child) if isinstance(child, Mapping) else (0.0, 0.0) for child in children]
        main_sizes = [size[1] if is_column else size[0] for size in sizes]
        cross_sizes = [size[0] if is_column else size[1] for size in sizes]
        container_main = height if is_column else width
        container_cross = width if is_column else height
        content_main = sum(main_sizes) + max(0, len(children) - 1) * gap
        free_main = max(0.0, container_main - content_main)
        justify = str(node.get("justify_content") or "flex-start").lower().replace("-", "_")
        start = free_main / 2.0 if justify == "center" else free_main if justify in {"flex_end", "end"} else 0.0
        step_gap = gap
        if justify == "space_between" and len(children) > 1:
            step_gap = gap + free_main / (len(children) - 1)
        cursor = start
        align = str(node.get("align_items") or "flex_start").lower().replace("-", "_")
        for child, (child_width, child_height), cross_size in zip(children, sizes, cross_sizes):
            if not isinstance(child, Mapping):
                continue
            cross_offset = 0.0
            if align == "center":
                cross_offset = max(0.0, (container_cross - cross_size) / 2.0)
            elif align in {"flex_end", "end"}:
                cross_offset = max(0.0, container_cross - cross_size)
            if is_column:
                child_base_x = node_x + cross_offset
                child_base_y = node_y + cursor
            else:
                child_base_x = node_x + cursor
                child_base_y = node_y + cross_offset
            walk(child, child_base_x, child_base_y)
            cursor += (child_height if is_column else child_width) + step_gap

    def distribute_grid_children(node: Mapping[str, Any], node_x: float, node_y: float) -> None:
        children = node.get("children")
        if not isinstance(children, list) or not children:
            return
        width, height = size_of(node)
        try:
            columns = max(1, int(node.get("columns") or 1))
        except (TypeError, ValueError):
            columns = 1
        try:
            rows = max(1, int(node.get("rows") or ((len(children) + columns - 1) // columns)))
        except (TypeError, ValueError):
            rows = max(1, (len(children) + columns - 1) // columns)
        column_gap = number(node.get("column_gap"))
        row_gap = number(node.get("row_gap"))
        cell_width = max(0.0, (width - max(0, columns - 1) * column_gap) / columns)
        cell_height = max(0.0, (height - max(0, rows - 1) * row_gap) / rows)
        for index, child in enumerate(children):
            if not isinstance(child, Mapping):
                continue
            row = index // columns
            column = index % columns
            walk(
                child,
                node_x + column * (cell_width + column_gap),
                node_y + row * (cell_height + row_gap),
            )

    def walk(node: Any, base_x: float, base_y: float) -> None:
        if isinstance(node, list):
            for value in node:
                walk(value, base_x, base_y)
            return
        if not isinstance(node, Mapping):
            return
        position = node.get("position")
        node_x = base_x + (number(position.get("x")) if isinstance(position, Mapping) else 0.0)
        node_y = base_y + (number(position.get("y")) if isinstance(position, Mapping) else 0.0)
        name = str(node.get("name") or "").strip()
        if name:
            occurrence = occurrences.get(name, 0)
            occurrences[name] = occurrence + 1
            width, height = size_of(node)
            boxes[(name, occurrence)] = (node_x, node_y, width, height)
        node_type = str(node.get("type") or "").lower()
        if node_type == "flex" and isinstance(node.get("children"), list):
            distribute_children(node, node_x, node_y)
        elif node_type == "grid" and isinstance(node.get("children"), list):
            distribute_grid_children(node, node_x, node_y)
        for key in ("elements", "components"):
            if key in node:
                walk(node[key], node_x, node_y)
        if node_type not in {"flex", "grid"} and "children" in node:
            walk(node["children"], node_x, node_y)
        if "child" in node:
            walk(node["child"], node_x, node_y)

    for component in layout_json.get("components") or []:
        if not isinstance(component, Mapping):
            continue
        position = component.get("position")
        base_x = number(position.get("x")) if isinstance(position, Mapping) else 0.0
        base_y = number(position.get("y")) if isinstance(position, Mapping) else 0.0
        walk(component.get("elements") or [], base_x, base_y)
        walk(component.get("components") or [], base_x, base_y)
        walk(component.get("children") or [], base_x, base_y)
        walk(component.get("child"), base_x, base_y)
    return boxes


def _infer_connector_targets(
    layout_json: Mapping[str, Any],
    model: SlideLayoutModel,
) -> List[ConnectorRelation]:
    """Infer only high-confidence line-to-slot relations from template geometry.

    A connector is considered semantic when one endpoint sits just outside a
    mutable heading/label box and is vertically aligned with its center.  The
    thresholds scale with the target box/font, so this works across all slide
    sizes without template-specific coordinates or scale constants.
    """
    targets: List[Tuple[str, int, TemplateElementModel, Tuple[float, float, float, float]]] = []
    for name, elements in model.elements.items():
        for index, element in enumerate(elements):
            if (
                element.element_type not in {"text", "text-list"}
                or not element.mutable_text
                or element.locked
                or element.width <= 0
                or element.height <= 0
            ):
                continue
            preferred_heading = element.semantic_role in {
                "card_title",
                "section_title",
                "metric_label",
                "card_value_label",
            }
            name_key = name.lower()
            body_target = (
                element.semantic_role in {"card_body", "body", "bullet_body"}
                and _CONNECTOR_BODY_SLOT_PATTERN.search(name_key)
                and not _CONNECTOR_NON_CONTENT_PATTERN.search(name_key)
            )
            if not preferred_heading and not body_target:
                continue
            targets.append(
                (
                    name,
                    index,
                    element,
                    model.effective_boxes.get(
                        (name, index),
                        (element.x, element.y, element.width, element.height),
                    ),
                )
            )

    candidates: List[Tuple[int, str, int, List[Tuple[float, float]]]] = []
    connector_index = 0
    for component in layout_json.get("components") or []:
        if not isinstance(component, Mapping):
            continue
        component_id = str(component.get("id") or "")
        position = component.get("position")
        base_x = _to_float(position.get("x")) if isinstance(position, Mapping) else 0.0
        base_y = _to_float(position.get("y")) if isinstance(position, Mapping) else 0.0
        for node, node_x, node_y, _ in _iter_layout_nodes(
            component.get("elements") or [], base_x, base_y, component_id
        ):
            points = _connector_points(node, node_x, node_y)
            if len(points) == 2:
                candidates.append((connector_index, component_id, connector_index, points))
                connector_index += 1

    relations: List[ConnectorRelation] = []
    preferred_roles = {"card_title", "section_title", "metric_label", "card_value_label"}
    for candidate_index, component_id, _, points in candidates:
        # A vertical divider/axis is normally a layout boundary, not a line
        # pointing at a text slot. Only infer text bindings for connectors whose
        # dominant direction is horizontal; this keeps decorative grid rules
        # out of the semantic contract while retaining timeline/callout lines.
        delta_x = abs(points[1][0] - points[0][0])
        delta_y = abs(points[1][1] - points[0][1])
        if delta_x <= delta_y:
            continue
        best: Optional[Tuple[Tuple[int, float], int, str, int, TemplateElementModel, float, float]] = None
        for endpoint_index, (endpoint_x, endpoint_y) in enumerate(points):
            for name, target_index, element, box in targets:
                target_x, target_y, target_width, target_height = box
                center_y = target_y + target_height / 2.0
                vertical_tolerance = max(target_height * 0.75, element.font_size * 0.75, 4.0)
                vertical_distance = abs(endpoint_y - center_y)
                if vertical_distance > vertical_tolerance:
                    continue
                left_gap = target_x - endpoint_x
                right_gap = endpoint_x - (target_x + target_width)
                gap = left_gap if left_gap >= 0 else right_gap
                gap_limit = max(target_height * 2.0, element.font_size * 2.0, 16.0)
                if gap < 0 or gap > gap_limit:
                    continue
                role_priority = 0 if element.semantic_role in preferred_roles else 1
                score = vertical_distance / max(vertical_tolerance, 1.0) + gap / max(gap_limit, 1.0)
                ranking = (role_priority, score)
                if best is None or ranking < best[0]:
                    best = (
                        ranking,
                        endpoint_index,
                        name,
                        target_index,
                        element,
                        endpoint_x,
                        endpoint_y,
                    )
        if best is None:
            continue
        _, endpoint_index, name, target_index, element, endpoint_x, endpoint_y = best
        target_box = model.effective_boxes.get(
            (name, target_index),
            (element.x, element.y, element.width, element.height),
        )
        target_x, target_y, target_width, target_height = target_box
        horizontal_gap = (
            target_x - endpoint_x
            if endpoint_x < target_x
            else max(0.0, endpoint_x - (target_x + target_width))
        )
        distance = math.hypot(
            endpoint_y - (target_y + target_height / 2.0),
            horizontal_gap,
        )
        relations.append(
            ConnectorRelation(
                connector_index=candidate_index,
                component_id=component_id,
                endpoint_index=endpoint_index,
                endpoint_x=endpoint_x,
                endpoint_y=endpoint_y,
                target_name=name,
                target_index=target_index,
                target_semantic_role=element.semantic_role,
                distance=round(distance, 3),
            )
        )
    return relations


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
        # No content slot may silently discard user/model text. When bounded
        # fitting strategies fail, the original text is kept and the QA gate
        # reports the overflow instead of exporting an ellipsis.
        allow_ellipsis=False,
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
        and element.semantic_role in ADAPTIVE_TEXT_ROLES
    ):
        return True
    content_chars = content_chars_for_element(text, element)
    return content_chars <= element.constraint.hard_max_chars


__all__ = [
    "SLIDE_WIDTH",
    "SLIDE_HEIGHT",
    "TextConstraint",
    "TemplateElementModel",
    "ConnectorRelation",
    "SlideLayoutModel",
    "parse_slide_layout",
    "measure_text",
    "estimate_lines",
    "text_fits",
    "count_content_chars",
    "content_chars_for_element",
    "ADAPTIVE_TEXT_ROLES",
]
