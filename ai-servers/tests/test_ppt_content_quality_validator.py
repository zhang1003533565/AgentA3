from app.ppt_generation.content_quality_validator import (
    assess_content_quality,
    build_source_trace,
    extract_source_chapters,
)


def test_source_trace_is_stable_and_excludes_document_title_from_chapters():
    source = "# 数据结构课程资料\n\n## 线性表\n顺序存储与链式存储。\n\n## 栈与队列\n先进后出与先进先出。"

    trace = build_source_trace("lesson.md", source)

    assert trace["sha256"]
    assert trace["charCount"] == len(source)
    assert extract_source_chapters(source) == ["线性表", "栈与队列"]
    assert trace["snapshotStored"] is False


def test_content_quality_accepts_grounded_substantive_pages():
    source = "# 算法课\n\n## 线性表\n顺序存储和链式存储的实现与复杂度。\n\n## 栈与队列\n栈遵循后进先出，队列遵循先进先出。"
    outline = {
        "title": "算法课",
        "items": [
            {"title": "线性表", "keyPoints": ["顺序存储", "链式存储"]},
            {"title": "栈与队列", "keyPoints": ["后进先出", "先进先出"]},
        ],
    }
    slides = [
        {
            "title": "线性表",
            "type": "content",
            "content": [
                "顺序表使用连续内存，按下标访问的时间复杂度为 O(1)。",
                "链表通过指针连接节点，插入删除不需要移动整段元素。",
            ],
            "sourceMaterial": "顺序存储和链式存储的实现与复杂度。",
        },
        {
            "title": "栈与队列",
            "type": "content",
            "content": [
                "栈只允许在一端进出，后进先出，适合表达函数调用和撤销操作。",
                "队列从尾部进入、头部离开，先进先出，常用于任务调度。",
            ],
            "sourceMaterial": "栈遵循后进先出，队列遵循先进先出。",
        },
    ]

    report = assess_content_quality(source, outline, slides)

    assert report["status"] == "complete"
    assert report["errorCount"] == 0
    assert report["warningCount"] == 0
    assert report["outline"]["chapterCoverage"] == 1.0
    assert all(page["evidenceChars"] > 0 for page in report["slides"])


def test_content_quality_catches_placeholders_thin_content_duplicates_and_order_break():
    source = "# 课程资料\n\n## 线性表\n线性表的存储结构。\n\n## 栈与队列\n两种受限线性结构。"
    outline = {
        "title": "课程资料",
        "items": [
            {"title": "线性表", "keyPoints": ["存储结构", "访问方式"]},
            {"title": "栈与队列", "keyPoints": ["受限线性结构", "应用场景"]},
        ],
    }
    slides = [
        {
            "title": "栈与队列",
            "type": "content",
            "content": ["存储结构"],
            "sourceMaterial": "两种受限线性结构。",
            "ui": {"components": [{"text": "Metric"}, {"text": "Metric"}]},
        },
        {
            "title": "线性表",
            "type": "content",
            "content": ["线性表的存储结构……", "线性表的存储结构……"],
            "sourceMaterial": "线性表的存储结构。",
        },
    ]

    report = assess_content_quality(source, outline, slides)
    codes = {issue["code"] for issue in report["issues"]}

    assert report["status"] == "failed"
    assert "TEMPLATE_PLACEHOLDER_LEAK" in codes
    assert "THIN_PAGE_CONTENT" in codes
    assert "OUTLINE_ONLY_CONTENT" in codes
    assert "CONTENT_TRUNCATED" in codes
    assert "DUPLICATE_PAGE_CONTENT" in codes
    assert "SOURCE_CHAPTER_ORDER_MISMATCH" in codes
