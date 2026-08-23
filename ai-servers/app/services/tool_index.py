"""Runtime index for matching structured user intent to enabled tools."""

import re
from typing import Any, Dict, Iterable, List, Sequence


# 工具检索词注册表。工具的完整说明仍来自工具定义，这里只维护用户常用说法。
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

_WORD_RE = re.compile(r"[a-z0-9_+#.-]+", re.IGNORECASE)


def _normalize(value: Any) -> str:
    return re.sub(r"\s+", "", str(value or "").strip().lower())


def _tool_search_text(tool: Dict[str, Any]) -> str:
    return _normalize(" ".join(str(tool.get(key) or "") for key in (
        "name", "zhName", "displayName", "category", "purpose", "trigger"
    )))


class ToolIndex:
    """Scores only the tools supplied by the caller (normally enabled tools)."""

    def search(
        self,
        input_text: str,
        tools: Iterable[Dict[str, Any]],
        intent_result: Dict[str, Any] | None = None,
        retrieval_profiles: Dict[str, Any] | None = None,
        top_k: int = 3,
    ) -> Dict[str, Any]:
        available = list(tools)
        result = intent_result or {}
        normalized = _normalize(input_text)
        if result.get("intent") == "capability_inquiry":
            # 能力询问不能把完整工具目录重新塞回 Leader。只暴露一个
            # 服务端能力查询工具，由它在执行阶段读取最新的后台开关。
            capability_tool = next(
                (
                    tool
                    for tool in available
                    if str(tool.get("name") or "").strip() == "tool_capability_query"
                ),
                None,
            )
            candidates = [capability_tool] if capability_tool else []
        else:
            ranked: List[Dict[str, Any]] = []
            for tool in available:
                name = str(tool.get("name") or "").strip()
                if not name:
                    continue
                profile = (retrieval_profiles or {}).get(name) or {}
                registered = list(TOOL_KEYWORDS.get(name, ()))
                for field in ("keywords", "aliases", "constraints", "examples"):
                    values = profile.get(field) if isinstance(profile, dict) else []
                    if isinstance(values, list):
                        registered.extend(str(item).strip() for item in values if str(item).strip())
                matched = [item for item in registered if _normalize(item) in normalized]
                if not matched:
                    text = _tool_search_text(tool)
                    matched = [token for token in _WORD_RE.findall(normalized) if len(token) > 1 and token in text]
                if not matched:
                    continue
                score = sum(min(len(_normalize(item)), 12) for item in matched)
                if name.lower() in normalized:
                    score += 20
                ranked.append({
                    **tool,
                    "matchScore": round(score / 100, 3),
                    "matchedKeywords": matched[:8],
                })
            ranked.sort(key=lambda item: (-float(item.get("matchScore") or 0), str(item.get("name") or "")))
            candidates = ranked[:max(1, int(top_k or 3))]
        return {
            **result,
            "candidateTools": candidates,
            "candidateCount": len(candidates),
            "topK": len(candidates) if result.get("intent") == "capability_inquiry" else max(1, int(top_k or 3)),
        }


tool_index = ToolIndex()
