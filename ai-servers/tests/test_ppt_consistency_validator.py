"""ConsistencyValidator + QA report unit tests.

Spec §62-§64, §78-§79: cross-page font consistency, QA report format.
"""

import copy

import pytest

from app.ppt_generation.consistency_validator import build_qa_report, validate_presentation
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import parse_slide_layout


@pytest.fixture(scope="module")
def catalog():
    return EmbeddedTemplateCatalog()


def _slide(catalog, layout_id, headline_text=None):
    layout = catalog.get_layout("general", layout_id)
    tree = {"components": copy.deepcopy(layout["components"])}

    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if isinstance(node, dict):
            if node.get("name") == "headline_text" and headline_text is not None:
                node["text"] = headline_text
                node["runs"] = [{"text": headline_text, "font": dict(node.get("font") or {})}]
            for key in ("elements", "components", "children"):
                if key in node:
                    walk(node[key])

    walk(tree)
    return {"templateLayoutId": layout_id, "type": "content", "ui": tree}


def _models(catalog, layout_ids):
    models = {}
    for layout_id in layout_ids:
        layout = catalog.get_layout("general", layout_id)
        models[layout_id] = parse_slide_layout(layout)
    return models


def test_same_font_across_slides_is_consistent(catalog):
    slides = [_slide(catalog, "title_intro") for _ in range(3)]
    models = _models(catalog, ["title_intro"])
    issues = validate_presentation(slides, models)
    assert not any(issue["kind"] == "FONT_INCONSISTENT" for issue in issues)


def test_font_drift_across_slides_is_detected(catalog):
    slides = [
        _slide(catalog, "title_intro"),
        _slide(catalog, "title_intro"),
    ]
    # 把第 2 页 headline 字号改小 10pt
    layout = catalog.get_layout("general", "title_intro")

    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if isinstance(node, dict):
            if node.get("name") == "headline_text":
                if isinstance(node.get("font"), dict):
                    node["font"]["size"] = node["font"]["size"] - 10
                for run in node.get("runs") or []:
                    if isinstance(run.get("font"), dict):
                        run["font"]["size"] = run["font"]["size"] - 10
            for key in ("elements", "components", "children"):
                if key in node:
                    walk(node[key])

    walk(slides[1]["ui"])
    models = _models(catalog, ["title_intro"])
    issues = validate_presentation(slides, models)
    font_issues = [issue for issue in issues if issue["kind"] == "FONT_INCONSISTENT"]
    assert font_issues
    assert font_issues[0]["element"] == "headline_text"
    assert len(font_issues[0]["slides"]) == 2


def test_qa_report_shape(catalog):
    slides = [_slide(catalog, "title_intro", headline_text="统一标题") for _ in range(2)]
    models = _models(catalog, ["title_intro"])
    for slide in slides:
        slide["_qa"] = {
            "layoutId": "title_intro",
            "semanticType": "content",
            "contentLength": 4,
            "validationErrors": [],
            "repairCount": 0,
            "finalStatus": "clean",
            "repairHistory": [],
            "densityLevel": "NORMAL",
            "fillRatio": 0.1,
        }
    report = build_qa_report(slides, [], models, "general")
    assert "## Slide 1 [PASS]" in report
    assert "## Slide 2 [PASS]" in report
    assert "template: general" in report
