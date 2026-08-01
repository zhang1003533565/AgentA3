import time

import pytest

from app.ppt_generation.service import PptGenerationService, _outline_items


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


def test_task_is_owner_scoped_and_creates_real_pptx(tmp_path, monkeypatch):
    monkeypatch.setenv("AI_EXPORT_ROOT", str(tmp_path))
    service = PptGenerationService()
    created = service.create_task("42", {
        "sourceName": "数据结构复习.txt",
        "outline": {"title": "数据结构复习"},
        "slides": [
            {"title": "栈", "content": ["后进先出"]},
            {"title": "队列", "content": ["先进先出"]},
        ],
        "exportFormats": ["pptx"],
    }, llm_config=None)

    task_id = created["taskId"]
    for _ in range(100):
        task = service.get_task("42", task_id)
        if task["status"] in {"completed", "failed"}:
            break
        time.sleep(0.03)

    assert task["status"] == "completed", task.get("error")
    assert task["attachments"][0]["type"] == "pptx"
    export = service.open_artifact("42", task_id, "pptx")
    try:
        assert export.stream.read(2) == b"PK"
    finally:
        export.stream.close()

    with pytest.raises(Exception) as error:
        service.get_task("7", task_id)
    assert error.value.status_code == 403
