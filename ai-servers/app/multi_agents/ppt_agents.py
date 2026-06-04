import re
from dataclasses import dataclass
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


@dataclass(frozen=True)
class PptAgent:
    name: str

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        answer = complete_agent_or_raise(self.name, input_text, evidence or [], model_provider=chat_service)
        if self.name == "ppt_outline_agent":
            return normalize_ppt_outline_answer(answer, input_text)
        if self.name == "ppt_layout_agent":
            return normalize_ppt_layout_answer(answer, input_text)
        return answer


ppt_outline_agent = PptAgent("ppt_outline_agent")
ppt_layout_agent = PptAgent("ppt_layout_agent")
ppt_review_agent = PptAgent("ppt_review_agent")
ppt_image_agent = PptAgent("ppt_image_agent")

PPT_AGENTS = {
    agent.name: agent
    for agent in (ppt_outline_agent, ppt_layout_agent, ppt_review_agent, ppt_image_agent)
}


def normalize_ppt_outline_answer(text: str, input_text: str = "") -> str:
    answer = _clean_transport_noise(text or "")
    if _is_valid_ppt_outline(answer):
        return answer.strip()
    normalized = _rewrite_ppt_outline(answer, input_text)
    if not _is_valid_ppt_outline(normalized):
        raise HTTPException(status_code=502, detail="ppt_outline_agent 返回内容不符合约定格式，且自动规范化失败")
    return normalized.strip()


def normalize_ppt_layout_answer(text: str, input_text: str = "") -> str:
    answer = _clean_transport_noise(text or "")
    if _is_valid_ppt_layout(answer):
        return answer.strip()
    normalized = _rewrite_ppt_layout(answer, input_text)
    if not _is_valid_ppt_layout(normalized):
        raise HTTPException(status_code=502, detail="ppt_layout_agent 返回内容不符合约定格式，且自动规范化失败")
    return normalized.strip()


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


def _is_valid_ppt_layout(text: str) -> bool:
    normalized = (text or "").strip()
    if not normalized.startswith("## PPT 布局方案") or "### 布局信息" not in normalized:
        return False
    if any(field in normalized for field in PPT_LAYOUT_FORBIDDEN_FIELDS):
        return False
    if "- 使用场景：" not in normalized or "- 受众：" not in normalized:
        return False
    page_blocks = _split_ppt_pages(normalized)
    return bool(page_blocks) and all(f"- {field}：" in block for _, block in page_blocks for field in PPT_LAYOUT_REQUIRED_FIELDS)


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


def _rewrite_ppt_layout(text: str, input_text: str) -> str:
    meta = _extract_outline_meta(input_text, text)
    outline_pages = _extract_outline_pages_from_input(input_text)
    layout_pages = _split_ppt_pages(text)
    if not layout_pages and outline_pages:
        layout_pages = [(page["page_no"], page["raw"]) for page in outline_pages]
    if not layout_pages:
        layout_pages = [("1", text)]
    lines = [
        "## PPT 布局方案",
        "",
        "### 布局信息",
        f"- 主题：{meta['topic']}",
        f"- 使用场景：{meta['scene_label']}",
        f"- 受众：{meta['audience']}",
        f"- 整体布局策略：{_infer_layout_strategy(meta['scene_label'], meta['audience'])}",
        f"- 视觉风格建议：{_replace_layout_forbidden_terms(_infer_visual_style(meta['scene_label']))}",
        "",
    ]
    total_pages = max(len(layout_pages), len(outline_pages))
    for index, (page_no, block) in enumerate(layout_pages, start=1):
        outline_page = outline_pages[index - 1] if index - 1 < len(outline_pages) else {}
        title = _extract_markdown_field(block, ["页标题"]) or outline_page.get("title") or _extract_page_title(block, page_no)
        page_type = (
            _extract_markdown_field(block, ["页面类型"])
            or outline_page.get("page_type")
            or _infer_page_type(title, outline_page.get("goal", ""), outline_page.get("content", ""), index, total_pages)
        )
        goal = outline_page.get("goal") or _extract_markdown_field(block, ["本页目标", "讲解目标"])
        content = outline_page.get("content") or _extract_markdown_field(block, ["核心内容", "页面内容建议"])
        display = outline_page.get("display") or _extract_markdown_field(block, ["展示建议"])
        assets = outline_page.get("assets") or _extract_markdown_field(block, ["素材建议"])
        layout_structure = _extract_markdown_field(block, ["布局结构", "版式类型"]) or _infer_layout_structure(page_type, display, content)
        info_hierarchy = _extract_markdown_field(block, ["信息层级", "视觉层级"]) or _infer_information_hierarchy(page_type, goal, content)
        region_plan = _extract_region_plan(block) or _infer_region_plan(page_type, content, display)
        visual = _extract_markdown_field(block, ["视觉建议", "留白", "讲解动线"]) or _infer_visual_advice(page_type, display)
        asset_handling = _extract_markdown_field(block, ["素材处理", "图表/图片区"]) or _infer_asset_handling(assets, page_type)
        lines.extend([
            f"### 第{page_no}页",
            f"- 页标题：{_normalize_inline_text(title)}",
            f"- 页面类型：{page_type}",
            f"- 布局结构：{_normalize_inline_text(layout_structure)}",
            f"- 信息层级：{_normalize_inline_text(info_hierarchy)}",
            f"- 区域安排：\n{_format_multiline_bullets(region_plan)}",
            f"- 视觉建议：{_normalize_inline_text(visual)}",
            f"- 素材处理：{_normalize_inline_text(asset_handling)}",
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


def _extract_outline_pages_from_input(text: str) -> List[Dict[str, str]]:
    return [
        {
            "page_no": page_no,
            "raw": block,
            "title": _extract_markdown_field(block, ["页标题"]) or _extract_page_title(block, page_no),
            "page_type": _extract_markdown_field(block, ["页面类型"]),
            "goal": _extract_markdown_field(block, ["本页目标", "讲解目标"]),
            "content": _extract_markdown_field(block, ["核心内容", "页面内容建议"]),
            "display": _extract_markdown_field(block, ["展示建议"]),
            "assets": _extract_markdown_field(block, ["素材建议"]),
        }
        for page_no, block in _split_ppt_pages(text)
    ]


def _infer_layout_strategy(scene_label: str, audience: str) -> str:
    if scene_label == "商务":
        return f"结论前置，重点页使用强对比层级，适合{audience}快速获取关键信息。"
    if scene_label == "学术":
        return f"按概念到应用递进展开，控制单页信息密度，兼顾图示与定义说明，适合{audience}理解。"
    if scene_label == "路演":
        return f"采用强叙事节奏，重点页突出亮点和差异，适合{audience}快速形成印象。"
    if scene_label == "述职":
        return f"按成果、问题、计划递进布局，突出数据与结论，适合{audience}阅读。"
    return f"保持结构清晰和节奏均衡，兼顾阅读效率与视觉稳定性，适合{audience}。"


def _infer_visual_style(scene_label: str) -> str:
    if scene_label == "学术":
        return "配色简洁克制，以蓝灰或中性色为主，图示强调逻辑关系与可读性。"
    if scene_label == "商务":
        return "使用品牌色点缀重点信息，标题与数字形成明显层级，整体专业干净。"
    if scene_label == "路演":
        return "强调大图和高对比标题，核心数字或亮点采用放大处理，增强记忆点。"
    return "保持字体层级清晰、留白充足、图文比例均衡。"


def _infer_layout_structure(page_type: str, display: str, content: str) -> str:
    text = f"{display}\n{content}"
    if page_type == "封面页":
        return "居中大标题封面布局"
    if page_type == "目录页":
        return "纵向目录导航布局"
    if page_type == "对比页" or "对比" in text or "表格" in text:
        return "左右双栏对比布局"
    if page_type == "流程页" or "流程" in text or "步骤" in text:
        return "横向流程或分步卡片布局"
    if page_type == "数据页" or "图表" in text:
        return "上结论下图表布局"
    if page_type == "总结页":
        return "要点总结加结尾提问布局"
    return "标题加主体内容的单页信息布局"


def _infer_information_hierarchy(page_type: str, goal: str, content: str) -> str:
    if page_type == "封面页":
        return "标题最高，副标题次之，辅助信息最弱。"
    if page_type == "目录页":
        return "章节编号和章节标题为主，次级说明弱化。"
    if page_type == "对比页":
        return "先突出对比维度，再展示差异项，结论最后收束。"
    if page_type == "总结页":
        return "结论最高，其次是回顾要点，最后给出延展问题。"
    if "图表" in (content or ""):
        return "先给一句结论，再展示图表主体，最后补充必要注释。"
    return "标题先行，核心概念居中强化，补充说明放在次级层。"


def _extract_region_plan(block: str) -> str:
    fields = []
    display_names = {
        "区域安排": "区域安排",
        "标题区": "顶部区域",
        "正文区": "主体内容区域",
        "图表/图片区": "视觉素材区域",
    }
    for name in ["区域安排", "标题区", "正文区", "图表/图片区"]:
        value = _extract_markdown_field(block, [name])
        if value:
            fields.append(f"{display_names[name]}：{_normalize_inline_text(value)}")
    return "\n".join(fields)


def _infer_region_plan(page_type: str, content: str, display: str) -> str:
    if page_type == "封面页":
        return "\n".join(["标题区：页面上半区居中放置主标题与副标题。", "主视觉区：页面中下部放置一张抽象主题图。", "辅助信息区：底部放课程名称或演讲者信息。"])
    if page_type == "目录页":
        return "\n".join(["标题区：顶部放目录标题。", "导航区：中部纵向排列章节列表或编号导航。", "辅助区：底部可放一句总览说明。"])
    if page_type == "对比页":
        return "\n".join(["标题区：顶部给出对比主题。", "左侧内容区：展示对象 A 的要点或结构。", "右侧内容区：展示对象 B 的要点或结构。", "结论区：底部收束关键差异。"])
    if "图表" in f"{content}\n{display}" or page_type == "数据页":
        return "\n".join(["标题区：顶部给出一句结论型标题。", "图表区：中部放主图表或主示意图。", "注释区：底部补充关键说明和数据口径。"])
    return "\n".join(["标题区：顶部放本页标题。", "正文区：中部放 3 到 5 个要点或卡片内容。", "辅助区：右侧或底部放图示、图标或补充说明。"])


def _infer_visual_advice(page_type: str, display: str) -> str:
    if page_type == "封面页":
        return "标题字号最大，主视觉占据主要版面，整体留白充足，避免堆叠说明文字。"
    if page_type == "目录页":
        return "目录项保持统一缩进和节奏，可用编号或图标建立导航感。"
    if page_type == "对比页":
        return "两侧内容保持同宽同高，使用统一对齐和相同视觉权重，避免一侧过重。"
    if page_type == "总结页":
        return "结论使用高对比强调，次要说明弱化，末尾问题与结论形成视觉收束。"
    return "标题、正文、辅助信息形成三级层级，保证留白和对齐关系稳定。"


def _infer_asset_handling(assets: str, page_type: str) -> str:
    asset_text = _normalize_inline_text(assets)
    if not asset_text or asset_text in {"无特定素材。", "无特定素材", "图标或简洁配图"}:
        if page_type in {"封面页", "内容页", "案例页"}:
            return "可选用简洁示意图或图标辅助说明，避免装饰性素材过多。"
        return "无强制素材要求，优先保证信息结构清晰。"
    return f"优先围绕以下素材组织页面：{asset_text}。素材应服务于信息表达，避免喧宾夺主。"


def _replace_layout_forbidden_terms(text: str) -> str:
    value = text or ""
    for forbidden, replacement in PPT_LAYOUT_FORBIDDEN_REPLACEMENTS.items():
        value = value.replace(forbidden, replacement)
    return value
