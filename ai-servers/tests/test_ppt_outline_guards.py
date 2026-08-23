"""Outline page-count guards + template slot completeness tests.

- 大纲只生成一条时：纠正重试 → 单页拆页兜底 → 502 拦截
- AI 漏填槽位时：按大纲数据回填，不产生孤立空槽
- componentSchema 携带槽位 role（标题/正文/卡片/标签）
"""

import copy
import json

import pytest
from fastapi import HTTPException

from app.ppt_generation.service import (
    PptGenerationService,
    _expand_single_page_outline,
    _fill_missing_slots,
    _outline_items,
    _outline_markdown_from_items,
    _outline_output_token_budget,
    _compact_outline_source,
    _repair_outline_sparse_pages,
    _resolve_outline_topic,
    _is_topic_only_outline_request,
    _source_outline_items,
    _content_quality_flags,
    _topic_outline_items,
    _outline_topic_guidance,
    _sanitize_content_payload,
)
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog
from app.ppt_generation.template_model import parse_slide_layout


@pytest.fixture(scope="module")
def catalog():
    return EmbeddedTemplateCatalog()

SINGLE_PAGE_MARKDOWN = """## PPT 大纲

### 大纲信息
- 主题：数据结构
- 受众：学生
- 建议页数：5 页
- 整体目标：掌握核心概念
- 风格建议：简洁

### 第1页
- 页标题：数据结构总览
- 页面类型：内容页
- 本页目标：了解全貌
- 核心内容：
  - 逻辑结构
  - 存储结构
  - 常见操作
- 展示建议：要点列表
- 素材建议：结构图
"""


def _full_markdown(page_count: int) -> str:
    lines = ["## PPT 大纲", "", "### 大纲信息", "- 主题：T", "- 受众：学生",
             f"- 建议页数：{page_count} 页", "- 整体目标：x", "- 风格建议：简洁", ""]
    for index in range(1, page_count + 1):
        lines += [f"### 第{index}页", f"- 页标题：第{index}页标题", "- 页面类型：内容页",
                  "- 本页目标：目标", "- 核心内容：", f"  - 要点{index}", "- 展示建议：x", "- 素材建议：y", ""]
    return "\n".join(lines)


def test_expand_single_page_splits_keypoints():
    items = [{"id": "slide_1", "level": 1, "title": "总览", "type": "内容页",
              "objective": "x", "keyPoints": ["逻辑结构", "存储结构", "常见操作"]}]
    expanded = _expand_single_page_outline(items, "数据结构")
    assert len(expanded) == 3
    assert expanded[0]["type"] == "封面页"
    assert expanded[-1]["type"] == "总结页"
    assert [item["keyPoints"] for item in expanded] == [["逻辑结构"], ["存储结构"], ["常见操作"]]


def test_expand_keeps_multiple_pages_untouched():
    items = _outline_items(_full_markdown(3))
    assert _expand_single_page_outline(items, "T") == items


def test_expand_without_points_stays_single():
    items = [{"id": "slide_1", "level": 1, "title": "T", "type": "内容页", "objective": "", "keyPoints": []}]
    assert _expand_single_page_outline(items, "T") == items


def test_outline_markdown_rebuild_roundtrip():
    items = _expand_single_page_outline(
        [{"id": "slide_1", "level": 1, "title": "总览", "type": "内容页", "objective": "x",
          "keyPoints": ["逻辑结构", "存储结构", "常见操作"]}],
        "数据结构",
    )
    markdown = _outline_markdown_from_items(items, "数据结构")
    reparsed = _outline_items(markdown)
    assert len(reparsed) == 3
    assert [item["title"] for item in reparsed] == [item["title"] for item in items]
    assert [item["keyPoints"] for item in reparsed] == [item["keyPoints"] for item in items]


NONSTANDARD_HEADING_MARKDOWN = """## PPT 大纲

### 大纲信息
- 主题：成都理工大学
- 受众：学生
- 建议页数：15 页
- 整体目标：系统梳理
- 风格建议：简洁

### 学校概况
- 页标题：学校概况
- 页面类型：内容页
- 本页目标：了解校情
- 核心内容：
  - 学校全称成都理工大学
  - 办学类型与层次
- 展示建议：卡片式
- 素材建议：校园图

### 历史沿革
- 页标题：历史沿革
- 页面类型：内容页
- 本页目标：梳理发展脉络
- 核心内容：
  - 建校时间
  - 重要节点
- 展示建议：时间轴
- 素材建议：时间轴图

### 学科专业
- 页标题：学科专业
- 页面类型：内容页
- 本页目标：介绍学科
- 核心内容：
  - 优势学科
  - 特色专业
- 展示建议：列表
- 素材建议：图标
"""


def test_content_style_headings_are_not_collapsed_into_one_page():
    """模型用内容型标题（### 学校概况）而非编号标题时，多页不得被压成一页。"""
    from app.multi_agents.ppt_outline_agent.agent import normalize_ppt_outline_answer

    normalized = normalize_ppt_outline_answer(NONSTANDARD_HEADING_MARKDOWN, "")
    items = _outline_items(normalized)
    assert len(items) == 3
    assert [item["title"] for item in items] == ["学校概况", "历史沿革", "学科专业"]
    assert items[0]["keyPoints"] == ["学校全称成都理工大学", "办学类型与层次"]


def test_generate_outline_accepts_ai_chosen_page_count(monkeypatch):
    """页数由 AI 自主决定：8 页（而非固定 15 页）应被接受，prompt 携带 min/max 区间。"""
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(prompt)
        return _full_markdown(8)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    service = PptGenerationService()
    result = service.generate_outline({"topic": "T", "sourceContent": "资料内容"}, None)
    assert len(result["items"]) == 8  # AI 自主的 8 页被接受，不再强制 15
    assert len(calls) == 1  # 无重试
    assert "min_pages" in calls[0]
    assert "max_pages" in calls[0]
    assert '"max_pages": 30' in calls[0]
    assert "slide_count" not in calls[0]


def test_outline_request_default_page_count_is_30():
    """接口默认上限与服务/前端保持一致，不再把未指定请求锁在 15 页。"""
    from app.ppt_generation.routes import OutlineRequest

    assert OutlineRequest(sourceName="资料").pageCount == 30


def test_outline_budget_scales_with_page_count():
    assert _outline_output_token_budget(5, 5) < _outline_output_token_budget(5, 30)
    assert _outline_output_token_budget(5, 5) >= 3200


def test_long_outline_source_keeps_structure_and_tail_without_overflow():
    source = "# 第一章 基础\n" + "\n".join(f"基础定义{i}。" for i in range(1800))
    source += "\n# 第二章 应用\n应用案例与结论。\n"
    compacted = _compact_outline_source(source, 2000)

    assert len(compacted) <= 2000
    assert "第一章" in compacted
    assert "第二章" in compacted
    assert "资料结尾" in compacted


def test_sparse_outline_repair_only_replaces_targeted_page(monkeypatch):
    from app.ppt_generation import service as svc

    items = _outline_items(_full_markdown(2))
    original_second = copy.deepcopy(items[1])
    repaired_markdown = """## PPT 大纲

### 第1页
- 页标题：第1页标题
- 页面类型：内容页
- 本页目标：补足本页信息
- 核心内容：
  - 关键事实一
  - 关键事实二
  - 关键事实三
- 页面节点：
  - 节点1：概念｜说明概念
  - 节点2：应用｜说明应用
- 展示建议：分层展示
- 素材建议：结构图
"""

    monkeypatch.setattr(svc, "run_specialist_agent", lambda *_args, **_kwargs: repaired_markdown)
    updated, changed = _repair_outline_sparse_pages(
        items,
        {"topic": "T", "source_mode": "non_outline", "material": "资料"},
        "T",
        [],
    )

    assert changed is True
    assert len(updated[0]["keyPoints"]) == 3
    assert updated[1] == original_second


def test_generate_outline_respects_user_max_pages(monkeypatch):
    """用户档位作为上限：pageCount=6 时 max_pages=6，AI 输出 6 页被接受。"""
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(prompt)
        return _full_markdown(6)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    service = PptGenerationService()
    result = service.generate_outline({"topic": "T", "pageCount": 6, "sourceContent": "资料内容"}, None)
    assert len(result["items"]) == 6
    assert '"max_pages": 6' in calls[0]


def test_topic_only_request_enables_general_knowledge_expansion(monkeypatch):
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(json.loads(prompt))
        return _full_markdown(5)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    result = PptGenerationService().generate_outline(
        {
            "topic": "大学高等数学教育学PPT",
            "sourceContent": "大学高等数学教育学PPT",
            "pageCount": 5,
        },
        None,
    )

    assert len(result["items"]) == 5
    assert calls[0]["source_mode"] == "topic_only"
    assert "通用知识" in calls[0]["constraints"]
    assert "统计数字" in calls[0]["constraints"]


def test_topic_only_sparse_outline_retries_for_depth(monkeypatch):
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(json.loads(prompt))
        return _full_markdown(5)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    result = PptGenerationService().generate_outline(
        {
            "topic": "大学高等数学教育学PPT",
            "sourceContent": "大学高等数学教育学PPT",
            "pageCount": 5,
        },
        None,
    )

    assert len(result["items"]) == 5
    assert len(calls) == 2
    assert "页面节点" in calls[1]["correction"]


def test_explicit_non_outline_mode_allows_framework_expansion(monkeypatch):
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(json.loads(prompt))
        return _full_markdown(5)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    result = PptGenerationService().generate_outline(
        {
            "topic": "高等数学教学",
            "sourceName": "手动输入资料.txt",
            "sourceContent": "高等数学教学。课程资料与零散知识点，需要系统整理。",
            "outlineMode": "ai_outline",
            "pageCount": 5,
        },
        None,
    )

    assert len(result["items"]) == 5
    assert calls[0]["source_mode"] == "non_outline"
    assert calls[0]["outline_mode"] == "ai_outline"
    assert "重新搭建" in calls[0]["constraints"]


def test_topic_guidance_uses_semantic_career_arc_without_layout_leakage():
    guidance = _outline_topic_guidance("大学计算机就业方向简介", "学生", "topic_only")

    assert "岗位" in guidance["recommended_narrative"] or "方向" in guidance["recommended_narrative"]
    assert any("能力" in item for item in guidance["must_cover"])
    assert any("方向" in item or "下一步" in item for item in guidance["must_cover"])
    assert all("layoutId" not in item for item in guidance["avoid"])


def test_outline_prompt_separates_topic_logic_from_ppt_format(monkeypatch):
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(json.loads(prompt))
        return _full_markdown(5)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    PptGenerationService().generate_outline(
        {
            "topic": "大学计算机就业方向简介",
            "sourceContent": "大学计算机就业方向简介",
            "audience": "学生",
            "pageCount": 5,
        },
        None,
    )

    prompt = calls[0]
    assert "topic_interpretation" in prompt
    assert "岗位" in prompt["topic_interpretation"]["recommended_narrative"]
    assert "format_boundary" in prompt["planning_requirements"]
    assert "不要把所有主题套入同一套课程知识结构" in prompt["planning_requirements"]["topic_first"]


def test_generic_route_topic_is_replaced_by_short_manual_input(monkeypatch):
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(json.loads(prompt))
        return _full_markdown(5)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    result = PptGenerationService().generate_outline(
        {
            "topic": "PPT生成",
            "sourceName": "手动输入资料.txt",
            "sourceContent": "大学高等数学教育学PPT",
            "pageCount": 5,
        },
        None,
    )

    assert result["title"] == "大学高等数学教育学PPT"
    assert calls[0]["topic"] == "大学高等数学教育学PPT"


def test_uploaded_filename_replaces_generic_outline_title(monkeypatch):
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(json.loads(prompt))
        return _full_markdown(5)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    result = PptGenerationService().generate_outline(
        {
            "topic": "PPT生成",
            "sourceName": "数据结构与算法核心知识详解.docx",
            "sourceContent": "# 第一章 绪论\n数据结构的基本概念。",
            "pageCount": 5,
        },
        None,
    )

    assert result["title"] == "数据结构与算法核心知识详解"
    assert calls[0]["topic"] == "数据结构与算法核心知识详解"


def test_default_model_titles_are_replaced_by_resolved_topic(monkeypatch):
    from app.ppt_generation import service as svc

    raw = _full_markdown(5).replace("第1页标题", "PPT生成").replace("第2页标题", "PPT生成补充与总结")

    monkeypatch.setattr(svc, "run_specialist_agent", lambda *_args, **_kwargs: raw)
    result = PptGenerationService().generate_outline(
        {
            "topic": "PPT生成",
            "sourceName": "手动输入资料.txt",
            "sourceContent": "大学高等数学教育学PPT",
            "pageCount": 5,
        },
        None,
    )

    assert result["items"][0]["title"] == "大学高等数学教育学PPT"
    assert result["items"][1]["title"] == "补充与总结"


def test_outline_normalizer_reads_topic_from_json_input():
    from app.multi_agents.ppt_outline_agent.agent import normalize_ppt_outline_answer

    answer = json.dumps({"slides": [{"title": "核心内容", "keyPoints": ["要点"]}]}, ensure_ascii=False)
    input_text = json.dumps({"topic": "大学高等数学教育学PPT", "audience": "学生"}, ensure_ascii=False)
    normalized = normalize_ppt_outline_answer(answer, input_text)

    assert "- 主题：大学高等数学教育学PPT" in normalized


def test_structured_outline_preserves_page_nodes_and_rich_fields():
    from app.multi_agents.ppt_outline_agent.agent import normalize_ppt_outline_answer

    answer = json.dumps({
        "title": "高等数学教育",
        "slides": [{
            "title": "极限思想",
            "type": "内容页",
            "objective": "理解极限思想的作用",
            "keyPoints": ["从变化趋势刻画对象", "为导数和积分提供基础"],
            "nodes": [
                {"title": "变化趋势", "content": "描述变量接近目标状态时的变化规律"},
                {"title": "后续连接", "content": "说明极限如何支撑导数与积分"},
            ],
            "displaySuggestion": "先建立直观认识，再落到概念关系",
            "assetSuggestion": "概念关系图",
        }],
    }, ensure_ascii=False)
    normalized = normalize_ppt_outline_answer(answer, '{"topic":"高等数学教育"}')
    items = _outline_items(normalized)

    assert items[0]["nodes"] == [
        {"id": "node_1", "level": 2, "title": "变化趋势", "content": "描述变量接近目标状态时的变化规律"},
        {"id": "node_2", "level": 2, "title": "后续连接", "content": "说明极限如何支撑导数与积分"},
    ]
    assert items[0]["displaySuggestion"] == "先建立直观认识，再落到概念关系"
    assert items[0]["assetSuggestion"] == "概念关系图"


def test_rebuilt_outline_keeps_nodes_in_markdown_and_items():
    items = [{
        "id": "slide_1",
        "level": 1,
        "title": "知识结构",
        "type": "内容页",
        "objective": "建立整体认识",
        "keyPoints": ["概念关系", "应用路径"],
        "nodes": [
            {"id": "n1", "level": 2, "title": "概念关系", "content": "说明概念之间如何衔接"},
            {"id": "n2", "level": 2, "title": "应用路径", "content": "说明知识如何进入实践"},
        ],
        "displaySuggestion": "关系图配合分步说明",
        "assetSuggestion": "关系图",
    }]

    reparsed = _outline_items(_outline_markdown_from_items(items, "主题"))[0]
    assert reparsed["nodes"][0]["title"] == "概念关系"
    assert reparsed["nodes"][1]["content"] == "说明知识如何进入实践"
    assert reparsed["displaySuggestion"] == "关系图配合分步说明"
    assert reparsed["assetSuggestion"] == "关系图"


def test_topic_only_single_page_is_recovered_to_editable_scaffold(monkeypatch):
    from app.ppt_generation import service as svc

    def fake_run_specialist_agent(agent, prompt, evidence):
        return SINGLE_PAGE_MARKDOWN

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    result = PptGenerationService().generate_outline(
        {
            "topic": "大学高等数学教育学PPT",
            "sourceContent": "大学高等数学教育学PPT",
            "pageCount": 5,
        },
        None,
    )

    assert len(result["items"]) == 5
    assert result["generationMode"] == "topic_recovery"
    assert result["items"][0]["title"] == "大学高等数学教育学PPT"
    assert result["items"][-1]["type"] == "总结页"


def test_short_material_is_not_treated_as_topic_only():
    assert not _is_topic_only_outline_request(
        {"topic": "数据结构", "sourceName": "资料.txt"},
        "资料内容",
        "数据结构",
    )


def test_topic_outline_scaffold_respects_page_range():
    items = _topic_outline_items("主题", 5, 5)
    assert len(items) == 5
    assert items[0]["type"] == "封面页"
    assert items[-1]["type"] == "总结页"


def test_generate_outline_retries_with_correction_on_single_page(monkeypatch):
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent, prompt, evidence):
        calls.append(prompt)
        if len(calls) == 1:
            return SINGLE_PAGE_MARKDOWN
        return _full_markdown(5)

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    service = PptGenerationService()
    result = service.generate_outline({"topic": "数据结构", "pageCount": 5, "sourceContent": "资料内容"}, None)
    assert len(result["items"]) == 5
    assert len(calls) == 2  # 第一次失败，第二次带纠正重试
    assert "correction" in calls[1]
    assert result["outlineMarkdown"].startswith("## PPT 大纲")


def test_generate_outline_reports_real_generation_stages(monkeypatch):
    from app.ppt_generation import service as svc

    monkeypatch.setattr(svc, "run_specialist_agent", lambda *args, **kwargs: _full_markdown(5))
    events = []
    service = PptGenerationService()

    result = service.generate_outline(
        {"topic": "数据结构", "pageCount": 5, "sourceContent": "资料内容"},
        None,
        progress_callback=events.append,
    )

    assert len(result["items"]) == 5
    assert [event["stage"] for event in events] == [
        "preparing",
        "planning",
        "model_generation",
        "parsing",
        "quality_check",
        "finalizing",
    ]
    assert [event["progress"] for event in events] == sorted(event["progress"] for event in events)
    assert events[-1]["progress"] == 96


def test_generate_outline_expands_single_page_after_all_retries(monkeypatch):
    from app.ppt_generation import service as svc

    def fake_run_specialist_agent(agent, prompt, evidence):
        return SINGLE_PAGE_MARKDOWN  # 模型始终只返回一页

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    service = PptGenerationService()
    result = service.generate_outline({"topic": "数据结构", "pageCount": 5, "sourceContent": "资料内容"}, None)
    # 重试耗尽后拆页兜底：3 个要点 → 3 页
    assert len(result["items"]) == 3
    assert result["outlineMarkdown"].startswith("## PPT 大纲")


def test_generate_outline_recovers_from_unexpandable_model_output(monkeypatch):
    from app.ppt_generation import service as svc

    def fake_run_specialist_agent(agent, prompt, evidence):
        return "## PPT 大纲\n### 大纲信息\n- 主题：T\n- 受众：学生\n- 建议页数：5 页\n- 整体目标：x\n- 风格建议：简洁\n\n### 第1页\n- 页标题：只有一页\n- 页面类型：内容页\n- 本页目标：x\n- 核心内容：\n- 展示建议：x\n- 素材建议：y\n"

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    service = PptGenerationService()
    result = service.generate_outline({"topic": "T", "pageCount": 5, "sourceContent": "资料"}, None)
    assert len(result["items"]) >= 2
    assert result["generationMode"] == "source_recovery"
    assert any("资料" in point for item in result["items"] for point in item["keyPoints"])


def test_generate_outline_recovers_from_connection_error(monkeypatch):
    from app.ppt_generation import service as svc

    monkeypatch.setattr(svc.time, "sleep", lambda *_args: None)

    def fail_model(*_args, **_kwargs):
        raise ConnectionError("connection reset by peer")

    monkeypatch.setattr(svc, "run_specialist_agent", fail_model)
    result = PptGenerationService().generate_outline(
        {
            "topic": "校园科研平台",
            "pageCount": 5,
            "sourceContent": "学校建设国家重点实验室。\n多个省部级科研平台持续产出成果。",
        },
        None,
    )
    assert len(result["items"]) == 2
    assert result["generationMode"] == "source_recovery"
    assert "模型服务暂时不可用" in result["warnings"][0]


def test_source_outline_keeps_headings_and_source_points():
    items = _source_outline_items(
        "第一章 项目背景\n建设背景与目标\n\n第二章 实施方案\n分阶段推进建设。",
        "项目方案",
        8,
    )
    assert [item["title"] for item in items] == ["项目背景", "实施方案"]
    assert items[0]["keyPoints"] == ["建设背景与目标"]
    assert items[1]["keyPoints"] == ["分阶段推进建设。"]


def test_content_quality_flags_outline_like_page_copy():
    flags = _content_quality_flags(
        {
            "type": "内容页",
            "content": ["明确大学高等数学教学PPT的讨论范围。", "梳理概念之间的基本关系。"],
        },
        {
            "type": "内容页",
            "keyPoints": ["讨论范围", "概念关系"],
        },
    )
    assert "outline_summary_language" in flags


def test_content_quality_does_not_flag_fact_bearing_copy():
    flags = _content_quality_flags(
        {
            "type": "内容页",
            "content": [
                "导数表示函数值随自变量变化的瞬时速率，几何上对应曲线切线的斜率。",
                "当导数为零时，函数可能在该点取得极值，但仍需结合导数符号判断。",
            ],
        },
        {"type": "内容页", "keyPoints": ["导数定义", "极值判断"]},
    )
    assert flags == []


def test_fill_missing_slots_backfills_from_outline(catalog):
    layout = catalog.get_layout("general", "title_intro")
    model = parse_slide_layout(layout)
    ui = {"components": copy.deepcopy(layout["components"])}
    missing = _fill_missing_slots(
        ui,
        model,
        matched_names={"headline_text"},
        slide_item={"title": "栈与队列", "content": ["后进先出", "先进先出"]},
        outline_item={"title": "栈与队列", "keyPoints": ["后进先出"]},
    )
    assert missing  # 存在漏填槽位
    # body_copy 应被回填第一个要点
    from app.ppt_generation.repair_engine import _find_node_by_name

    node = _find_node_by_name(ui, "body_copy", 0)
    assert node is not None
    assert "后进先出" in (node.get("text") or "")


def test_sanitize_partial_match_records_missing_slots(catalog):
    layout = catalog.get_layout("general", "title_intro")
    payload = {"slides": [{
        "type": "content",
        "title": "栈与队列",
        "content": ["后进先出"],
        "componentContent": {"headline_text": "栈与队列"},
    }]}
    normalized = _sanitize_content_payload(
        payload, ["title_intro"], {"title_intro": layout}, 1,
        current_slides=[{"title": "栈与队列", "keyPoints": ["后进先出"]}],
    )
    item = normalized["slides"][0]
    assert item["_missingSlots"]
    assert "ui" in item
    # 漏填的正文槽已被回填（不再是空槽）
    from app.ppt_generation.repair_engine import _find_node_by_name

    body = _find_node_by_name(item["ui"], "body_copy", 0)
    assert body is not None
    assert (body.get("text") or "").strip()


def test_sanitize_does_not_turn_outline_keypoints_into_body_copy(catalog):
    layout = catalog.get_layout("general", "title_intro")
    payload = {"slides": [{
        "type": "content",
        "title": "栈与队列",
        "content": [],
        "componentContent": {"headline_text": "栈与队列"},
    }]}
    normalized = _sanitize_content_payload(
        payload,
        ["title_intro"],
        {"title_intro": layout},
        1,
        current_slides=[{"title": "栈与队列", "keyPoints": ["后进先出"]}],
    )
    from app.ppt_generation.repair_engine import _find_node_by_name

    body = _find_node_by_name(normalized["slides"][0]["ui"], "body_copy", 0)
    assert body is not None
    assert (body.get("text") or "").strip() == ""


def test_schema_roles_match_element_semantics(catalog):
    layout = catalog.get_layout("general", "title_intro")
    model = parse_slide_layout(layout)
    assert model.element("headline_text").role == "title"
    assert model.element("body_copy").role == "body"
    assert model.element("main_visual").role == "image"
    assert model.element("attribution_name").role in {"body", "card", "label"}
