from __future__ import annotations

from io import BytesIO
from pathlib import Path

import pytest
from pptx import Presentation

from app.ppt_generation.presenton_renderer import render_presenton_presentation
from app.ppt_generation.service import PptGenerationService
from app.ppt_generation.source_parser import extract_source_text
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog


def test_embedded_engine_is_default(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_PPT_SOURCE_ROOT", str(tmp_path / "sources"))
    service = PptGenerationService()

    options = service.get_options()

    assert options["engine"] == "presenton-embedded"
    assert options["enhancedEngineAvailable"] is True
    assert options["editorEnabled"] is False
    assert len(options["templates"]) == 7


def test_template_catalog_reads_bundled_resources():
    catalog = EmbeddedTemplateCatalog()
    ids = {item["id"] for item in catalog.list_templates()}

    assert ids == {"dynamic", "executive", "general", "modern", "momentum", "standard", "swift"}
    content, content_type = catalog.thumbnail("general")
    assert content.startswith(b"\x89PNG")
    assert content_type == "image/png"
    with pytest.raises(FileNotFoundError):
        catalog.thumbnail("../general")
    assert len(catalog.layout_summaries("general")) == 12
    assert {"text", "image"}.issubset(set(catalog.layout_summaries("general")[0]["elementTypes"]))


def test_uploaded_source_file_is_local_and_owner_scoped(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_PPT_SOURCE_ROOT", str(tmp_path / "sources"))
    service = PptGenerationService()

    uploaded = service.upload_source_file("42", "material.txt", "text/plain", "栈与队列".encode())
    owned = service._source_files.get_owned("42", uploaded["fileId"])

    assert uploaded["fileId"].startswith("ppt_file_")
    assert Path(owned["localPath"]).is_file()
    assert "localPath" not in uploaded
    assert service._source_files.get_owned("7", uploaded["fileId"]) is None


def test_source_parser_supports_txt_and_pptx(tmp_path):
    txt = tmp_path / "material.txt"
    txt.write_text("数据结构复习", encoding="utf-8")
    assert extract_source_text(txt) == "数据结构复习"

    pptx = tmp_path / "material.pptx"
    presentation = Presentation()
    slide = presentation.slides.add_slide(presentation.slide_layouts[1])
    slide.shapes.title.text = "栈"
    slide.placeholders[1].text = "后进先出"
    presentation.save(pptx)
    assert "后进先出" in extract_source_text(pptx)


@pytest.mark.parametrize("template_id", [
    "dynamic", "executive", "general", "modern", "momentum", "standard", "swift",
])
def test_presenton_renderer_creates_editable_pptx(monkeypatch, tmp_path, template_id):
    monkeypatch.setenv("AI_EXPORT_ROOT", str(tmp_path / "exports"))
    slides = [
        {"type": "cover", "title": "数据结构复习", "content": ["期末核心知识梳理"]},
        {"type": "content", "title": "栈", "content": ["后进先出", "入栈与出栈", "典型应用"]},
        {"type": "content", "title": "队列", "content": ["先进先出", "顺序队列", "循环队列", "链式队列"]},
    ]

    attachment, path = render_presenton_presentation(
        slides,
        "数据结构复习",
        {"templateId": template_id},
    )

    assert attachment["type"] == "pptx"
    assert attachment["templateId"] == template_id
    assert len(attachment["templateLayoutIds"]) == 3
    assert path.read_bytes().startswith(b"PK")
    rendered = Presentation(BytesIO(path.read_bytes()))
    assert len(rendered.slides) == 3
    assert any("数据结构复习" in shape.text for shape in rendered.slides[0].shapes if hasattr(shape, "text"))


def test_task_cancel_is_persisted_and_owner_scoped(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_PPT_SOURCE_ROOT", str(tmp_path / "sources"))
    service = PptGenerationService()
    task = {
        "taskId": "ppt_task_0123456789abcdef0123456789abcdef",
        "userId": "42",
        "status": "running",
        "stage": "rendering",
        "progress": 50,
        "attachments": [],
        "previews": [],
    }
    service._task_store.put(task)

    assert service.cancel_task("42", task["taskId"])["status"] == "cancelled"
    with pytest.raises(Exception) as error:
        service.cancel_task("7", task["taskId"])
    assert error.value.status_code == 403
