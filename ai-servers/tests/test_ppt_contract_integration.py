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
from app.ppt_generation.template_model import parse_slide_layout, semantic_content_contract


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


def test_merge_expands_prototype_agenda_grid_for_all_supplied_items(catalog):
    """目录模板只有一个原型子组时，也必须 materialize 全部目录项。"""
    layout = catalog.get_layout("momentum", "center_title_agenda_grid_wave_7815")
    descriptions = [
        "第一章 绪论：数据结构基本概念",
        "第二章 线性表",
        "第三章 栈与队列",
        "第四章 串与模式匹配",
        "第五章 树与二叉树",
        "第六章 图",
    ]
    merged = _merge_content_into_layout(layout, {
        "slide_title": "全书章节概览",
        "agenda_item_label": ["01", "02", "03", "04", "05", "06"],
        "agenda_item_description": descriptions,
    })

    labels = [
        node.get("text")
        for node in _collect_text_nodes(merged)
        if node.get("name") == "agenda_item_label"
    ]
    actual_descriptions = [
        node.get("text")
        for node in _collect_text_nodes(merged)
        if node.get("name") == "agenda_item_description"
    ]
    assert labels == ["01", "02", "03", "04", "05", "06"]
    assert actual_descriptions == descriptions


def test_fallback_expands_prototype_agenda_grid_from_slide_content(catalog):
    """没有 componentContent 时，目录兜底也不能只保留第一条。"""
    layout = catalog.get_layout("momentum", "center_title_agenda_grid_wave_7815")
    descriptions = [
        "第一章 绪论：数据结构基本概念",
        "第二章 线性表",
        "第三章 栈与队列",
        "第四章 串与模式匹配",
        "第五章 树与二叉树",
        "第六章 图",
    ]
    fallback = _fill_layout_with_slide_text(
        layout,
        {"title": "全书章节概览", "content": descriptions},
        {},
    )
    actual_descriptions = [
        node.get("text")
        for node in _collect_text_nodes(fallback)
        if node.get("name") == "agenda_item_description"
    ]
    assert len(actual_descriptions) == len(descriptions)
    assert all(str(text or "").strip() for text in actual_descriptions)
    for fragment in ["数据结构基本概念", "线性表", "栈与队列", "串与模式匹配", "树与二叉树", "图"]:
        assert any(fragment in str(text or "") for text in actual_descriptions)


def test_merge_reports_content_cardinality_when_template_capacity_is_exceeded(catalog):
    """超过模板容量时必须显式报告，不能把多余目录静默丢掉。"""
    layout = catalog.get_layout("momentum", "center_title_agenda_grid_wave_7815")
    values = [f"第{i}章 内容" for i in range(1, 10)]
    merged = _merge_content_into_layout(layout, {
        "agenda_item_label": [f"0{i}" for i in range(1, 10)],
        "agenda_item_description": values,
    })
    assert merged["_contentCardinalityIssues"] == [
        {"elementId": "agenda_item_label", "expected": 9, "rendered": 8},
        {"elementId": "agenda_item_description", "expected": 9, "rendered": 8},
    ]


def test_cardinality_issue_promotes_slide_qa_to_partial(catalog):
    """容量不足必须进入成品质量门禁，而不是只留内部日志。"""
    service = PptGenerationService()
    layout_id = "center_title_agenda_grid_wave_7815"
    layout = catalog.get_layout("momentum", layout_id)
    values = [f"第{i}章 内容" for i in range(1, 10)]
    normalized = _sanitize_content_payload(
        {"slides": [{
            "title": "全书章节概览",
            "content": values,
            "componentContent": {
                "agenda_item_label": [f"0{i}" for i in range(1, 10)],
                "agenda_item_description": values,
            },
        }]},
        [layout_id],
        {layout_id: layout},
        1,
    )
    slide = normalized["slides"][0]
    enforced = service._enforce_slide_contract(
        slide, "momentum", layout_id, layout, None, 1
    )
    assert enforced["_qa"]["finalStatus"] == "partial"
    assert "CONTENT_CARDINALITY" in enforced["_qa"]["validationErrors"]
    assert enforced["_qa"]["contentCardinalityIssues"]


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
    assert any(text.strip() and text != sample_text for text in texts)
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

    # 模板目录本身就应暴露几何收紧后的有效容量，避免结构/内容智能体
    # 先看到宽松的原始 max_length，再在后端被迫否定。
    schema = service._template_catalog.component_schema(layout.get("components") or [])
    for entry in schema:
        element = model.element(str(entry.get("name") or ""))
        if element and element.constraint:
            assert entry.get("capacity")
            assert entry["capacity"]["hardMaxChars"] == element.constraint.hard_max_chars
            assert entry["max_length"] == element.constraint.hard_max_chars
    headline = next(e for e in schema if e["name"] == "headline_text")
    assert 0 < headline["capacity"]["hardMaxChars"] == headline["max_length"]


def test_all_bundled_template_schemas_use_geometry_bounded_capacity(catalog):
    """Raw template max_length must never exceed the model's real box capacity."""
    for template in catalog.list_templates():
        template_id = template["id"]
        payload = catalog.load(template_id)
        for layout in payload.get("layouts") or []:
            if not isinstance(layout, dict):
                continue
            model = parse_slide_layout(layout)
            schema = catalog.component_schema(layout.get("components") or [])
            for entry in schema:
                element = model.element(
                    str(entry.get("name") or ""),
                    int(entry.get("occurrence") or 0),
                )
                if element is None or element.constraint is None:
                    continue
                assert entry["max_length"] == element.constraint.hard_max_chars, (
                    template_id,
                    layout.get("id"),
                    entry.get("name"),
                )


def test_missing_repeated_card_slots_do_not_create_overflow(catalog):
    """A partial repeated-card response must stay valid after semantic fill."""
    service = PptGenerationService()
    layout_id = "title_intro_staggered_cards_4014"
    layout = catalog.get_layout("momentum", layout_id)
    model = parse_slide_layout(layout)
    component_content = {}
    for name, elements in model.elements.items():
        if not any(element.mutable_text for element in elements):
            continue
        values = []
        for occurrence, element in enumerate(elements):
            if element.semantic_role in {"page_title", "section_title", "page_subtitle"}:
                values.append("栈与队列")
            elif element.semantic_role == "card_title":
                values.append(f"卡片主题{occurrence + 1}")
            elif element.semantic_role in {"card_body", "body", "bullet_body"}:
                values.append(f"第{occurrence + 1}项说明")
        if values:
            component_content[name] = values[:-1] if len(values) > 1 else values

    normalized = _sanitize_content_payload(
        {"slides": [{
            "type": "content",
            "title": "栈与队列",
            "content": ["聚焦数据结构原理与算法执行逻辑；覆盖关键概念和应用。"],
            "componentContent": component_content,
        }]},
        [layout_id],
        {layout_id: layout},
        1,
        current_slides=[{"title": "栈与队列", "keyPoints": ["后进先出", "先进先出"]}],
    )
    slide = service._enforce_slide_contract(
        normalized["slides"][0], "momentum", layout_id, layout, None, 1
    )
    assert slide.get("_missingSlots")
    assert slide["_qa"]["finalStatus"] in {"clean", "repaired"}
    assert "TEXT_OVERFLOW" not in slide["_qa"].get("validationErrors", [])
    assert "CONNECTOR_TARGET_EMPTY" not in slide["_qa"].get("validationErrors", [])


def test_semantic_contract_uses_real_geometry_capacity(catalog):
    """Timeline metadata must be a one-line label, not generic body copy."""
    layout = catalog.get_layout("momentum", "title_timeline_cards_9036")
    model = parse_slide_layout(layout)
    duration = model.element("duration_label", 0)
    description = model.element("milestone_description", 0)
    assert duration is not None and duration.semantic_role == "label"
    assert duration.constraint is not None and duration.constraint.max_lines == 1
    assert description is not None and description.constraint is not None
    contract = semantic_content_contract(description.semantic_role, description.constraint)
    assert contract["maxLines"] == description.constraint.max_lines
    assert contract["hardMaxChars"] == description.constraint.hard_max_chars


def test_layout_catalog_exposes_per_slot_capacity(catalog):
    summary = next(
        item for item in catalog.layout_summaries("momentum")
        if item["id"] == "title_timeline_cards_9036"
    )
    duration = next(
        item for item in summary["semanticSlots"]
        if item["name"] == "duration_label"
    )
    assert duration["semanticRole"] == "label"
    assert duration["capacity"]["maxLines"] == 1
    assert duration["contentContract"]["hardMaxChars"] == duration["capacity"]["hardMaxChars"]


def test_overflow_page_recovers_with_same_template_fallback(catalog):
    """A stubborn single page must not block the deck when another layout fits."""
    service = PptGenerationService()
    payload = catalog.load("momentum")
    layouts = {
        str(layout["id"]): layout
        for layout in payload["layouts"]
        if isinstance(layout, dict) and layout.get("id")
    }
    current_id = "title_timeline_cards_9036"
    slide = {
        "title": "复杂项目实施路线与阶段性目标",
        "content": [
            "第一阶段：完成需求调研、技术选型、人员分工与风险识别，形成完整实施方案。",
            "第二阶段：完成核心功能开发、联调测试、性能优化、用户验收与问题闭环，确保系统稳定上线。",
            "第三阶段：持续运营监控、收集反馈、迭代优化并形成长期治理机制。",
        ],
    }
    slide["ui"] = _fill_layout_with_slide_text(layouts[current_id], slide, slide)
    enforced = service._enforce_slide_contract(
        slide, "momentum", current_id, layouts[current_id], None, 1
    )
    assert enforced["_qa"]["finalStatus"] in {"clean", "repaired"}
    assert not any(
        error == "TEXT_OVERFLOW"
        for error in enforced["_qa"].get("validationErrors") or []
    )
