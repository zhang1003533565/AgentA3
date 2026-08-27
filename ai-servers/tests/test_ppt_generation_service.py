import json
import time
from copy import deepcopy

import pytest
from fastapi import HTTPException

from app.rag.document_conversion import generated_exporter
from app.ppt_generation.routes import OutlineRequest, PreviewRequest, SlidesRequest
from app.ppt_generation.service import (
    PptGenerationService,
    _merge_content_into_layout,
    _ensure_outline_item_structure,
    _outline_items,
    _outline_markdown_from_items,
    _infer_outline_level,
    _normalize_flat_outline_levels,
    _parse_outline_level,
    _source_outline_items,
    _topic_outline_items,
    _is_topic_only_outline_request,
    _normalize_outline_mode,
    _repair_material_outline_coverage,
    _retry_llm_call,
    _safe_error_message,
    _layout_requires_numeric_data,
    _layout_has_required_visual,
    _rebalance_layout_choices,
    _visuals_enabled,
    _fill_layout_with_slide_text,
    _run_export_quality_check,
    _set_text_node_content,
    _sanitize_content_payload,
)
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import parse_slide_layout
from app.ppt_generation.presenton_html_renderer import _normalize_renderer_slides
from app.ppt_generation.layout_validator import validate_slide


def _sample_ui(layout_id="title_intro"):
    layout = EmbeddedTemplateCatalog().get_layout("general", layout_id)
    return {"components": deepcopy(layout["components"]), "background": "#FFFFFF"}


def _named_text_nodes(root, target_name):
    found = []

    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if not isinstance(node, dict):
            return
        if node.get("name") == target_name and node.get("type") in {"text", "text-list"}:
            found.append(node)
        for key in ("components", "elements", "children", "child"):
            if key in node:
                walk(node[key])

    walk(root)
    return found


def test_presenton_renderer_normalizes_external_slide_indexes_without_mutating_input():
    slides = [{"index": 0, "title": "第一页"}, {"title": "第二页"}, {"index": 18, "title": "第三页"}]

    normalized = _normalize_renderer_slides(slides)

    assert [slide["index"] for slide in normalized] == [1, 2, 3]
    assert [slide.get("index") for slide in slides] == [0, None, 18]


def test_export_quality_findings_are_non_blocking(monkeypatch, tmp_path):
    export_path = tmp_path / "finished.pptx"
    export_path.write_bytes(b"pptx")
    monkeypatch.setattr(
        generated_exporter,
        "_current_export_root",
        lambda: tmp_path,
    )
    monkeypatch.setattr(
        "app.ppt_generation.service.validate_exported_pptx",
        lambda _path: {
            "passed": False,
            "slides": 2,
            "errors": [{"slide": 1, "kind": "TEXT_OVERLAP"}],
            "warnings": [],
        },
    )

    report = _run_export_quality_check({"ext": "pptx", "storageKey": "finished.pptx"})

    assert report["status"] == "warning"
    assert report["passed"] is False
    assert report["errors"]
    assert report["messages"] == ["第1页检测到文本区域重叠"]


def test_fallback_preserves_template_badges_and_fills_card_copy():
    catalog = EmbeddedTemplateCatalog()
    layout = catalog.get_layout("momentum", "title_over_cards_layout_8558")
    original_badges = [node.get("text") for node in _named_text_nodes(layout, "card_badge_label")]

    result = _fill_layout_with_slide_text(
        layout,
        {
            "title": "第三章 栈与队列",
            "content": ["栈的后进先出机制", "队列的先进先出机制"],
        },
        {},
    )

    rendered_badges = [node.get("text") for node in _named_text_nodes(result, "card_badge_label")]
    rendered_titles = [node.get("text") for node in _named_text_nodes(result, "card_title")]
    assert rendered_badges == original_badges
    assert any(str(value or "").strip() for value in rendered_titles)


def test_fallback_card_title_is_compacted_to_template_geometry():
    catalog = EmbeddedTemplateCatalog()
    layout = catalog.get_layout("momentum", "title_over_cards_layout_8558")
    result = _fill_layout_with_slide_text(
        layout,
        {
            "title": "第三章 栈与队列",
            "content": [
                "聚焦计算机科学的底层基石；覆盖数据组织方式与算法执行逻辑；保留卡片语义信息。",
            ],
        },
        {},
    )

    rendered_titles = [
        str(node.get("text") or "")
        for node in _named_text_nodes(result, "card_title")
    ]
    visible_titles = [value for value in rendered_titles if value]
    assert visible_titles
    assert all(len(value) <= 16 for value in visible_titles)
    assert all("…" not in value for value in visible_titles)
    model = parse_slide_layout(layout)
    validation = validate_slide(result, model)
    assert not any(issue.error_type == "TEXT_OVERFLOW" for issue in validation.issues)


def test_numeric_layout_is_rebalanced_for_non_numeric_outline():
    catalog = EmbeddedTemplateCatalog()
    summaries = catalog.layout_summaries("momentum")
    numeric_id = "title_infographic_sections_6705"
    assert _layout_requires_numeric_data(next(item for item in summaries if item["id"] == numeric_id))

    selected = _rebalance_layout_choices(
        [numeric_id],
        summaries,
        [{
            "title": "栈与队列 FIFO 机制及应用",
            "type": "content",
            "content": ["栈采用后进先出", "队列采用先进先出"],
        }],
    )
    assert selected[0] != numeric_id
    assert not _layout_requires_numeric_data(next(item for item in summaries if item["id"] == selected[0]))


def test_cjk_text_does_not_keep_extreme_negative_letter_spacing():
    node = {
        "type": "text",
        "name": "headline",
        "text": "old",
        "font": {"size": 64, "letter_spacing": -5.95},
        "size": {"width": 500, "height": 80},
    }
    _set_text_node_content(node, "栈与队列 LIFO/FIFO", respect_capacity=False)
    assert node["text"] == "栈与队列 LIFO/FIFO"
    assert node["font"]["letter_spacing"] == -1.0


def test_fallback_fills_metric_slots_only_from_explicit_numeric_facts():
    layout = EmbeddedTemplateCatalog().get_layout("momentum", "title_infographic_sections_6705")
    result = _fill_layout_with_slide_text(
        layout,
        {
            "title": "复杂度分析",
            "content": ["顺序实现的入栈、出栈和入队保持 O(1)"],
        },
        {},
    )
    values = [node.get("text") for node in _named_text_nodes(result, "outer_metric_value")]
    assert values == ["O(1)"]


def test_fallback_keeps_connector_target_headings_paired_with_body():
    layout = EmbeddedTemplateCatalog().get_layout("momentum", "title_infographic_sections_6705")

    result = _fill_layout_with_slide_text(
        layout,
        {
            "title": "栈队列 LIFO/FIFO 机制及应用",
            "content": [
                "LIFO：栈顶操作适合撤销、递归和表达式求值。",
                "FIFO：队首出队适合任务调度和消息处理。",
                "复杂度：顺序实现的入栈、出栈和入队保持 O(1)。",
            ],
        },
        {},
    )

    headings = [node.get("text") for node in _named_text_nodes(result, "section_heading")]
    bodies = [node.get("text") for node in _named_text_nodes(result, "section_body")]
    assert headings == ["LIFO", "FIFO", "复杂度"]
    assert all(str(value or "").strip() for value in bodies)


def test_component_content_path_backfills_connector_target_headings():
    catalog = EmbeddedTemplateCatalog()
    layout_id = "title_infographic_sections_6705"
    layout = catalog.get_layout("momentum", layout_id)
    result = _sanitize_content_payload(
        {
            "slides": [
                {
                    "title": "栈队列机制及应用",
                    "content": [
                        "LIFO：栈顶操作适合撤销、递归和表达式求值。",
                        "FIFO：队首出队适合任务调度和消息处理。",
                        "复杂度：顺序实现的入栈、出栈和入队保持 O(1)。",
                    ],
                    "componentContent": {
                        "section_body": [
                            "LIFO：栈顶操作适合撤销、递归和表达式求值。",
                            "FIFO：队首出队适合任务调度和消息处理。",
                            "复杂度：顺序实现的入栈、出栈和入队保持 O(1)。",
                        ],
                    },
                }
            ]
        },
        [layout_id],
        {layout_id: layout},
        1,
    )

    ui = result["slides"][0]["ui"]
    headings = [node.get("text") for node in _named_text_nodes(ui, "section_heading")]
    assert headings == ["LIFO", "FIFO", "复杂度"]


def test_content_merge_keeps_confirmed_title_source_evidence_and_clears_template_samples():
    catalog = EmbeddedTemplateCatalog()
    layout_id = "title_intro"
    layout = catalog.get_layout("general", layout_id)
    result = _sanitize_content_payload(
        {
            "slides": [
                {
                    "title": "模型擅自改写的标题",
                    "content": ["队列按进入顺序处理任务。", "出队操作保持先进先出。"],
                    "componentContent": {
                        "headline_text": "模型擅自改写的标题",
                        "body_copy": "队列按进入顺序处理任务。出队操作保持先进先出。",
                    },
                }
            ]
        },
        [layout_id],
        {layout_id: layout},
        1,
        [{
            "title": "已确认的队列标题",
            "sourceMaterial": "资料证据：队列从队尾进入、从队首移出。",
        }],
    )

    slide = result["slides"][0]
    assert slide["title"] == "已确认的队列标题"
    assert slide["sourceMaterial"].startswith("资料证据：")
    text = " ".join(str(node.get("text") or "") for node in _named_text_nodes(slide["ui"], "attribution_name"))
    text += " " + " ".join(str(node.get("text") or "") for node in _named_text_nodes(slide["ui"], "attribution_detail"))
    assert "John Doe" not in text
    assert "December 2025" not in text
    assert "模型擅自改写的标题" not in " ".join(
        str(node.get("text") or "") for node in _named_text_nodes(slide["ui"], "headline_text")
    )
    assert "已确认的队列标题" in " ".join(
        str(node.get("text") or "") for node in _named_text_nodes(slide["ui"], "headline_text")
    )


def test_visual_disabled_mode_excludes_required_image_layouts():
    catalog = EmbeddedTemplateCatalog()
    assert _visuals_enabled({"includeVisuals": False}) is False
    assert _visuals_enabled({"includeVisuals": "true"}) is True
    assert _layout_has_required_visual(catalog.get_layout("general", "title_intro")) is True
    assert _layout_has_required_visual(catalog.get_layout("general", "title_table_description")) is False


def test_layout_rebalance_rejects_contents_template_for_normal_content():
    catalog = EmbeddedTemplateCatalog()
    summaries = catalog.layout_summaries("general")
    selected = _rebalance_layout_choices(
        ["table_of_contents"],
        summaries,
        [{
            "title": "栈的 LIFO 机制",
            "type": "内容页",
            "content": ["插入和删除都在栈顶进行"],
        }],
    )
    assert selected[0] != "table_of_contents"


def test_layout_rebalance_rejects_numeric_template_without_numeric_source():
    catalog = EmbeddedTemplateCatalog()
    summaries = catalog.layout_summaries("standard")
    metric = next(item for item in summaries if item["id"] == "title_metric_card_grid_7973")
    assert _layout_requires_numeric_data(metric)

    items = [{
        "title": "数据结构核心概念",
        "type": "content",
        "content": ["线性表与树的关系", "复杂度的判断方法"],
    }]
    selected = _rebalance_layout_choices(
        [metric["id"]],
        summaries,
        items,
    )
    assert selected[0] != metric["id"]


def test_layout_rebalance_keeps_numeric_template_for_explicit_metrics():
    catalog = EmbeddedTemplateCatalog()
    summaries = catalog.layout_summaries("standard")
    metric = next(item for item in summaries if item["id"] == "title_metric_card_grid_7973")
    items = [{
        "title": "课程完成率",
        "type": "data",
        "content": ["完成率 82%", "正确率 76%"],
    }]
    selected = _rebalance_layout_choices([metric["id"]], summaries, items)
    assert selected[0] == metric["id"]


def test_outline_markdown_is_exposed_as_editable_items():
    items = _outline_items("""
## PPT 大纲
### 第1页
- 页标题：数据结构概述
- 页面类型：内容页
- 本页目标：理解基本概念
- 核心内容：
  - 逻辑结构
  - 存储结构
- 展示建议：双栏
- 素材建议：结构图
""")

    assert items == [{
        "id": "slide_1",
        "level": 1,
        "title": "数据结构概述",
        "type": "内容页",
        "objective": "理解基本概念",
        "keyPoints": ["逻辑结构", "存储结构"],
        "nodes": [
            {"id": "node_1", "level": 2, "title": "逻辑结构", "content": "逻辑结构"},
            {"id": "node_2", "level": 2, "title": "存储结构", "content": "存储结构"},
        ],
        "displaySuggestion": "双栏",
        "assetSuggestion": "结构图",
    }]


def test_outline_levels_follow_document_heading_patterns():
    assert _infer_outline_level("第一章 绪论") == 1
    assert _infer_outline_level("1.1 基本术语与逻辑结构") == 2
    assert _infer_outline_level("1.1.1 具体实现") == 3
    assert _infer_outline_level("1 基本术语与逻辑结构") == 2
    assert _infer_outline_level("线性表") == 1


def test_normalized_model_items_infer_level_when_model_omits_it():
    assert _ensure_outline_item_structure({"title": "第一章 绪论"})["level"] == 1
    assert _ensure_outline_item_structure({"title": "1.1 基本术语"})["level"] == 2
    assert _ensure_outline_item_structure({"title": "1.1.1 顺序表"})["level"] == 3


@pytest.mark.parametrize(
    ("value", "expected"),
    [("章节", 1), ("小节", 2), ("节点", 2), ("知识点", 3), ("level 3", 3)],
)
def test_outline_level_parser_accepts_hierarchy_labels(value, expected):
    assert _parse_outline_level(value) == expected


def test_outline_level_inference_uses_page_title_not_last_node_title():
    item = _ensure_outline_item_structure({
        "title": "1.1 基本术语与逻辑结构",
        "nodes": [{"title": "补充说明"}],
    })
    assert item["level"] == 2


def test_flat_topic_outline_gets_mixed_hierarchy_levels():
    items = _topic_outline_items("大学高等数学教育", 5, 5)
    assert [item["level"] for item in items] == [1, 2, 2, 3, 1]


def test_source_recovery_does_not_force_every_heading_to_chapter():
    items = _source_outline_items(
        """# 第一章 基础
## 1.1 基本概念
# 第二章 应用
## 2.1 典型案例""",
        "数据结构",
        8,
    )
    assert [item["level"] for item in items] == [1, 2, 1, 2]


def test_outline_markdown_round_trip_preserves_level():
    source_items = [{"title": "核心概念", "level": 2, "type": "内容页", "keyPoints": ["定义"]}]
    markdown = _outline_markdown_from_items(source_items, "数据结构")
    parsed = _outline_items(markdown)
    assert parsed[0]["level"] == 2


def test_outline_mode_semantics_do_not_depend_on_uploaded_filename_extension():
    request = {"sourceName": "手动输入资料.txt", "sourceFileId": "uploaded-short-topic"}
    short_topic = "大学高等数学教学PPT超级详细"

    assert _normalize_outline_mode("非大纲") == "ai_outline"
    assert _normalize_outline_mode("上传大纲") == "original_outline"
    source = f"{short_topic} 课程资料"
    assert _is_topic_only_outline_request(request, source, short_topic, "ai_outline") is True
    assert _is_topic_only_outline_request(request, source, short_topic, "original_outline") is False


def test_bailian_model_access_error_is_explained_without_leaking_credentials():
    message = _safe_error_message(Exception("Error code: 403 - Access to model denied"))
    assert "百炼拒绝了当前模型访问" in message
    assert "Access to model denied" not in message


def test_material_outline_coverage_repairs_missing_chapters():
    items = [{
        "id": "slide_1",
        "title": "PPT生成",
        "type": "封面页",
        "objective": "",
        "keyPoints": [],
    }, {
        "id": "slide_2",
        "title": "绪论",
        "type": "内容页",
        "objective": "",
        "keyPoints": ["绪论：数据结构基本概念"],
    }]
    source = """数据结构与算法核心知识详解
# 绪论：数据结构基本概念
## 1.1 基本术语
# 线性表
## 2.1 顺序表
# 栈与队列
## 3.1 栈
"""

    repaired, changed = _repair_material_outline_coverage(items, source, "数据结构与算法核心知识详解", 2)

    assert changed is True
    assert repaired[0]["title"] == "数据结构与算法核心知识详解"
    assert any(item["title"] == "全书章节概览" for item in repaired)
    directory = next(item for item in repaired if item["title"] == "全书章节概览")
    assert "线性表" in directory["keyPoints"]
    assert "栈与队列" in directory["keyPoints"]


def test_optional_audience_and_tone_accept_java_null_payloads():
    outline = OutlineRequest(sourceName="source.txt", audience=None, tone=None)
    slides = SlidesRequest(outline={}, audience=None, tone=None)

    assert outline.audience is None
    assert outline.tone is None
    assert slides.audience is None
    assert slides.tone is None


def test_retry_count_is_bounded_and_zero_means_one_attempt():
    calls = []

    def fail():
        calls.append(1)
        raise RuntimeError("temporary")

    with pytest.raises(RuntimeError):
        _retry_llm_call(fail, max_retries=0)
    assert len(calls) == 1

    calls.clear()
    with pytest.raises(RuntimeError):
        _retry_llm_call(fail, max_retries=1, base_delay=0)
    assert len(calls) == 2


def test_task_deadline_transitions_to_terminal_timeout(monkeypatch):
    service = PptGenerationService()
    task_id = "ppt_task_deadline_test"
    service._register_task({
        "taskId": task_id,
        "userId": "42",
        "status": "queued",
        "progress": 0,
        "stage": "queued",
        "deadlineAt": int(time.time() * 1000) + 60,
    })

    for _ in range(30):
        task = service.get_task("42", task_id)
        if task["status"] == "timed_out":
            break
        time.sleep(0.02)

    assert task["status"] == "timed_out"
    assert task["stage"] == "timeout"
    assert task["error"]["type"] == "PptTaskTimeout"


def test_outline_task_returns_before_model_work_and_reaches_terminal_state(monkeypatch):
    markdown = """## PPT 大纲
### 第1页
- 页标题：概念
- 页面类型：内容页
- 本页目标：理解概念
- 核心内容：
  - 定义
- 展示建议：列表
- 素材建议：无
### 第2页
- 页标题：总结
- 页面类型：总结页
- 本页目标：回顾重点
- 核心内容：
  - 要点
- 展示建议：列表
- 素材建议：无
### 第3页
- 页标题：练习
- 页面类型：练习页
- 本页目标：巩固知识
- 核心内容：
  - 题目
- 展示建议：列表
- 素材建议：无
### 第4页
- 页标题：应用
- 页面类型：案例页
- 本页目标：联系实际
- 核心内容：
  - 案例
- 展示建议：列表
- 素材建议：无
### 第5页
- 页标题：结语
- 页面类型：总结页
- 本页目标：结束课程
- 核心内容：
  - 结论
- 展示建议：列表
- 素材建议：无
"""
    monkeypatch.setattr("app.ppt_generation.service.run_specialist_agent", lambda *args, **kwargs: markdown)
    service = PptGenerationService()

    created = service.create_outline_task("42", {
        "sourceName": "资料.txt",
        "sourceContent": "资料内容",
        "pageCount": 5,
    }, llm_config=None)
    assert created["taskId"].startswith("ppt_task_")

    for _ in range(100):
        task = service.get_task("42", created["taskId"])
        if task["status"] in {"completed", "failed", "timed_out"}:
            break
        time.sleep(0.02)

    assert task["status"] == "completed", task.get("error")
    assert len(task["outline"]["items"]) == 5


def test_edited_slide_preview_uses_final_renderer_and_cleans_inline_artifact(tmp_path, monkeypatch):
    monkeypatch.setenv("AI_EXPORT_ROOT", str(tmp_path))
    monkeypatch.setenv("PPT_TEMPLATE_PREVIEW_WARMUP", "0")
    monkeypatch.setattr(generated_exporter, "EXPORT_ROOT", tmp_path)

    def fake_render(slides, title, settings):
        assert len(slides) == 1
        assert settings == {"templateId": "general", "previewOnly": True}
        preview_path = generated_exporter._new_export_path("edited-1", "png")
        preview_path.write_bytes(b"preview-image")
        preview = generated_exporter._attachment_for_file(
            preview_path, "ai_ppt_generation_tool", "PNG", display_stem=f"{title}-1"
        )
        preview.update({"type": "preview", "slideIndex": 1})
        return None, None, [preview], None

    monkeypatch.setattr("app.ppt_generation.service.render_presenton_html", fake_render)
    service = PptGenerationService()
    result = service.render_preview(
        PreviewRequest(
            templateId="general",
            title="数据结构复习",
            slide={
                "index": 1,
                "title": "栈",
                "content": "后进先出",
                "templateLayoutId": "title_intro",
                "ui": _sample_ui(),
            },
        ).model_dump()
    )

    assert result["imageBase64"] == "cHJldmlldy1pbWFnZQ=="
    assert result["mimeType"] == "image/png"
    assert result["slideIndex"] == 1
    assert not list(tmp_path.glob("*"))


def test_task_is_owner_scoped_and_creates_real_preview(tmp_path, monkeypatch):
    monkeypatch.setenv("AI_EXPORT_ROOT", str(tmp_path))
    monkeypatch.setenv("PPT_TEMPLATE_PREVIEW_WARMUP", "0")
    monkeypatch.setattr(generated_exporter, "EXPORT_ROOT", tmp_path)

    def fake_render(slides, title, settings):
        del slides, title, settings
        pptx_path = generated_exporter._new_export_path("rendered", "pptx")
        pptx_path.write_bytes(b"PK\x03\x04")
        pptx = generated_exporter._attachment_for_file(
            pptx_path, "ai_ppt_generation_tool", "PPTX", display_stem="rendered"
        )
        pptx["type"] = "pptx"
        preview_path = generated_exporter._new_export_path("rendered-1", "png")
        preview_path.write_bytes(b"\x89PNG\r\n\x1a\n")
        preview = generated_exporter._attachment_for_file(
            preview_path, "ai_ppt_generation_tool", "PNG", display_stem="rendered-1"
        )
        preview.update({"type": "preview", "slideIndex": 1})
        return None, None, [preview], pptx

    monkeypatch.setattr("app.ppt_generation.service.render_presenton_html", fake_render)
    service = PptGenerationService()
    created = service.create_task("42", {
        "sourceName": "数据结构复习.txt",
        "outline": {"title": "数据结构复习"},
        "slides": [
            {"title": "栈", "content": ["后进先出"], "templateLayoutId": "title_intro", "ui": _sample_ui()},
            {"title": "队列", "content": ["先进先出"], "templateLayoutId": "title_intro", "ui": _sample_ui()},
        ],
        "exportFormats": ["pptx"],
    }, llm_config=None)

    task_id = created["taskId"]
    # 真实 Chromium 启动在 Windows 冷启动时可能超过 3 秒；这不是 PPT
    # 任务超时，测试应等待异步渲染任务完成。
    for _ in range(100):
        task = service.get_task("42", task_id)
        if task["status"] in {"completed", "failed"}:
            break
        time.sleep(0.03)

    assert task["status"] == "completed", task.get("error")
    assert task["attachments"][0]["type"] == "pptx"
    assert task["previews"]
    export = service.open_artifact("42", task_id, "pptx")
    try:
        assert export.stream.read(4) == b"PK\x03\x04"
    finally:
        export.stream.close()

    with pytest.raises(Exception) as error:
        service.get_task("7", task_id)
    assert error.value.status_code == 403


def test_final_task_blocks_unrenderable_slide_before_export(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_EXPORT_ROOT", str(tmp_path))
    monkeypatch.setenv("PPT_TEMPLATE_PREVIEW_WARMUP", "0")
    service = PptGenerationService()
    monkeypatch.setattr(
        "app.ppt_generation.service.render_presenton_html",
        lambda *args, **kwargs: pytest.fail("质量门禁失败后不应进入渲染器"),
    )
    created = service.create_task("42", {
        "sourceName": "bad-layout.txt",
        "outline": {"title": "测试"},
        "slides": [
            {"title": "A", "content": ["a"], "templateLayoutId": "missing", "ui": _sample_ui()},
            {"title": "B", "content": ["b"], "templateLayoutId": "missing", "ui": _sample_ui()},
        ],
        "exportFormats": ["pptx"],
    }, llm_config=None)

    task_id = created["taskId"]
    for _ in range(100):
        task = service.get_task("42", task_id)
        if task["status"] in {"completed", "failed"}:
            break
        time.sleep(0.03)

    assert task["status"] == "failed"
    assert task["stage"] == "quality_check"
    assert task["error"]["type"] == "PptQualityError"
    assert task["qa"]["status"] == "blocked"


def test_outline_provider_failure_returns_actionable_gateway_error(monkeypatch):
    service = PptGenerationService()
    monkeypatch.setattr(
        "app.ppt_generation.service.run_specialist_agent",
        lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("connection refused at http://model.test/v1 token=secret")),
    )

    with pytest.raises(HTTPException) as error:
        service.generate_outline({
            "sourceName": "a.txt",
            "sourceContent": "复习资料",
            "topic": "测试",
            "pageCount": 5,
        }, llm_config=None)

    assert error.value.status_code == 502
    assert "PPT 大纲无法从模型或原始资料中恢复" in error.value.detail
    assert "secret" not in error.value.detail
    assert "http://model.test" not in error.value.detail


def test_component_content_merge_respects_presenton_text_constraints():
    layout = {
        "components": [{
            "type": "text",
            "name": "headline",
            "text": "旧标题",
            "max_length": 12,
            "runs": [{"text": "旧"}, {"text": "残留"}],
        }, {
            "type": "table",
            "name": "table",
            "max_children": 2,
            "columns": ["旧列"],
            "rows": [["旧值"]],
        }]
    }

    merged = _merge_content_into_layout(layout, {
        "headline": "这是一个非常非常长的标题，应该被压缩到模板允许范围内。",
        "table": {
            "columns": ["第一列", "第二列", "第三列"],
            "rows": [["第一行第一列", "第一行第二列", "第一行第三列"], ["第二行第一列", "第二行第二列"], ["第三行"]],
        },
    })

    headline = merged["components"][0]
    assert headline["text"] == "这是一个非常非常长的标题，应该被压缩到模板允许范围内。"
    assert "…" not in headline["text"]
    assert headline["runs"][0]["text"] == headline["text"]
    assert headline["runs"][1]["text"] == ""
    table = merged["components"][1]
    assert table["columns"] == ["第一列", "第二列"]
    assert len(table["rows"]) == 2
    assert all(len(row) <= 2 for row in table["rows"])


def test_slides_keep_presenton_ui_when_structure_agent_breaks_contract(monkeypatch):
    service = PptGenerationService()
    def fake_runner(agent_name, input_text, *args, **kwargs):
        if agent_name == "ppt_structure_agent":
            raise HTTPException(status_code=502, detail="ppt_structure_agent 未返回有效 JSON")
        request = json.loads(input_text)
        slides = []
        for selected in request["selectedLayouts"]:
            layout = selected["layout"]
            slides.append({
                "index": selected["slideIndex"],
                "type": "content",
                "title": "测试页",
                "content": ["测试内容"],
                "objective": "测试目标",
                "visualPrompt": "",
                "speakerNote": "",
                "ui": {"components": deepcopy(layout["components"]), "background": "#FFFFFF"},
            })
        return json.dumps({"slides": slides}, ensure_ascii=False)
    monkeypatch.setattr(
        "app.ppt_generation.service.run_specialist_agent",
        fake_runner,
    )

    result = service.generate_slides({
        "outline": {
            "title": "数据结构复习",
            "items": [
                {
                    "title": "数据结构概述",
                    "type": "内容页",
                    "objective": "理解基本概念",
                    "keyPoints": ["逻辑结构", "存储结构"],
                },
                {
                    "title": "线性表",
                    "type": "内容页",
                    "objective": "掌握线性表",
                    "keyPoints": ["顺序表", "链表"],
                },
            ],
        },
        "settings": {"pptStyle": "simple"},
    }, llm_config=None)

    assert [slide["title"] for slide in result["slides"]] == ["数据结构概述", "线性表"]
    assert all(slide["ui"].get("components") for slide in result["slides"])
    assert result["layoutMarkdown"].startswith("## PPT 布局方案")


def test_generation_filters_layouts_with_unusable_title_geometry():
    service = PptGenerationService()
    catalog = EmbeddedTemplateCatalog()
    unsafe = catalog.get_layout("momentum", "title_charts_card_grid_4598")
    safe = catalog.get_layout("momentum", "title_intro_cards_decorative_frame_6592")

    assert service._layout_is_generation_safe(
        "momentum", "title_charts_card_grid_4598", unsafe
    ) is False
    assert service._layout_is_generation_safe(
        "momentum", "title_intro_cards_decorative_frame_6592", safe
    ) is True
