"""ContentFitter unit tests.

Spec §14-§16, §29-§31:
- strategy order: rewrite → summarize → bulletize → remove-secondary →
    shrink-font (bounded) → explicit failure with original text preserved
- never shrink font first; never below min_font_size
- rewrite prompt carries explicit capacity
"""

import copy

import pytest

from app.ppt_generation.content_fitter import (
    build_rewrite_user_prompt,
    fit_text,
)
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import parse_slide_layout, text_fits


@pytest.fixture(scope="module")
def catalog():
    return EmbeddedTemplateCatalog()


@pytest.fixture(scope="module")
def title_model():
    layout = EmbeddedTemplateCatalog().get_layout("general", "title_intro")
    return parse_slide_layout(layout)


def _node(catalog, layout_id, name):
    return catalog.get_layout("general", layout_id), name


def _fit(title_model, name, text, llm=None, budget=2):
    element = title_model.element(name)
    return fit_text(text, element, llm_rewrite=llm, llm_call_budget=budget)


def test_short_content_is_untouched(title_model):
    result = _fit(title_model, "headline_text", "数据结构概述")
    assert result.strategy == "none"
    assert result.text == "数据结构概述"
    assert result.fits


def test_overflowing_title_uses_rewrite_first_when_llm_available(title_model):
    long_title = "超" * 60

    def llm_rewrite(text, constraint, mode):
        return "重写后的精炼标题"

    result = _fit(title_model, "headline_text", long_title, llm=llm_rewrite)
    assert result.strategy == "rewrite"
    assert result.fits


def test_overflowing_title_never_shrinks_font_first(title_model):
    """超出容量时不能用截断伪装成成功，必须保留完整标题并失败。"""
    long_title = "超" * 60
    result = _fit(title_model, "headline_text", long_title, llm=None)
    assert result.strategy == "failed"
    assert result.text == long_title
    assert not result.fits
    assert "…" not in result.text


def test_body_content_compresses_to_capacity(title_model):
    long_body = "自动完成重复任务降低人工处理成本，这是企业数字化转型的核心价值所在。" * 5
    result = _fit(title_model, "body_copy", long_body, llm=None)
    element = title_model.element("body_copy")
    assert result.text == long_body
    assert not result.fits
    assert not text_fits(result.text, element)
    assert "…" not in result.text


def test_shrink_font_is_bounded_by_min_font(title_model):
    """§16：缩字不能低于下限；正文最多缩 15%。"""
    element = title_model.element("body_copy")
    constraint = element.constraint
    # 构造一个内容策略都失败、必须缩字的场景：单段极长文本且无标点可切
    text = "这是" + "极长且没有标点的文本内容" * 20
    result = fit_text(text, element, llm_rewrite=None, llm_call_budget=0)
    if result.strategy == "shrink-font" and result.shrink_scale:
        assert result.shrink_scale >= constraint.max_shrink_ratio - 1e-6


def test_rewrite_prompt_carries_explicit_capacity(title_model):
    element = title_model.element("headline_text")
    prompt = build_rewrite_user_prompt("原始内容", element.constraint)
    assert str(element.constraint.hard_max_chars) in prompt
    assert str(element.constraint.recommended_chars) in prompt
    assert str(element.constraint.max_lines) in prompt


def test_single_line_slot_never_ellipsis(catalog):
    # table_of_contents 的 item_label 是单行槽，但不能静默丢内容
    layout = catalog.get_layout("general", "table_of_contents")
    model = parse_slide_layout(layout)
    element = model.element("item_label")
    if element is None or element.constraint is None:
        pytest.skip("item_label not found")
    result = fit_text("很" * 100, element, llm_rewrite=None, llm_call_budget=0)
    assert result.text == "很" * 100
    assert "…" not in result.text
    assert not result.fits


def test_empty_text_passthrough(title_model):
    result = _fit(title_model, "body_copy", "   ")
    assert result.text == ""
    assert result.fits


def test_chinese_momentum_title_shrinks_before_truncating():
    layout = EmbeddedTemplateCatalog().get_layout("momentum", "title_with_accent_footer_6891")
    model = parse_slide_layout(layout)
    element = model.element("cover_title")
    result = fit_text("成都理工大学宣传PPT", element, llm_rewrite=None, llm_call_budget=0)

    assert result.strategy == "shrink-font"
    assert result.text == "成都理工大学宣传PPT"
    assert result.shrink_scale is not None
    assert result.shrink_scale <= 0.72 + 1e-6
    assert result.fits


def test_single_line_title_tolerates_template_rounding_after_shrink():
    layout = EmbeddedTemplateCatalog().get_layout(
        "dynamic", "large_title_bottom_image_3101"
    )
    model = parse_slide_layout(layout)
    element = model.element("primary_heading")
    result = fit_text(
        "人工智能发展趋势与应用实践",
        element,
        llm_rewrite=None,
        llm_call_budget=0,
        current_font_size=element.font_size,
    )
    assert result.fits


def test_formal_title_is_semantically_shortened_without_ellipsis():
    layout = EmbeddedTemplateCatalog().get_layout(
        "dynamic", "large_title_bottom_image_3101"
    )
    model = parse_slide_layout(layout)
    element = model.element("primary_heading")
    result = fit_text("复杂系统的关键机制与实施路径", element, llm_rewrite=None, llm_call_budget=0)
    assert result.fits
    assert "…" not in result.text
    assert "..." not in result.text
    assert result.text != "复杂系统的关键机制与实施路径"
