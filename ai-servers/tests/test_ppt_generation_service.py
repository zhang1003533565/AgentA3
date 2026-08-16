import json
import time
from copy import deepcopy

import pytest
from fastapi import HTTPException

from app.ppt_generation.service import PptGenerationService, _outline_items
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog


def _sample_ui(layout_id="title_intro"):
    layout = EmbeddedTemplateCatalog().get_layout("general", layout_id)
    return {"components": deepcopy(layout["components"]), "background": "#FFFFFF"}


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
    }]


def test_task_is_owner_scoped_and_creates_real_preview(tmp_path, monkeypatch):
    monkeypatch.setenv("AI_EXPORT_ROOT", str(tmp_path))
    service = PptGenerationService()
    created = service.create_task("42", {
        "sourceName": "数据结构复习.txt",
        "outline": {"title": "数据结构复习"},
        "slides": [
            {"title": "栈", "content": ["后进先出"], "templateLayoutId": "title_intro", "ui": _sample_ui()},
            {"title": "队列", "content": ["先进先出"], "templateLayoutId": "title_intro", "ui": _sample_ui()},
        ],
        "exportFormats": ["pdf"],
    }, llm_config=None)

    task_id = created["taskId"]
    for _ in range(100):
        task = service.get_task("42", task_id)
        if task["status"] in {"completed", "failed"}:
            break
        time.sleep(0.03)

    assert task["status"] == "completed", task.get("error")
    assert task["attachments"][0]["type"] == "pdf"
    assert task["previews"]
    export = service.open_artifact("42", task_id, "pdf")
    try:
        assert export.stream.read(4) == b"%PDF"
    finally:
        export.stream.close()

    with pytest.raises(Exception) as error:
        service.get_task("7", task_id)
    assert error.value.status_code == 403


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
    assert "PPT 大纲模型调用失败" in error.value.detail
    assert "secret" not in error.value.detail
    assert "http://model.test" not in error.value.detail


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
