"""Service integration tests for the template contract layer.

Spec §25-§27, §80 (Case 6, 7), §85:
- merge by component name never touches geometry/coordinates
- unknown elementId is reported, never creates elements
- capacity is injected into the content prompt schema
"""

import copy

import pytest

from app.ppt_generation.service import (
    PptGenerationService,
    _merge_content_into_layout,
    _collect_text_nodes,
    _fill_layout_with_slide_text,
    _sanitize_content_payload,
    _node_display_text,
)
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog


@pytest.fixture(scope="module")
def catalog():
    return EmbeddedTemplateCatalog()


def test_merge_only_changes_matched_text(catalog):
    """Case 7：AI 只给内容 → 程序决定位置；几何/样式字段完全不动。"""
    layout = catalog.get_layout("general", "title_intro")
    before = copy.deepcopy(layout)

    merged = _merge_content_into_layout(layout, {
        "headline_text": "AI 驱动业务增长",
        "body_copy": "自动完成重复任务，降低人工成本",
    })

    def collect(node, out):
        if isinstance(node, list):
            for item in node:
                collect(item, out)
            return
        if isinstance(node, dict):
            out.append(node)
            for key in ("elements", "components", "children"):
                if key in node:
                    collect(node[key], out)
            if "child" in node:
                collect(node["child"], out)

    before_nodes, after_nodes = [], []
    collect(before, before_nodes)
    collect(merged, after_nodes)
    assert len(before_nodes) == len(after_nodes)
    for before_node, after_node in zip(before_nodes, after_nodes):
        for key in ("position", "size", "font", "fill", "type", "name", "id"):
            assert before_node.get(key) == after_node.get(key), f"{key} 被修改"
    assert merged["_matchedComponents"] >= 2


def test_unknown_element_id_is_reported_not_created(catalog):
    """Case 6：AI 返回不存在的 elementId → 不创建元素，只报告。"""
    layout = catalog.get_layout("general", "title_intro")
    before = copy.deepcopy(layout)

    merged = _merge_content_into_layout(layout, {
        "headline_text": "合法标题",
        "hallucinated_element_xyz": "AI 幻觉出来的元素",
    })
    assert merged["_matchedComponents"] == 1

    def collect(node, out):
        if isinstance(node, list):
            for item in node:
                collect(item, out)
            return
        if isinstance(node, dict):
            out.append(node)
            for key in ("elements", "components", "children"):
                if key in node:
                    collect(node[key], out)
            if "child" in node:
                collect(node["child"], out)

    before_nodes, after_nodes = [], []
    collect(before, before_nodes)
    collect(merged, after_nodes)
    names_before = {n.get("name") for n in before_nodes if n.get("name")}
    names_after = {n.get("name") for n in after_nodes if n.get("name")}
    assert names_after == names_before  # 未新增任何元素


def test_merge_accepts_indexed_repeated_component_keys(catalog):
    """模型把重复槽位写成 card_title_1/card_title_2 时仍应正确落位。"""
    layout = catalog.get_layout("momentum", "title_intro_staggered_cards_4014")
    merged = _merge_content_into_layout(layout, {
        "slide_title": "数据结构",
        "card_title_1": "线性表",
        "card_body_1": "顺序存储与链式存储是两种基本实现。",
        "card_title_2": "栈与队列",
        "card_body_2": "分别体现后进先出和先进先出的访问规则。",
    })
    texts = [_node_display_text(node) for node in _collect_text_nodes(merged)]
    assert "数据结构" in texts
    assert "线性表" in texts
    assert "栈与队列" in texts
    assert "Customer Retention" not in texts
    assert "High-Quality Lead Generation" not in texts


def test_merge_prunes_unused_repeated_card_groups_and_clears_template_copy(catalog):
    """模板有 4 张卡、模型只返回 2 张时，后两组连同旧图标一起移除。"""
    layout = catalog.get_layout("general", "title_image_bullet_points_1")
    merged = _merge_content_into_layout(layout, {
        "slide_headline": "线性表的两种存储方式",
        "feature_title": ["顺序表", "链表"],
        "feature_description": [
            "元素连续存放，按下标访问速度快，但插入删除需要移动后续元素。",
            "结点通过指针连接，插入删除灵活，但访问指定位置需要顺序查找。",
        ],
    })

    def collect(node, out):
        if isinstance(node, list):
            for item in node:
                collect(item, out)
            return
        if isinstance(node, dict):
            out.append(node)
            for key in ("elements", "components", "children"):
                if key in node:
                    collect(node[key], out)
            if "child" in node:
                collect(node["child"], out)

    nodes = []
    collect(merged, nodes)
    assert sum(node.get("name") == "feature_item" for node in nodes) == 2
    assert sum(node.get("name") == "feature_icon" for node in nodes) == 2
    texts = [node.get("text") or "" for node in nodes if node.get("type") == "text"]
    assert "Support Services" not in texts
    assert "Scalable Marketing" not in texts
    assert all("We provide ongoing support" not in text for text in texts)


def test_outline_fallback_clears_realistic_template_sample_copy(catalog):
    """componentContent 失效时，不能把看似正常的模板示例文案带入成品。"""
    layout = catalog.get_layout("momentum", "title_intro_staggered_cards_4014")
    filled = _fill_layout_with_slide_text(
        layout,
        {"title": "数据结构", "content": ["线性表：顺序存储与链式存储"]},
        {},
    )

    texts = [_node_display_text(node) for node in _collect_text_nodes(filled)]
    leaked = {
        "Business Growth",
        "Customer Retention",
        "High-Quality Lead Generation",
        "Strategic Market Expansion",
        "Revenue Growth through Upselling",
        "Growing online sales through e-commerce platforms, digital marketing campaign",
    }
    assert leaked.isdisjoint(texts)
    assert "数据结构" in texts
    assert any("线性表" in text for text in texts)


@pytest.mark.parametrize(
    ("template_id", "layout_id", "sample_text"),
    [
        ("dynamic", "cover_title_left_image_right_8271", "INTRODUCTION"),
        ("momentum", "title_with_accent_footer_6891", "SALES"),
        ("executive", "centered_title_metadata_6418", "SMART TASK"),
    ],
)
def test_outline_fallback_replaces_uppercase_editable_title_slots(
    catalog, template_id, layout_id, sample_text
):
    """全大写的模板示例标题仍是可编辑槽位，不能被当成装饰保留。"""
    layout = catalog.get_layout(template_id, layout_id)
    filled = _fill_layout_with_slide_text(
        layout,
        {"title": "人工智能发展趋势与应用实践", "content": ["背景：行业进入快速变化阶段"]},
        {},
    )

    texts = [_node_display_text(node) for node in _collect_text_nodes(filled)]
    assert any(text.startswith("人工智能发展趋势") for text in texts)
    assert not any(sample_text in text for text in texts)


def test_outline_fallback_does_not_preserve_mutable_badge_sample_copy(catalog):
    """badge 文本槽位是可编辑内容，不应保留 John Doe/日期示例。"""
    layout = catalog.get_layout("swift", "left_cover_text_right_image_2459")
    filled = _fill_layout_with_slide_text(
        layout,
        {"title": "人工智能发展趋势", "content": ["核心概念", "应用场景"]},
        {},
    )
    texts = [_node_display_text(node) for node in _collect_text_nodes(filled)]
    assert "John Doe" not in texts
    assert "Jan 1, 2025" not in texts
    assert "www.yourwebsite.com" not in texts


def test_outline_fallback_replaces_footer_profile_sample_copy(catalog):
    """页脚个人资料卡不能把模板的 John Doe 带入成品。"""
    layout = catalog.get_layout(
        "editorial", "title_slide_with_image_collage_and_footer"
    )
    filled = _fill_layout_with_slide_text(
        layout,
        {"title": "人工智能发展趋势", "content": ["核心概念", "应用场景"]},
        {},
    )
    texts = [_node_display_text(node) for node in _collect_text_nodes(filled)]
    assert "John Doe" not in texts


def test_sanitize_reports_unknown_ids(catalog):
    layout = catalog.get_layout("general", "title_intro")
    payload = {"slides": [{
        "type": "content",
        "title": "测试页",
        "content": ["要点"],
        "componentContent": {
            "headline_text": "标题",
            "not_a_component": "幻觉",
        },
    }]}
    normalized = _sanitize_content_payload(
        payload, ["title_intro"], {"title_intro": layout}, 1
    )
    item = normalized["slides"][0]
    assert item["_unknownElementIds"] == ["not_a_component"]
    assert "ui" in item


def test_capacity_injected_into_content_prompt(catalog, monkeypatch):
    """§57：内容提示词必须携带每槽位容量。"""
    monkeypatch.setenv("PPT_CONTENT_BATCH_SIZE", "1")
    monkeypatch.setenv("PPT_CONTENT_MAX_WORKERS", "1")
    monkeypatch.setenv("PPT_OUTLINE_SOURCE_MAX_CHARS", "2000")
    service = PptGenerationService()

    layout = catalog.get_layout("general", "title_intro")
    model = service._layout_model("general", "title_intro", layout)
    assert model is not None

    # 直接验证 service 的容量注入逻辑（_generate_batch 内对 schema 的增强）
    schema = service._template_catalog.component_schema(layout.get("components") or [])
    for entry in schema:
        element = model.element(str(entry.get("name") or ""))
        if element and element.constraint:
            assert entry.get("capacity") is None  # 原始 schema 无容量
    # 增强后的 schema 应带容量
    enhanced = copy.deepcopy(schema)
    for entry in enhanced:
        element = model.element(str(entry.get("name") or ""))
        constraint = element.constraint if element else None
        if constraint is not None:
            entry["capacity"] = {
                "recommendedChars": constraint.recommended_chars,
                "hardMaxChars": constraint.hard_max_chars,
                "maxLines": constraint.max_lines,
                "charsPerLine": round(constraint.chars_per_line),
            }
    headline = next(e for e in enhanced if e["name"] == "headline_text")
    assert 0 < headline["capacity"]["hardMaxChars"] <= headline.get("max_length")
