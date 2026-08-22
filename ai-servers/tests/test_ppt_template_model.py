"""Template model unit tests: element roles, constraints, capacity estimation.

Spec §5-§17: TemplateModel / SlideLayoutModel / element roles / TextConstraint
"""

import copy

import pytest

from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import (
    parse_slide_layout,
    text_fits,
    measure_text,
)


@pytest.fixture(scope="module")
def catalog():
    return EmbeddedTemplateCatalog()


def _layout(catalog, layout_id="title_intro"):
    return catalog.get_layout("general", layout_id)


def test_parses_real_template_elements(catalog):
    layout = _layout(catalog)
    model = parse_slide_layout(layout)
    assert model.layout_id == "title_intro"
    assert len(model.elements) >= 5
    headline = model.element("headline_text")
    assert headline is not None
    assert headline.role == "title"
    assert headline.width > 0 and headline.height > 0
    assert headline.font_size > 0


def test_duplicate_names_are_preserved_in_order(catalog):
    layout = _layout(catalog, "title_image_bullet_points")
    model = parse_slide_layout(layout)
    occurrences = model.occurrences("item_body")
    assert len(occurrences) == 4  # 4 组卡片共用同名槽位
    assert len({occ.x for occ in occurrences}) == 4  # 每组坐标不同


def test_decorations_and_backgrounds_are_locked(catalog):
    layout = _layout(catalog)
    model = parse_slide_layout(layout)
    # background_canvas 组件里的元素要么无 name 要么是装饰
    for name, elements in model.elements.items():
        for element in elements:
            if element.element_type in {"vector", "shape"}:
                assert element.locked, name


def test_headline_component_names_are_not_mistaken_for_line_decorations(catalog):
    """headline 中的 line 只是单词的一部分，标题/正文仍必须可编辑。"""
    dynamic = parse_slide_layout(
        catalog.get_layout("general", "title_description_bullet_points")
    )
    assert dynamic.element("main_heading").locked is False
    assert dynamic.element("main_heading").mutable_text is True

    swift = parse_slide_layout(
        catalog.get_layout("swift", "left_cover_text_right_image_2459")
    )
    assert swift.element("large_heading_text").locked is False
    assert swift.element("supporting_paragraph_text").locked is False


def test_footer_profile_callout_text_is_not_locked_as_decoration(catalog):
    """页脚个人信息卡里的文字仍是内容槽位，不能被组件名 footer 锁死。"""
    model = parse_slide_layout(
        catalog.get_layout("editorial", "title_slide_with_image_collage_and_footer")
    )
    profile_name = model.element("profile_name")
    assert profile_name is not None
    assert profile_name.locked is False
    assert profile_name.mutable_text is True


def test_image_slots_are_position_locked_but_not_text_mutable(catalog):
    layout = _layout(catalog)
    model = parse_slide_layout(layout)
    image = model.element("main_visual")
    assert image is not None and image.element_type == "image"
    assert not image.mutable_text


def test_text_constraint_respects_template_max_length(catalog):
    layout = _layout(catalog)
    model = parse_slide_layout(layout)
    headline = model.element("headline_text")
    assert headline.constraint is not None
    # 硬上限 = min(模板 max_length, 几何容量)，两者都不得被突破
    assert headline.constraint.hard_max_chars <= headline.max_length
    assert headline.constraint.hard_max_chars <= int(headline.constraint.chars_per_line * headline.constraint.max_lines)
    assert headline.constraint.min_font_size <= headline.constraint.preferred_font_size
    # 标题允许按中文实际宽度降到原字号的 60%，由几何适配器决定是否触发
    # 这一档，而不是把所有标题统一缩到同一个字号。
    assert headline.constraint.max_shrink_ratio == pytest.approx(0.60)


def test_body_min_font_floor_is_85_percent(catalog):
    layout = _layout(catalog)
    model = parse_slide_layout(layout)
    body = model.element("body_copy")
    assert body.constraint is not None
    assert body.constraint.max_shrink_ratio == pytest.approx(0.85)
    assert body.constraint.min_font_size == pytest.approx(body.constraint.preferred_font_size * 0.85, abs=0.1)


def test_measure_text_cjk_wider_than_latin():
    lines_cjk, width_cjk = measure_text("中文字符串测试", 20.0, 200.0)
    lines_latin, width_latin = measure_text("latin string", 20.0, 200.0)
    assert width_cjk > width_latin
    assert lines_cjk >= 1 and lines_latin >= 1


def test_long_english_word_wraps_measurement():
    # 超长单词（§82：英文长单词可能导致横向溢出）
    lines, width = measure_text("Supercalifragilisticexpialidocious", 24.0, 200.0)
    assert lines >= 2
    assert width <= 200.0 + 1e-6


def test_text_fits_truncation_and_geometry(catalog):
    layout = _layout(catalog)
    model = parse_slide_layout(layout)
    headline = model.element("headline_text")
    assert text_fits("短标题", headline)
    assert not text_fits("超" * (headline.constraint.hard_max_chars + 1), headline)


def test_empty_layout_parses_safely():
    model = parse_slide_layout({"id": "empty", "components": []})
    assert model.layout_id == "empty"
    assert model.elements == {}


def test_semantic_roles_keep_metric_and_card_contracts_separate(catalog):
    model = parse_slide_layout(
        catalog.get_layout("standard", "title_metric_card_grid_7973")
    )
    assert model.element("primary_heading").semantic_role == "page_title"
    assert model.element("metric_value").semantic_role == "metric_value"
    assert model.element("metric_unit").semantic_role == "metric_description"
    assert model.element("card_heading").semantic_role == "card_title"
    assert model.element("card_body").semantic_role == "card_body"


def test_card_label_is_a_short_card_title_not_body(catalog):
    model = parse_slide_layout(
        catalog.get_layout("editorial", "title_with_concentric_circles_and_linked_side_cards")
    )
    assert model.element("card_label").semantic_role == "card_title"
    assert model.element("card_description").semantic_role == "card_body"


def test_layout_summary_exposes_semantic_slots_and_numeric_contract(catalog):
    summary = next(
        item
        for item in catalog.layout_summaries("standard")
        if item["id"] == "title_metric_card_grid_7973"
    )
    roles = {slot["semanticRole"] for slot in summary["semanticSlots"]}
    assert "metric_value" in roles
    assert "card_title" in roles
    assert summary["requiresNumericData"] is True
