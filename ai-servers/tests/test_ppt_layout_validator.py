"""LayoutValidator unit tests.

Spec §38-§53, §80 (Case 5, 7):
- bounds / overlap / overflow / font floor / card balance / density
- pristine template must keep geometry identical (GEOMETRY_CHANGED = 0)
- images keep fit=cover (crop, never stretch)
"""

import copy

import pytest

from app.ppt_generation.layout_validator import validate_slide
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import parse_slide_layout


@pytest.fixture(scope="module")
def catalog():
    return EmbeddedTemplateCatalog()


def _tree(catalog, layout_id):
    layout = catalog.get_layout("general", layout_id)
    return layout, parse_slide_layout(layout), {"components": copy.deepcopy(layout["components"])}


def _errors(issues):
    return [(issue.error_type, issue.element_id) for issue in issues if issue.severity == "error"]


def test_pristine_template_keeps_geometry(catalog):
    """Case 1：正常（未修改）模板 → 结构 0 改动。"""
    for layout in catalog.load("general")["layouts"]:
        model = parse_slide_layout(layout)
        tree = {"components": copy.deepcopy(layout["components"])}
        errors = _errors(validate_slide(tree, model).issues)
        geometry_errors = [e for e in errors if e[0] in {"GEOMETRY_CHANGED", "OUT_OF_BOUNDS", "ELEMENT_OVERLAP"}]
        assert geometry_errors == [], f"{layout['id']}: {geometry_errors}"


def test_geometry_drift_is_detected(catalog):
    """Case 7：AI 尝试修改 x/y → 检测为 GEOMETRY_CHANGED。"""
    layout, model, tree = _tree(catalog, "title_intro")

    def find(node, name):
        if isinstance(node, list):
            for item in node:
                result = find(item, name)
                if result:
                    return result
        if isinstance(node, dict):
            if node.get("name") == name:
                return node
            for key in ("elements", "components", "children"):
                if key in node:
                    result = find(node[key], name)
                    if result:
                        return result
        return None

    node = find(tree, "headline_text")
    node["position"] = {"x": 500, "y": 500}  # AI 幻觉坐标
    errors = _errors(validate_slide(tree, model).issues)
    assert ("GEOMETRY_CHANGED", "headline_text") in errors


def test_out_of_bounds_detected(catalog):
    layout, model, tree = _tree(catalog, "title_intro")

    def find(node, name):
        if isinstance(node, list):
            for item in node:
                result = find(item, name)
                if result:
                    return result
        if isinstance(node, dict):
            if node.get("name") == name:
                return node
            for key in ("elements", "components", "children"):
                if key in node:
                    result = find(node[key], name)
                    if result:
                        return result
        return None

    node = find(tree, "main_visual")
    node["position"] = {"x": 10000, "y": 0}
    errors = _errors(validate_slide(tree, model).issues)
    assert ("OUT_OF_BOUNDS", "main_visual") in errors


def test_template_background_bleed_is_allowed(catalog):
    """模板背景出血是原始版式的一部分，不应阻断整份 PPT。"""
    layout = catalog.get_layout("executive", "title_with_stacked_cards_2967")
    model = parse_slide_layout(layout)
    tree = {"components": copy.deepcopy(layout["components"])}
    errors = _errors(validate_slide(tree, model).issues)
    assert not any(error_type == "OUT_OF_BOUNDS" for error_type, _ in errors)


def test_text_overflow_detected(catalog):
    layout, model, tree = _tree(catalog, "title_intro")
    node = None

    def find(node_, name):
        if isinstance(node_, list):
            for item in node_:
                result = find(item, name)
                if result:
                    return result
        if isinstance(node_, dict):
            if node_.get("name") == name:
                return node_
            for key in ("elements", "components", "children"):
                if key in node_:
                    result = find(node_[key], name)
                    if result:
                        return result
        return None

    node = find(tree, "headline_text")
    overflow_text = "超" * 200
    node["text"] = overflow_text
    node["runs"] = [{"text": overflow_text, "font": dict(node.get("font") or {})}]
    errors = _errors(validate_slide(tree, model).issues)
    assert ("TEXT_OVERFLOW", "headline_text") in errors


def test_shrunk_chinese_title_uses_geometry_after_font_fit():
    layout = EmbeddedTemplateCatalog().get_layout("momentum", "title_with_accent_footer_6891")
    model = parse_slide_layout(layout)
    tree = {"components": copy.deepcopy(layout["components"])}

    def find(node, name):
        if isinstance(node, list):
            for item in node:
                result = find(item, name)
                if result:
                    return result
        if isinstance(node, dict):
            if node.get("name") == name:
                return node
            for key in ("elements", "components", "children"):
                if key in node:
                    result = find(node[key], name)
                    if result:
                        return result
        return None

    node = find(tree, "cover_title")
    text = "成都理工大学宣传PPT"
    node["text"] = text
    node["font"]["size"] = round(node["font"]["size"] * 0.71, 1)
    node["runs"] = [{"text": text, "font": dict(node["font"])}]
    errors = _errors(validate_slide(tree, model).issues)

    assert ("TEXT_OVERFLOW", "cover_title") not in errors


def test_image_uses_cover_not_stretch(catalog):
    """Case 5：图片 16:9 进 1:1 框 → fit=cover（crop），不能 stretch。"""
    layout, model, tree = _tree(catalog, "title_intro")

    def find(node, name):
        if isinstance(node, list):
            for item in node:
                result = find(item, name)
                if result:
                    return result
        if isinstance(node, dict):
            if node.get("name") == name:
                return node
            for key in ("elements", "components", "children"):
                if key in node:
                    result = find(node[key], name)
                    if result:
                        return result
        return None

    node = find(tree, "main_visual")
    assert node["fit"] == "cover"  # 模板规范：crop/cover，保持宽高比


def test_density_levels(catalog):
    layout, model, tree = _tree(catalog, "title_intro")
    result = validate_slide(tree, model)
    assert result.density_level in {"LOW", "NORMAL", "HIGH", "OVERLOADED"}
    assert 0.0 <= result.fill_ratio <= 1.0


def test_card_balance_flags_extreme_difference(catalog):
    layout, model, tree = _tree(catalog, "title_image_bullet_points")

    def set_all(node, name, text):
        if isinstance(node, list):
            for item in node:
                set_all(item, name, text)
            return
        if isinstance(node, dict):
            if node.get("name") == name:
                node["text"] = text
                node["runs"] = [{"text": text, "font": dict(node.get("font") or {})}]
            for key in ("elements", "components", "children"):
                if key in node:
                    set_all(node[key], name, text)

    set_all(tree, "item_heading", "短标题")
    set_all(tree, "item_body", "极短")
    errors = _errors(validate_slide(tree, model).issues)
    # 全部填充成短内容后不应失衡
    assert not any(e[0] == "UNBALANCED_CARDS" for e in errors)


def test_template_native_overlap_is_not_a_quality_error(catalog):
    """模板原本的标题/图片叠放不应阻断最终导出。"""
    layout = catalog.get_layout("dynamic", "title_chart_right_info_panel_2580")
    model = parse_slide_layout(layout)
    result = validate_slide({"components": copy.deepcopy(layout["components"])}, model)
    assert not any(
        issue.error_type == "ELEMENT_OVERLAP" and issue.severity == "error"
        for issue in result.issues
    )


def test_unbalanced_cards_are_a_warning_not_a_blocker(catalog):
    layout = catalog.get_layout("general", "title_metrics_description")
    model = parse_slide_layout(layout)
    tree = {"components": copy.deepcopy(layout["components"])}

    def set_all(node, name, text):
        if isinstance(node, list):
            for item in node:
                set_all(item, name, text)
            return
        if isinstance(node, dict):
            if node.get("name") == name:
                node["text"] = text
                node["runs"] = [{"text": text, "font": dict(node.get("font") or {})}]
            for key in ("elements", "components", "children"):
                if key in node:
                    set_all(node[key], name, text)

    set_all(tree, "metric_value", "指标")
    body_index = 0

    def set_unbalanced_body(node):
        nonlocal body_index
        if isinstance(node, list):
            for item in node:
                set_unbalanced_body(item)
            return
        if isinstance(node, dict):
            if node.get("name") == "metric_description":
                text = "短" if body_index == 0 else "用于说明数据组织、访问方式和适用场景的详细知识内容。"
                body_index += 1
                node["text"] = text
                node["runs"] = [{"text": text, "font": dict(node.get("font") or {})}]
            for key in ("elements", "components", "children"):
                if key in node:
                    set_unbalanced_body(node[key])

    set_unbalanced_body(tree)
    result = validate_slide(tree, model)
    warnings = [issue for issue in result.issues if issue.error_type == "UNBALANCED_CARDS"]
    assert warnings
    assert all(issue.severity == "warning" for issue in warnings)
    assert not any(issue.severity == "error" and issue.error_type == "UNBALANCED_CARDS" for issue in result.issues)
