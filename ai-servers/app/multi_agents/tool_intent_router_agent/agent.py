"""Internal tool that narrows the enabled tool catalog before Leader routing."""

import re
from typing import Any, Dict, Iterable, List, Sequence


TOOL_INTENT_ROUTER_TOOL = {
    "name": "tool_intent_router",
    "zhName": "工具意图识别工具",
    "category": "internal_routing",
    "purpose": "提取用户问题关键词，从已启用工具中评分并返回最匹配的候选工具。",
    "trigger": "每次 Leader 路由前自动执行，不由用户或 Leader 直接选择。",
    "outputs": ["intent", "keywords", "candidate_tools"],
}


# 这是注册表索引，不是 Leader 的工具白名单。新增工具时可以在这里补充
# 用户常用说法，工具本身的详细说明仍以工具注册定义为准。
TOOL_KEYWORDS: Dict[str, Sequence[str]] = {
    "recognize_image_tool": ("识别图片", "图片识别", "图片理解", "截图", "ocr", "图片内容", "图表分析", "看图"),
    "generate_image_tool": ("生成图片", "画图", "配图", "插图", "封面", "海报", "图片素材", "文生图"),
    "generate_mind_map_image_tool": ("思维导图", "脑图", "导图图片"),
    "generate_flowchart_image_tool": ("流程图", "算法流程", "步骤流程"),
    "generate_activity_image_tool": ("活动图", "泳道图", "角色任务流程"),
    "generate_architecture_image_tool": ("架构图", "系统架构", "技术架构", "模块依赖"),
    "generate_knowledge_graph_image_tool": ("知识图谱", "实体关系图", "概念关系图"),
    "generate_ppt_image_tool": ("ppt配图", "ppt图片", "课件配图", "页面插图", "ppt封面"),
    "text_to_sql": ("统计", "数量", "多少", "列表", "排名", "sql", "结构化查询"),
    "java_schedule_api": ("课表", "课程安排", "有什么课", "有课", "今日课程", "明天课程", "上课", "任课老师", "老师是谁", "课时", "几节课", "课程时间"),
    "java_activity_api": ("校园活动", "讲座", "比赛", "报名活动", "活动安排"),
    "java_meeting_api": ("会议", "会议列表", "会议状态", "预约会议"),
    "java_canteen_api": ("食堂", "餐厅", "档口", "菜品", "吃什么", "餐饮优惠"),
    "java_facility_api": ("教学楼", "宿舍", "操场", "图书馆", "设施位置", "在哪里", "导航"),
    "java_secondhand_api": ("二手", "旧物", "闲置", "转让", "买卖物品"),
    "generated_export_tools": ("导出", "文件版", "文档版", "下载", "打包", "附件"),
    "markdown_export_tool": ("markdown", "md文件", "markdown文件"),
    "docx_export_tool": ("word", "docx", "word文件"),
    "excel_export_tool": ("excel", "xlsx", "表格文件", "题库表格"),
    "pptx_export_tool": ("pptx", "ppt文件", "幻灯片文件"),
    "content_archive_tool": ("压缩包", "zip", "附件打包"),
    "diagram_source_export_tool": ("mermaid", "图表源码", "图表源文件", "mmd"),
}

_CAPABILITY_QUERY_MARKERS = ("你能做什么", "有哪些功能", "支持什么", "有什么能力", "能不能做")
_WORD_RE = re.compile(r"[a-z0-9_+#.-]+", re.IGNORECASE)


def _normalize(value: Any) -> str:
    return re.sub(r"\s+", "", str(value or "").strip().lower())


def _tool_search_text(tool: Dict[str, Any]) -> str:
    return _normalize(" ".join(
        str(tool.get(key) or "")
        for key in ("name", "zhName", "displayName", "category", "purpose", "trigger")
    ))


class ToolIntentRouterAgent:
    name = "tool_intent_router_agent"
    top_k = 3

    def extract_keywords(self, input_text: str) -> List[str]:
        normalized = _normalize(input_text)
        if not normalized:
            return []
        matched: List[str] = []
        all_keywords = sorted(
            {keyword for values in TOOL_KEYWORDS.values() for keyword in values},
            key=lambda value: len(_normalize(value)),
            reverse=True,
        )
        for keyword in all_keywords:
            normalized_keyword = _normalize(keyword)
            if normalized_keyword and normalized_keyword in normalized:
                matched.append(keyword)
        matched.extend(token for token in _WORD_RE.findall(normalized) if token not in matched)
        return matched[:12]

    def is_capability_query(self, input_text: str) -> bool:
        normalized = _normalize(input_text)
        return any(marker in normalized for marker in _CAPABILITY_QUERY_MARKERS)

    def rank_tools(self, input_text: str, tools: Iterable[Dict[str, Any]], top_k: int | None = None) -> List[Dict[str, Any]]:
        normalized = _normalize(input_text)
        if not normalized:
            return []
        limit = max(1, int(top_k or self.top_k))
        results: List[Dict[str, Any]] = []
        for tool in tools:
            name = str(tool.get("name") or "").strip()
            if not name:
                continue
            keywords = list(TOOL_KEYWORDS.get(name, ()))
            matched = [keyword for keyword in keywords if _normalize(keyword) in normalized]
            # 对没有显式关键词的新工具，使用注册表中的用途和触发条件做低权重匹配。
            if not matched:
                registered_text = _tool_search_text(tool)
                matched = [token for token in _WORD_RE.findall(normalized) if len(token) > 1 and token in registered_text]
            if not matched:
                continue
            score = sum(min(len(_normalize(keyword)), 12) for keyword in matched)
            if name in normalized:
                score += 20
            results.append({
                **tool,
                "matchScore": round(score / 100, 3),
                "matchedKeywords": matched[:8],
            })
        results.sort(key=lambda item: (-float(item.get("matchScore") or 0), str(item.get("name") or "")))
        return results[:limit]

    def select_candidates(self, input_text: str, tools: Iterable[Dict[str, Any]], top_k: int | None = None) -> Dict[str, Any]:
        keywords = self.extract_keywords(input_text)
        available = list(tools)
        if self.is_capability_query(input_text):
            candidates = available
        else:
            candidates = self.rank_tools(input_text, available, top_k=top_k)
        return {
            "intent": "capability_inquiry" if self.is_capability_query(input_text) else (candidates[0].get("category", "") if candidates else "direct_answer"),
            "keywords": keywords,
            "candidateTools": candidates,
            "candidateCount": len(candidates),
            "topK": len(candidates) if self.is_capability_query(input_text) else int(top_k or self.top_k),
        }


tool_intent_router_agent = ToolIntentRouterAgent()
