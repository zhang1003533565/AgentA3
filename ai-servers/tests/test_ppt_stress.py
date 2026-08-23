"""Extreme stress tests (spec §81-§82).

Core invariant under every stress input:
    CONTENT MUST ADAPT TO TEMPLATE. TEMPLATE MUST NOT ADAPT TO CONTENT.
The template geometry and locked elements must never change, no matter how
extreme the content is.
"""

import copy

import pytest

from app.ppt_generation.layout_validator import validate_slide
from app.ppt_generation.repair_engine import RepairEngine
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import parse_slide_layout

STRESS_TEXTS = {
    "超长中文": "这是一段极其长的中文内容。" * 50,
    "超长英文": "Supercalifragilisticexpialidocious " * 30,
    "大量项目符号": "\n".join(f"• 第 {i} 个要点内容说明" for i in range(60)),
    "极端长标题": "极" * 300,
    "中英混排": "混合内容 Mixed content 中文 English 混合 1234567890 " * 20,
    "长URL": "https://example.com/very/long/path?query=1&x=" + "a" * 200,
    "大量数字": " ".join(str(i * 123456789) for i in range(80)),
    "图片缺失": "图片说明文字" * 30,
    "数据缺失": "没有数据的描述" * 30,
}

LAYOUTS = ["title_intro", "title_image_bullet_points", "title_description_chart_cards"]


@pytest.fixture(scope="module")
def catalog():
    return EmbeddedTemplateCatalog()


@pytest.mark.parametrize("layout_id", LAYOUTS)
@pytest.mark.parametrize("case_name", list(STRESS_TEXTS))
def test_stress_content_never_breaks_template(catalog, layout_id, case_name):
    layout = catalog.get_layout("general", layout_id)
    model = parse_slide_layout(layout)
    tree = {"components": copy.deepcopy(layout["components"])}
    text = STRESS_TEXTS[case_name]

    # 把所有可写文本槽位全部塞入极端内容
    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if isinstance(node, dict):
            name = str(node.get("name") or "")
            if name and node.get("type") == "text":
                element = model.element(name, 0)
                if element and element.mutable_text:
                    node["text"] = text
                    node["runs"] = [{"text": text, "font": dict(node.get("font") or {})}]
            for key in ("elements", "components", "children"):
                if key in node:
                    walk(node[key])
            if "child" in node:
                walk(node["child"])

    walk(tree)
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)

    # 关键不变量：几何与锁定元素永远不变（即使内容修复不完美）
    result = validate_slide(outcome.ui, model)
    geometry_errors = [
        issue for issue in result.issues
        if issue.error_type in {"GEOMETRY_CHANGED", "OUT_OF_BOUNDS"} and issue.severity == "error"
    ]
    assert geometry_errors == [], f"{layout_id}/{case_name}: {geometry_errors}"

    # 修复必须有上限、有历史记录（不静默、不无限循环）
    assert outcome.repair_count <= 5
    assert isinstance(outcome.history, list)


def test_stress_geometry_stays_identical_to_template_snapshot(catalog):
    """几何快照对比：修复后每个元素坐标/尺寸与模板完全一致。"""
    layout = catalog.get_layout("general", "title_intro")
    model = parse_slide_layout(layout)
    tree = {"components": copy.deepcopy(layout["components"])}

    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if isinstance(node, dict):
            if node.get("name") == "headline_text":
                node["text"] = "极" * 300
                node["runs"] = [{"text": "极" * 300, "font": dict(node.get("font") or {})}]
            for key in ("elements", "components", "children"):
                if key in node:
                    walk(node[key])

    walk(tree)
    outcome = RepairEngine().repair(tree, model, llm_rewrite=None)

    def boxes(root):
        found = {}

        def walk_node(node, base_x, base_y):
            if isinstance(node, list):
                for item in node:
                    walk_node(item, base_x, base_y)
                return
            if isinstance(node, dict):
                name = str(node.get("name") or "")
                if name:
                    position = node.get("position") or {}
                    size = node.get("size") or {}
                    found[name] = (
                        base_x + float(position.get("x") or 0),
                        base_y + float(position.get("y") or 0),
                        float(size.get("width") or 0),
                        float(size.get("height") or 0),
                    )
                position = node.get("position") or {}
                for key in ("elements", "components", "children"):
                    if key in node:
                        walk_node(node[key], base_x + float(position.get("x") or 0),
                                   base_y + float(position.get("y") or 0))
                if "child" in node:
                    walk_node(node["child"], base_x + float(position.get("x") or 0),
                               base_y + float(position.get("y") or 0))

        for component in root.get("components") or []:
            position = component.get("position") or {}
            walk_node(component.get("elements") or [], float(position.get("x") or 0), float(position.get("y") or 0))
        return found

    before = boxes(tree)
    after = boxes(outcome.ui)
    for name in before:
        assert before[name] == pytest.approx(after[name], abs=0.5), f"{name} 几何变化"
