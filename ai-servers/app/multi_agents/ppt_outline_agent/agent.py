import json
import re
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


PPT_OUTLINE_REQUIRED_FIELDS = ["页标题", "页面类型", "本页目标", "核心内容", "展示建议", "素材建议"]
PPT_OUTLINE_FORBIDDEN_FIELDS = ["讲解目标", "页面内容建议", "课堂互动建议", "教学目标", "互动建议"]
PPT_LAYOUT_REQUIRED_FIELDS = ["页标题", "页面类型", "布局结构", "信息层级", "区域安排", "视觉建议", "素材处理"]
PPT_LAYOUT_FORBIDDEN_FIELDS = ["版式类型", "标题区", "正文区", "图表/图片区", "视觉层级", "留白", "讲解动线"]
PPT_LAYOUT_FORBIDDEN_REPLACEMENTS = {
    "版式类型": "布局结构",
    "标题区": "顶部区域",
    "正文区": "主体内容区域",
    "图表/图片区": "视觉素材区域",
    "视觉层级": "信息层级",
    "留白": "空白空间",
    "讲解动线": "阅读顺序",
}
SCENE_TYPE_LABELS = {
    "academic": "学术",
    "business": "商务",
    "roadshow": "路演",
    "report": "述职",
    "teaching": "教学",
}


class PptOutlineAgent:
    name = "ppt_outline_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        answer = complete_agent_or_raise(self.name, input_text, evidence or [], model_provider=chat_service)
        return normalize_ppt_outline_answer(answer, input_text)


ppt_outline_agent = PptOutlineAgent()


def normalize_ppt_outline_answer(text: str, input_text: str = "") -> str:
    answer = _clean_transport_noise(text or "")
    structured = _structured_outline_to_markdown(answer, input_text)
    if structured:
        return structured
    if _is_valid_ppt_outline(answer):
        return answer.strip()
    normalized = _rewrite_ppt_outline(answer, input_text)
    if not _is_valid_ppt_outline(normalized):
        raise HTTPException(status_code=502, detail="ppt_outline_agent 返回内容不符合约定格式，且自动规范化失败")
    return normalized.strip()


def _structured_outline_to_markdown(text: str, input_text: str) -> str:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", cleaned, flags=re.IGNORECASE | re.DOTALL).strip()
    if not cleaned.startswith("{"):
        return ""
    try:
        payload = json.loads(cleaned)
    except json.JSONDecodeError:
        return ""
    slides = payload.get("slides") if isinstance(payload, dict) else None
    if not isinstance(slides, list) or not slides:
        return ""
    title = str(payload.get("title") or _match_labeled_value(input_text, ["topic", "主题"]) or "复习资料 PPT").strip()
    scene = _match_labeled_value(input_text, ["scene_type", "使用场景"]) or "通用"
    audience = _match_labeled_value(input_text, ["audience", "受众"]) or "学生"
    lines = [
        "## PPT 大纲", "", "### 大纲信息",
        f"- 主题：{_normalize_inline_text(title)}",
        f"- 使用场景：{_normalize_inline_text(scene)}",
        f"- 受众：{_normalize_inline_text(audience)}",
        f"- 建议页数：{len(slides)} 页",
        f"- 整体目标：围绕 {_normalize_inline_text(title)} 建立清晰、连贯的知识结构。",
        "- 风格建议：简洁、清晰、适合复习。", "",
    ]
    for position, raw in enumerate(slides, start=1):
        if not isinstance(raw, dict):
            raw = {}
        title_value = _normalize_inline_text(str(raw.get("title") or raw.get("页标题") or f"第 {position} 页"))
        page_type = _normalize_inline_text(str(raw.get("type") or raw.get("页面类型") or ("封面页" if position == 1 else "内容页")))
        objective = _normalize_inline_text(str(raw.get("objective") or raw.get("本页目标") or "明确本页需要掌握的重点。"))
        points = raw.get("keyPoints") or raw.get("content") or raw.get("核心内容") or []
        if isinstance(points, str):
            points = [line.strip(" -*•") for line in points.splitlines() if line.strip(" -*•")]
        if not isinstance(points, list):
            points = [str(points)]
        points = [str(point).strip() for point in points if str(point).strip()][:6] or ["提炼本页需要掌握的核心要点。"]
        lines.extend([
            f"### 第{position}页",
            f"- 页标题：{title_value}",
            f"- 页面类型：{page_type}",
            f"- 本页目标：{objective}",
            "- 核心内容：",
            *[f"  - {_normalize_inline_text(point)}" for point in points],
            f"- 展示建议：{_normalize_inline_text(str(raw.get('displaySuggestion') or raw.get('展示建议') or '突出本页核心信息，控制文字密度。'))}",
            f"- 素材建议：{_normalize_inline_text(str(raw.get('assetSuggestion') or raw.get('素材建议') or '按内容需要使用模板组件。'))}",
            "",
        ])
    result = "\n".join(lines).strip()
    return result if _is_valid_ppt_outline(result) else ""

def _clean_transport_noise(text: str) -> str:
    cleaned_lines = []
    for line in (text or "").splitlines():
        if "Connection #" in line and "left intact" in line:
            continue
        cleaned_lines.append(line.rstrip())
    return "\n".join(cleaned_lines).strip()

def _is_valid_ppt_outline(text: str) -> bool:
    normalized = (text or "").strip()
    if not normalized.startswith("## PPT 大纲") or "### 大纲信息" not in normalized:
        return False
    if any(field in normalized for field in PPT_OUTLINE_FORBIDDEN_FIELDS):
        return False
    if "- 使用场景：" not in normalized or "- 受众：" not in normalized:
        return False
    page_blocks = _split_ppt_pages(normalized)
    return bool(page_blocks) and all(f"- {field}：" in block for _, block in page_blocks for field in PPT_OUTLINE_REQUIRED_FIELDS)

def _rewrite_ppt_outline(text: str, input_text: str) -> str:
    meta = _extract_outline_meta(input_text, text)
    page_blocks = _split_ppt_pages(text) or [("1", text)]
    lines = ["## PPT 大纲", ""]
    if "未检索到外部证据" in text:
        lines.extend(["**提示：** 未检索到外部证据，以下大纲基于通用信息生成。", ""])
    lines.extend([
        "### 大纲信息",
        f"- 主题：{meta['topic']}",
        f"- 使用场景：{meta['scene_label']}",
        f"- 受众：{meta['audience']}",
        f"- 建议页数：{meta['slide_count']} 页",
        f"- 整体目标：{meta['overall_goal']}",
        f"- 风格建议：{meta['style']}",
        "",
    ])
    total_pages = len(page_blocks)
    for index, (page_no, block) in enumerate(page_blocks, start=1):
        title = _extract_page_title(block, page_no)
        goal = _extract_markdown_field(block, ["本页目标", "讲解目标", "教学目标"]) or "概括本页希望传达的关键信息。"
        core_content = _extract_markdown_field(block, ["核心内容", "页面内容建议"]) or "提炼本页的核心要点并控制信息密度。"
        page_type = _infer_page_type(title, goal, core_content, index, total_pages)
        lines.extend([
            f"### 第{page_no}页",
            f"- 页标题：{title}",
            f"- 页面类型：{page_type}",
            f"- 本页目标：{_normalize_inline_text(goal)}",
            f"- 核心内容：\n{_format_multiline_bullets(core_content)}",
            f"- 展示建议：{_infer_display_suggestion(title, core_content, page_type)}",
            f"- 素材建议：{_infer_asset_suggestion(core_content, page_type)}",
            "",
        ])
    return "\n".join(lines).strip()

def _extract_outline_meta(input_text: str, answer_text: str) -> Dict[str, str]:
    raw = input_text or ""
    topic = _match_labeled_value(raw, ["topic", "主题"]) or _extract_first_page_title(answer_text) or "未提供主题"
    scene_type = (_match_labeled_value(raw, ["scene_type", "使用场景"]) or "").strip().lower()
    audience = _match_labeled_value(raw, ["audience", "受众"]) or "未明确"
    slide_count = _match_labeled_value(raw, ["slide_count", "页数"]) or str(max(len(_split_ppt_pages(answer_text)), 1))
    scene_label = SCENE_TYPE_LABELS.get(scene_type, scene_type or "通用")
    return {
        "topic": topic,
        "scene_label": scene_label,
        "audience": audience,
        "slide_count": re.sub(r"[^\d]", "", slide_count) or slide_count,
        "overall_goal": _infer_overall_goal(topic, scene_label, audience),
        "style": _infer_style(scene_label, audience),
    }

def _match_labeled_value(text: str, labels: List[str]) -> str:
    for label in labels:
        match = re.search(rf"{re.escape(label)}\s*[:：]\s*([^;\n]+)", text or "", flags=re.IGNORECASE)
        if match:
            return match.group(1).strip()
    return ""

def _extract_first_page_title(text: str) -> str:
    pages = _split_ppt_pages(text)
    return _extract_page_title(pages[0][1], pages[0][0]) if pages else ""

def _split_ppt_pages(text: str) -> List[tuple[str, str]]:
    matches = list(re.finditer(r"###\s*第\s*(\d+)\s*页(?:[:：]\s*([^\n]+))?", text or "", flags=re.IGNORECASE))
    pages: List[tuple[str, str]] = []
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text or "")
        pages.append((match.group(1), (text or "")[start:end].strip()))
    return pages

def _extract_page_title(block: str, page_no: str) -> str:
    title = _extract_markdown_field(block, ["页标题"])
    if title:
        return _normalize_inline_text(title)
    heading_match = re.search(rf"###\s*第\s*{re.escape(page_no)}\s*页[:：]?\s*(.+)", block or "")
    if heading_match and heading_match.group(1).strip():
        return _normalize_inline_text(heading_match.group(1))
    return f"第{page_no}页内容"

def _extract_markdown_field(block: str, names: List[str]) -> str:
    for name in names:
        patterns = [
            rf"-\s*\*\*{re.escape(name)}[:：]\*\*\s*(.*?)(?=\n-\s*\*\*|\n###|\Z)",
            rf"-\s*{re.escape(name)}[:：]\s*(.*?)(?=\n-\s*[^\s]|\n###|\Z)",
        ]
        for pattern in patterns:
            match = re.search(pattern, block or "", flags=re.DOTALL)
            if match:
                return match.group(1).strip()
    return ""

def _normalize_inline_text(text: str) -> str:
    return _replace_layout_forbidden_terms(
        re.sub(r"\s+", " ", (text or "").strip()).replace(" ：", "：")
    )

def _format_multiline_bullets(text: str) -> str:
    raw = (text or "").strip()
    if not raw:
        return "  - 提炼本页需要展示的核心要点。"
    items = []
    for line in raw.splitlines():
        stripped = re.sub(r"^[-*]\s*", "", line.strip())
        if stripped:
            items.append(f"  - {_replace_layout_forbidden_terms(stripped)}")
    return "\n".join(items or [f"  - {_normalize_inline_text(raw)}"])

def _infer_page_type(title: str, goal: str, content: str, page_index: int, total_pages: int) -> str:
    text = f"{title}\n{goal}\n{content}"
    if page_index == 1 or "封面" in title:
        return "封面页"
    if "目录" in text:
        return "目录页"
    if "总结" in text or "回顾" in text or page_index == total_pages:
        return "总结页"
    if "对比" in text or "vs" in text.lower():
        return "对比页"
    if "流程" in text or "步骤" in text:
        return "流程页"
    if "案例" in text or "场景" in text:
        return "案例页"
    if "数据" in text or "图表" in text:
        return "数据页"
    if "过渡" in text:
        return "过渡页"
    return "内容页"

def _infer_display_suggestion(title: str, content: str, page_type: str) -> str:
    text = f"{title}\n{content}"
    if page_type == "封面页":
        return "采用大标题加副标题的封面布局，配合单张主视觉，文字控制在 3 行以内。"
    if "表格" in text or page_type == "对比页":
        return "使用对比表或双栏布局，突出差异点，避免段落堆砌。"
    if "流程图" in text or "步骤" in text or page_type == "流程页":
        return "使用流程图或步骤卡片展示顺序关系，每步保留一句核心说明。"
    if "代码" in text or "伪代码" in text:
        return "采用上文下码或左右分栏布局，代码片段只保留关键逻辑。"
    return "采用要点列表或卡片式布局，每页控制在 3 到 5 个核心信息块。"

def _infer_asset_suggestion(content: str, page_type: str) -> str:
    suggestions = []
    text = content or ""
    if page_type == "封面页":
        suggestions.append("主视觉插图")
    if "表格" in text:
        suggestions.append("对比表格")
    if "图表" in text or page_type == "数据页":
        suggestions.append("数据图表")
    if "流程图" in text or "步骤" in text:
        suggestions.append("流程图")
    if "代码" in text or "伪代码" in text:
        suggestions.append("代码片段")
    return "、".join(dict.fromkeys(suggestions or ["图标或简洁配图"]))

def _infer_overall_goal(topic: str, scene_label: str, audience: str) -> str:
    return f"围绕“{topic}”建立清晰的叙事顺序，面向{audience}完成一套适用于{scene_label}场景的 PPT 大纲。"

def _infer_style(scene_label: str, audience: str) -> str:
    if scene_label == "商务":
        return f"表达简洁、结论前置、强调价值与行动建议，适配{audience}阅读。"
    if scene_label == "路演":
        return f"突出亮点、差异化和说服力，控制文字密度，适配{audience}快速浏览。"
    if scene_label == "述职":
        return f"强调成果、问题、计划的递进结构，语气专业克制，适配{audience}。"
    if scene_label == "教学":
        return f"概念清晰、层次递进，但仍保持通用 PPT 表达，适配{audience}。"
    return f"结构清晰、表达中性、信息密度适中，适配{audience}。"

def _replace_layout_forbidden_terms(text: str) -> str:
    value = text or ""
    for forbidden, replacement in PPT_LAYOUT_FORBIDDEN_REPLACEMENTS.items():
        value = value.replace(forbidden, replacement)
    return value


__all__ = ["ppt_outline_agent", "normalize_ppt_outline_answer"]
