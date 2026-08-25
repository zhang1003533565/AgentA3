"""Internal tool that narrows the enabled tool catalog before Leader routing."""

import re
import json
from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


TOOL_INTENT_ROUTER_TOOL = {
    "name": "tool_intent_router",
    "zhName": "工具意图识别工具",
    "category": "internal_routing",
    "purpose": "只提取用户问题中的意图、关键词、实体、约束条件和查询变体，不回答问题、不选择工具、不执行任务。",
    "trigger": "每次 Leader 路由前自动执行，不由用户或 Leader 直接选择。",
    "outputs": ["intent", "keywords", "entities", "constraints", "query_variants"],
}


_CAPABILITY_QUERY_MARKERS = (
    "你能做什么",
    "有哪些功能",
    "有哪些工具",
    "工具列表",
    "当前可用",
    "系统能力",
    "能提供什么",
    "工具能力",
    "支持什么",
    "有什么能力",
    "能不能做",
)
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
    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        """用于后台模型连通性测试；生产路由仍由内部工具统一裁剪已启用工具。"""
        return complete_agent_or_raise(
            self.name,
            input_text,
            evidence or [],
            model_provider=chat_service,
        )

    def parse_model_result(self, value: str) -> Dict[str, Any] | None:
        try:
            payload = json.loads(str(value or "").strip())
        except json.JSONDecodeError:
            return None
        if not isinstance(payload, dict):
            return None
        keywords = payload.get("keywords") if isinstance(payload.get("keywords"), list) else []
        entities = payload.get("entities") if isinstance(payload.get("entities"), dict) else {}
        constraints = payload.get("constraints") if isinstance(payload.get("constraints"), list) else []
        variants = payload.get("queryVariants", payload.get("query_variants"))
        variants = variants if isinstance(variants, list) else []
        return {
            "intent": str(payload.get("intent") or "unknown").strip(),
            "keywords": [str(item).strip() for item in keywords if str(item).strip()][:12],
            "entities": entities,
            "constraints": [str(item).strip() for item in constraints if str(item).strip()][:8],
            "queryVariants": [str(item).strip() for item in variants if str(item).strip()][:3],
        }

    def extract_keywords(self, input_text: str) -> List[str]:
        normalized = _normalize(input_text)
        if not normalized:
            return []
        matched: List[str] = []
        all_keywords = sorted(_DOMAIN_KEYWORDS, key=lambda value: len(_normalize(value)), reverse=True)
        for keyword in all_keywords:
            normalized_keyword = _normalize(keyword)
            if normalized_keyword and normalized_keyword in normalized:
                matched.append(keyword)
        matched.extend(token for token in _WORD_RE.findall(normalized) if token not in matched)
        return matched[:12]

    def extract(self, input_text: str) -> Dict[str, Any]:
        normalized = _normalize(input_text)
        keywords = self.extract_keywords(input_text)
        entities: Dict[str, Any] = {}
        for label, markers in {
            "时间": ("今天", "明天", "后天", "本周", "下周", "上午", "下午", "晚上"),
            "数量": ("多少", "几节", "几项", "几个", "几张", "几页"),
            "地点": ("哪里", "在哪", "地点", "教室", "教学楼", "宿舍"),
            "格式": ("word", "docx", "excel", "xlsx", "markdown", "ppt", "pptx", "pdf", "zip"),
        }.items():
            values = [marker for marker in markers if _normalize(marker) in normalized]
            if values:
                entities[label] = values
        constraints = [
            marker for marker in ("分别", "只要", "全部", "最近", "详细", "简要", "导出", "下载")
            if _normalize(marker) in normalized
        ]
        core = keywords[:6]
        query_variants = []
        if core:
            query_variants.append(" ".join(core))
            query_variants.append(" ".join(core[:4]))
        if input_text and input_text.strip() not in query_variants:
            query_variants.append(input_text.strip())
        return {
            "intent": "capability_inquiry" if self.is_capability_query(input_text) else (core[0] if core else "unknown"),
            "keywords": keywords,
            "entities": entities,
            "constraints": constraints,
            "queryVariants": query_variants[:3],
        }

    def is_capability_query(self, input_text: str) -> bool:
        normalized = _normalize(input_text)
        return any(marker in normalized for marker in _CAPABILITY_QUERY_MARKERS)

_DOMAIN_KEYWORDS = {
    "课表", "课程安排", "有什么课", "校园活动", "讲座", "比赛", "会议", "食堂", "餐厅", "教学楼", "宿舍",
    "二手", "导出", "下载", "打包", "图片", "识别", "截图", "思维导图", "流程图", "架构图", "知识图谱",
    "统计", "数量", "列表", "排名", "word", "docx", "excel", "xlsx", "markdown", "ppt", "pptx", "pdf", "zip",
}

tool_intent_router_agent = ToolIntentRouterAgent()
