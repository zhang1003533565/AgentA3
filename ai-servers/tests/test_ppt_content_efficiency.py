import json
from types import SimpleNamespace

import pytest
from fastapi import HTTPException

from app.ppt_generation import service as svc
from app.ppt_generation.service import PptGenerationService
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.model_providers.runtime_config import (
    LlmRuntimeConfig,
    get_active_llm_config,
    set_active_llm_config,
)


def test_quality_repair_only_regenerates_flagged_pages(monkeypatch):
    service = PptGenerationService()
    catalog = EmbeddedTemplateCatalog()
    layout_id = "title_intro"
    layout = catalog.get_layout("general", layout_id)
    items = [
        {
            "title": "栈",
            "type": "内容页",
            "objective": "理解栈",
            "keyPoints": ["后进先出"],
        },
        {
            "title": "队列",
            "type": "内容页",
            "objective": "理解队列",
            "keyPoints": ["先进先出"],
        },
    ]
    calls = []

    def fake_runner(agent_name, input_text, evidence):
        del evidence
        assert agent_name == "ppt_content_agent"
        request = json.loads(input_text)
        calls.append(request)
        current_slides = request["currentSlides"]
        if request.get("contentQualityCorrection"):
            slides = [{
                "index": request["selectedLayouts"][offset]["slideIndex"],
                "type": "content",
                "title": slide["title"],
                "content": [f"{slide['title']}：该结构按规则组织数据，操作顺序决定元素的访问结果。"],
                "componentContent": {},
            } for offset, slide in enumerate(current_slides)]
        else:
            slides = [{
                "index": request["selectedLayouts"][offset]["slideIndex"],
                "type": "content",
                "title": slide["title"],
                "content": slide["keyPoints"],
                "componentContent": {},
            } for offset, slide in enumerate(current_slides)]
        return json.dumps({"slides": slides}, ensure_ascii=False)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_runner)
    monkeypatch.setattr(svc, "PPT_CONTENT_BATCH_SIZE", 5)
    monkeypatch.setattr(svc, "PPT_CONTENT_MAX_WORKERS", 1)
    monkeypatch.setattr(svc, "PPT_CONTENT_QUALITY_REPAIR", True)

    result = service._generate_presenton_ui_slides(
        outline={"title": "数据结构", "items": items},
        items=items,
        settings={},
        template_id="general",
        selected_layouts=[layout_id, layout_id],
        layouts_by_id={layout_id: layout},
        shared_prompt="",
        source="栈和队列的资料",
        llm_config=None,
    )

    assert len(calls) == 2
    assert len(calls[0]["currentSlides"]) == 2
    assert [len(call["currentSlides"]) for call in calls[1:]] == [2]
    assert result[0]["content"][0] != items[0]["keyPoints"][0]
    assert result[1]["content"][0] != items[1]["keyPoints"][0]
    assert len(result) == 2


@pytest.mark.parametrize("failure_mode", ["raw", "http_502"])
def test_invalid_json_batch_splits_into_single_page_requests(monkeypatch, failure_mode):
    service = PptGenerationService()
    layout_id = "title_intro"
    layout = EmbeddedTemplateCatalog().get_layout("general", layout_id)
    items = [
        {
            "title": "栈",
            "type": "内容页",
            "objective": "理解栈",
            "keyPoints": ["后进先出"],
        },
        {
            "title": "队列",
            "type": "内容页",
            "objective": "理解队列",
            "keyPoints": ["先进先出"],
        },
    ]
    batch_sizes = []

    def fake_runner(agent_name, input_text, evidence):
        del evidence
        assert agent_name == "ppt_content_agent"
        request = json.loads(input_text)
        current_slides = request["currentSlides"]
        batch_sizes.append(len(current_slides))
        if len(current_slides) > 1:
            if failure_mode == "http_502":
                raise HTTPException(status_code=502, detail="ppt_content_agent 未返回有效 JSON")
            return "not-json"
        slide = current_slides[0]
        return json.dumps({
            "slides": [{
                "index": request["selectedLayouts"][0]["slideIndex"],
                "type": "content",
                "title": slide["title"],
                "content": [f"{slide['title']}：具体原理与应用"],
                "componentContent": {},
            }],
        }, ensure_ascii=False)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_runner)
    monkeypatch.setattr(svc, "PPT_CONTENT_BATCH_SIZE", 2)
    monkeypatch.setattr(svc, "PPT_CONTENT_MAX_WORKERS", 1)
    monkeypatch.setattr(svc, "PPT_CONTENT_QUALITY_REPAIR", False)

    result = service._generate_presenton_ui_slides(
        outline={"title": "数据结构", "items": items},
        items=items,
        settings={},
        template_id="general",
        selected_layouts=[layout_id, layout_id],
        layouts_by_id={layout_id: layout},
        shared_prompt="",
        source="栈和队列的资料",
        llm_config=None,
    )

    assert batch_sizes == [2, 1, 1]
    assert len(result) == 2
    assert [slide["title"] for slide in result] == ["栈", "队列"]
    assert all("_generationError" not in slide for slide in result)


def test_content_concurrency_is_limited_by_provider_safety(monkeypatch):
    monkeypatch.setattr(svc, "PPT_CONTENT_MAX_WORKERS", 2)

    assert svc._content_worker_count(SimpleNamespace(provider="aliyun"), 4) == 2
    assert svc._content_worker_count(SimpleNamespace(provider="opencode"), 4) == 1
    assert svc._content_worker_count(SimpleNamespace(provider="unknown"), 4) == 1


def test_outline_successful_model_is_reused_for_slide_generation():
    service = PptGenerationService()
    primary = LlmRuntimeConfig(
        provider="aliyun",
        base_url="https://llm.example/v1",
        api_key="primary-key",
        model="deepseek-v4-flash-0731",
    )
    outline = {
        "outlineId": "outline-model-reuse",
        "_llmSelection": {"provider": "aliyun", "model": "glm-5.2"},
    }

    selected = service._llm_config_for_outline(outline, primary)

    assert selected.provider == "aliyun"
    assert selected.base_url == primary.base_url
    assert selected.api_key == primary.api_key
    assert selected.model == "glm-5.2"


def test_content_batches_reuse_model_selected_by_previous_batch(monkeypatch):
    service = PptGenerationService()
    layout_id = "title_intro"
    layout = EmbeddedTemplateCatalog().get_layout("general", layout_id)
    items = [
        {"title": "栈", "type": "内容页", "objective": "理解栈", "keyPoints": ["后进先出"]},
        {"title": "队列", "type": "内容页", "objective": "理解队列", "keyPoints": ["先进先出"]},
    ]
    primary = LlmRuntimeConfig(
        provider="aliyun",
        base_url="https://llm.example/v1",
        api_key="primary-key",
        model="deepseek-v4-flash-0731",
    )
    calls = []

    def fake_runner(agent_name, input_text, evidence):
        del evidence
        assert agent_name == "ppt_content_agent"
        request = json.loads(input_text)
        active = get_active_llm_config()
        calls.append(active.model if active else "")
        if len(calls) == 1:
            token = set_active_llm_config(
                LlmRuntimeConfig(
                    provider="aliyun",
                    base_url="https://llm.example/v1",
                    api_key="primary-key",
                    model="glm-5.2",
                )
            )
            # The content worker captures this active config before restoring
            # its request context in finally.
            del token
        slide = request["currentSlides"][0]
        return json.dumps({
            "slides": [{
                "index": request["selectedLayouts"][0]["slideIndex"],
                "type": "content",
                "title": slide["title"],
                "content": [f"{slide['title']}：具体原理与应用"],
                "componentContent": {},
            }],
        }, ensure_ascii=False)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_runner)
    monkeypatch.setattr(svc, "PPT_CONTENT_BATCH_SIZE", 1)
    monkeypatch.setattr(svc, "PPT_CONTENT_MAX_WORKERS", 1)
    monkeypatch.setattr(svc, "PPT_CONTENT_QUALITY_REPAIR", False)

    result = service._generate_presenton_ui_slides(
        outline={"title": "数据结构", "items": items},
        items=items,
        settings={},
        template_id="general",
        selected_layouts=[layout_id, layout_id],
        layouts_by_id={layout_id: layout},
        shared_prompt="",
        source="栈和队列的资料",
        llm_config=primary,
    )

    assert len(result) == 2
    assert calls == ["deepseek-v4-flash-0731", "glm-5.2"]
