"""RepairEngine unit tests.

Spec §44-§51, §80 (Case 2, 3, 4, 6):
- repair loop bounded by MAX_REPAIR_ROUNDS
- repairHistory records round/error/element/strategy
- repairs change content first; font shrink bounded; geometry restored
- unknown elementId is rejected (never creates elements)
- no silent failure: partial status keeps issues visible
"""

import copy

import pytest

from app.ppt_generation.layout_validator import validate_slide
from app.ppt_generation.repair_engine import RepairEngine
from app.ppt_generation.repair_engine import _target_elements
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import parse_slide_layout, text_fits


@pytest.fixture(scope="module")
def catalog():
    return EmbeddedTemplateCatalog()


def _model(catalog, layout_id="title_intro"):
    layout = catalog.get_layout("general", layout_id)
    return layout, parse_slide_layout(layout)


def _set_text(tree, name, text):
    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if isinstance(node, dict):
            if node.get("name") == name:
                node["text"] = text
                node["runs"] = [{"text": text, "font": dict(node.get("font") or {})}]
            for key in ("elements", "components", "children"):
                if key in node:
                    walk(node[key])

    walk(tree)


def _get_text(tree, name):
    found = []

    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if isinstance(node, dict):
            if node.get("name") == name:
                found.append(node.get("text") or (node.get("runs") or [{}])[0].get("text", ""))
            for key in ("elements", "components", "children"):
                if key in node:
                    walk(node[key])

    walk(tree)
    return found


def test_short_content_needs_no_repair(catalog):
    """Case 1：正常短内容 → 0 轮修复，模板结构 0 改动。"""
    layout, model = _model(catalog)
    tree = {"components": copy.deepcopy(layout["components"])}
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)
    assert outcome.status == "clean"
    assert outcome.repair_count == 0
    assert not any(issue.severity == "error" for issue in outcome.final_issues)


def test_long_title_is_rewritten_not_unboundedly_shrunk(catalog):
    """Case 2：超长标题 → 压缩内容（程序化），不缩字、不破坏版式。"""
    layout, model = _model(catalog)
    tree = {"components": copy.deepcopy(layout["components"])}
    _set_text(tree, "headline_text", "超" * 60)
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)
    assert outcome.status == "repaired"
    assert outcome.repair_count >= 1
    final_text = _get_text(outcome.ui, "headline_text")[0]
    element = model.element("headline_text")
    assert len(final_text) <= element.constraint.hard_max_chars
    assert text_fits(final_text, element)
    # 字号未被修改（内容策略优先，模板几何/字号保持）
    assert "shrink-font" not in [entry["strategy"] for entry in outcome.history]
    assert not any(issue.error_type == "GEOMETRY_CHANGED" for issue in outcome.final_issues)


def test_long_card_body_is_compressed(catalog):
    """Case 3：卡片正文过长 → 压缩到容量内。"""
    layout, model = _model(catalog, "title_image_bullet_points")
    tree = {"components": copy.deepcopy(layout["components"])}
    long_body = "这是一段非常长的卡片正文内容用来测试内容压缩机制是否正常工作。" * 10
    _set_text(tree, "item_body", long_body)
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)
    for text in _get_text(outcome.ui, "item_body"):
        assert len(text) <= 107  # 模板 max_length
    assert not any(issue.error_type == "TEXT_OVERFLOW" and issue.severity == "error"
                   for issue in outcome.final_issues)


def test_repair_targets_all_equal_repeated_slots(catalog):
    """重复槽位模型内容相同，也必须逐个修复而不是只处理第一项。"""
    layout = catalog.get_layout("standard", "left_text_list_right_title_note_4909")
    model = parse_slide_layout(layout)
    tree = {"components": copy.deepcopy(layout["components"])}
    _set_text(tree, "stacked_label", "这是一个需要压缩到单行标签容量以内的较长页面标签")
    result = validate_slide(tree, model)
    issue = next(issue for issue in result.issues if issue.error_type == "TEXT_OVERFLOW")
    targets = _target_elements(model, issue)
    assert [index for _, index in targets if _.name == "stacked_label"] == [0, 1, 2, 3]
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)
    assert outcome.status != "partial"
    assert not any(
        issue.error_type == "TEXT_OVERFLOW" and issue.severity == "error"
        for issue in outcome.final_issues
    )


def test_bounded_font_shrink_is_applied_and_revalidated():
    """轻微几何溢出可在字号下限内修复，且校验使用修复后的字号。"""
    from app.ppt_generation.content_fitter import fit_text

    text = "中" * 3 + "\n\n" + "中" * 3
    layout = {"id": "shrink_fixture", "components": [{
        "id": "panel",
        "position": {"x": 0, "y": 0},
        "elements": [{
            "type": "text",
            "name": "small_label",
            "position": {"x": 0, "y": 0},
            "size": {"width": 100, "height": 62},
            "font": {"size": 20, "line_height": 1.2},
            "runs": [{"text": text, "font": {"size": 20}}],
        }],
    }]}
    model = parse_slide_layout(layout)
    element = model.element("small_label")
    result = fit_text(text, element, llm_call_budget=0)
    assert result.strategy == "shrink-font"
    assert result.fits

    outcome = RepairEngine().repair(
        {"components": copy.deepcopy(layout["components"])},
        model,
        llm_rewrite=None,
    )
    assert outcome.status == "repaired"
    assert outcome.ui["components"][0]["elements"][0]["font"]["size"] < 20
    assert not any(issue.severity == "error" for issue in outcome.final_issues)


def test_geometry_drift_is_restored(catalog):
    layout, model = _model(catalog)
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

    find(tree, "main_visual")["position"] = {"x": 999, "y": 999}
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)
    restored = find(outcome.ui, "main_visual")
    assert restored["position"] == {"x": 0, "y": 0}  # 相对组件偏移还原
    assert not any(issue.error_type in {"GEOMETRY_CHANGED", "OUT_OF_BOUNDS"}
                   and issue.severity == "error" for issue in outcome.final_issues)


def test_repair_rounds_are_bounded(catalog):
    """§47：MAX_REPAIR_ROUNDS 上限，防止无限循环。"""
    layout, model = _model(catalog, "title_image_bullet_points")
    tree = {"components": copy.deepcopy(layout["components"])}
    _set_text(tree, "item_body", "超" * 500)
    engine = RepairEngine(max_rounds=5)
    outcome = engine.repair(tree, model, llm_rewrite=None)
    assert outcome.repair_count <= 5
    assert len({entry["round"] for entry in outcome.history}) <= 5


def test_repair_history_records_reason(catalog):
    layout, model = _model(catalog)
    tree = {"components": copy.deepcopy(layout["components"])}
    _set_text(tree, "headline_text", "超" * 60)
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)
    assert outcome.history
    entry = outcome.history[0]
    assert entry["round"] >= 1
    assert entry["error"] == "TEXT_OVERFLOW"
    assert entry["element"] == "headline_text"
    assert entry["strategy"]


def test_no_silent_failure_on_extreme_content(catalog):
    """§49：修复失败时标记 partial 并保留错误信息，不静默导出。

    用合成极小元素（单字符容量）构造"内容策略与限幅缩字都无法容纳"
    的场景，验证失败路径可见。
    """
    from app.ppt_generation.template_model import TemplateElementModel, _build_text_constraint

    element = TemplateElementModel(
        name="tiny_slot",
        element_type="text",
        role="label",
        x=0,
        y=0,
        width=20.0,   # 比一个汉字（30px 字号）还窄
        height=30.0,
        font_size=30.0,
        line_height=1.2,
    )
    element.constraint = _build_text_constraint(element)
    assert element.constraint.hard_max_chars <= 1

    model = parse_slide_layout({"id": "synthetic", "components": [{
        "id": "panel",
        "position": {"x": 0, "y": 0},
        "elements": [{
            "type": "text",
            "name": "tiny_slot",
            "position": {"x": 0, "y": 0},
            "size": {"width": 20, "height": 30},
            "font": {"size": 30, "line_height": 1.2},
            "runs": [{"text": "超" * 500, "font": {"size": 30}}],
        }],
    }]})
    # 构造树：把 500 字塞进 20px 宽的槽位
    tree = {"components": [{
        "id": "panel",
        "position": {"x": 0, "y": 0},
        "elements": [{
            "type": "text",
            "name": "tiny_slot",
            "position": {"x": 0, "y": 0},
            "size": {"width": 20, "height": 30},
            "font": {"size": 30, "line_height": 1.2},
            "runs": [{"text": "超" * 500, "font": {"size": 30}}],
        }],
    }]}
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)
    assert outcome.status == "partial"
    assert any(issue.error_type == "TEXT_OVERFLOW" and issue.severity == "error"
               for issue in outcome.final_issues)  # 错误可见（不静默）
    assert isinstance(outcome.history, list)


def test_llm_rewrite_is_used_when_available(catalog):
    layout, model = _model(catalog)
    tree = {"components": copy.deepcopy(layout["components"])}
    _set_text(tree, "headline_text", "超" * 60)
    calls = []

    def llm_rewrite(text, constraint, mode):
        calls.append(mode)
        return "简洁标题"

    outcome = RepairEngine().repair(tree, model, llm_rewrite=llm_rewrite)
    assert calls, "修复应优先调用 LLM 重写"
    assert _get_text(outcome.ui, "headline_text")[0] == "简洁标题"
    assert not any(issue.severity == "error" for issue in outcome.final_issues)
