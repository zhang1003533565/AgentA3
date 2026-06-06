import json
import re
from dataclasses import asdict, dataclass
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.multi_agents.catalog import DIAGRAM_AGENT_SPECS, MEETING_AGENT_SPECS, PPT_AGENT_SPECS, QUESTION_AGENT_SPECS, get_agent_profile, normalize_agent_name
from app.model_providers.factory import get_chat_model_provider
from app.multi_agents.runtime import load_agent_prompt
from app.rag.core import RAG_STRATEGY_SPECS
from app.services.memory_store import memory_store
from app.utils.logger import get_logger
from app.utils.text_utils import is_schedule_intent, is_smalltalk_intent

logger = get_logger("multi_agents.leader")


@dataclass
class LeaderPlan:
    intent: str
    target_agent: str
    need_retrieval: bool
    rag_strategy: str
    action: str = "delegate_agent"
    tool_name: str = ""
    route_reason: str = ""
    answer: str = ""

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


class LeaderAgent:
    name = "leader_agent"

    def plan(self, input_text: str, rag_strategy: str = "", requested_agent: Optional[str] = None, chat_service=None) -> LeaderPlan:
        forced_plan = self._plan_for_requested_agent(requested_agent, rag_strategy)
        if forced_plan:
            return forced_plan

        return self._plan_with_llm(input_text, rag_strategy, chat_service)

    def _plan_with_rules(self, input_text: str, rag_strategy: str = "") -> LeaderPlan:
        normalized = (input_text or "").strip().lower()
        if is_smalltalk_intent(input_text):
            return LeaderPlan(
                intent="smalltalk",
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="",
                action="direct_answer",
                route_reason="命中问候/闲聊意图，Leader 直接回复，不需要检索。",
                answer=self._smalltalk_answer(input_text),
            )
        if is_schedule_intent(input_text):
            return LeaderPlan(
                intent="schedule",
                target_agent="textbook_knowledge_agent",
                need_retrieval=False,
                rag_strategy="",
                action="call_tool",
                tool_name="java_schedule_api",
                route_reason="命中课表意图，优先调用 Java 后端课表接口。",
            )
        if self._is_structured_query(normalized):
            return LeaderPlan(
                intent="structured_query",
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="text_to_sql",
                action="call_tool",
                tool_name="text_to_sql",
                route_reason="命中统计/查询结构化数据意图，使用 Text-to-SQL 查询接口。",
            )
        if any(token in normalized for token in ("架构图", "系统架构图", "architecture diagram", "architecture")):
            return LeaderPlan("diagram_architecture", "diagram_architecture_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中架构图生成意图，分发给图表架构图智能体。")
        if any(token in normalized for token in ("活动图", "泳道图", "activity diagram", "任务活动图")):
            return LeaderPlan("diagram_activity", "diagram_activity_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中活动图生成意图，分发给图表活动图智能体。")
        if any(token in normalized for token in ("流程图", "flowchart", "流程")):
            return LeaderPlan("diagram_flowchart", "diagram_flowchart_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中流程图生成意图，分发给图表流程图智能体。")
        if any(token in normalized for token in ("思维导图", "mindmap", "mind map", "脑图")):
            return LeaderPlan("diagram_mind_map", "diagram_mind_map_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中思维导图生成意图，分发给图表思维导图智能体。")
        if any(token in normalized for token in ("多选题", "多项选择")):
            return LeaderPlan("multiple_choice", "textbook_question_multiple_choice_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中多选题生成意图，分发给多选题智能体。")
        if any(token in normalized for token in ("选择题", "单选题", "单项选择")):
            return LeaderPlan("single_choice", "textbook_question_single_choice_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中选择题生成意图，分发给选择题智能体。")
        if any(token in normalized for token in ("填空题", "填空")):
            return LeaderPlan("fill_blank", "textbook_question_fill_blank_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中填空题生成意图，分发给填空题智能体。")
        if any(token in normalized for token in ("判断题", "判断")):
            return LeaderPlan("true_false", "textbook_question_true_false_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中判断题生成意图，分发给判断题智能体。")
        if any(token in normalized for token in ("简答题", "问答题")):
            return LeaderPlan("short_answer", "textbook_question_short_answer_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中简答题生成意图，分发给简答题智能体。")
        if any(token in normalized for token in ("计算题", "计算")):
            return LeaderPlan("calculation", "textbook_question_calculation_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中计算题生成意图，分发给计算题智能体。")
        if any(token in normalized for token in ("编程题", "程序题", "代码题")):
            return LeaderPlan("programming", "textbook_question_programming_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中编程题生成意图，分发给编程题智能体。")
        if any(token in normalized for token in ("题库", "练习题", "试卷", "出题")):
            return LeaderPlan("single_choice", "textbook_question_single_choice_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中出题意图但未指定题型，默认分发给选择题智能体。")
        if any(token in normalized for token in ("会议总控", "会议状态", "会议调度", "流程调度")):
            return LeaderPlan("meeting_control", "meeting_controller_agent", False, "", route_reason="命中会议总控/调度意图，分发给会议总控智能体。")
        if any(token in normalized for token in ("语音转写", "会议转写", "说话人", "发言文本")):
            return LeaderPlan("meeting_transcription", "meeting_transcription_agent", False, "", route_reason="命中会议转写意图，分发给语音转写智能体。")
        if any(token in normalized for token in ("会议总结", "会议纪要", "会议摘要", "主要结论")):
            return LeaderPlan("meeting_summary", "meeting_summary_agent", False, "", route_reason="命中会议总结意图，分发给会议总结智能体。")
        if any(token in normalized for token in ("成员分析", "参与特征", "理解偏差", "薄弱点")):
            return LeaderPlan("meeting_member_analysis", "meeting_member_analysis_agent", False, "", route_reason="命中成员分析意图，分发给成员分析智能体。")
        if any(token in normalized for token in ("资源推荐", "学习资源", "推送策略")):
            return LeaderPlan("meeting_resource_recommendation", "meeting_resource_recommendation_agent", False, "", route_reason="命中会议资源推荐意图，分发给资源推荐智能体。")
        if any(token in normalized for token in ("语音播报", "播报脚本", "tts")):
            return LeaderPlan("meeting_voice_broadcast", "meeting_voice_broadcast_agent", False, "", route_reason="命中语音播报意图，分发给语音播报智能体。")
        if "会议" in normalized:
            return LeaderPlan("meeting_summary", "meeting_summary_agent", False, "", route_reason="命中会议处理意图但未指定子任务，默认分发给会议总结智能体。")
        if any(token in normalized for token in ("ppt转docx", "pptx转docx", "ppt转word", "pptx转word", "ppt 转 docx", "pptx 转 docx", "ppt 转 word", "pptx 转 word", "演示文稿转word", "幻灯片转word")):
            return LeaderPlan("ppt_to_docx", "ppt_to_docx_agent", False, "", route_reason="命中 PPTX 转 DOCX/Word 意图，分发给 PPT 转 DOCX 智能体。")
        if any(token in normalized for token in ("ppt审查", "ppt评分", "置信度", "审查ppt", "检查ppt")):
            return LeaderPlan("ppt_review", "ppt_review_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中 PPT 审查/评分意图，分发给 PPT 审查智能体。")
        if any(token in normalized for token in ("ppt布局", "ppt排版", "ppt版式", "排布局", "排版")):
            return LeaderPlan("ppt_layout", "ppt_layout_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中 PPT 布局/排版意图，分发给 PPT 布局智能体。")
        if any(token in normalized for token in ("ppt图片", "ppt配图", "ppt插图", "ppt封面", "课件配图")):
            return LeaderPlan("ppt_image", "ppt_image_agent", True, rag_strategy or "multimodal_rag", route_reason="命中 PPT 图片/配图意图，分发给 PPT 图片智能体。")
        if any(token in normalized for token in ("ppt", "幻灯片", "课件", "演示文稿")):
            return LeaderPlan("ppt_outline", "ppt_outline_agent", True, rag_strategy or "multi_agent_rag", route_reason="命中 PPT/课件生成意图，默认分发给 PPT 大纲智能体。")
        if any(token in normalized for token in ("md", "markdown", "知识点提取", "提取知识点", "知识点整理")):
            return LeaderPlan("textbook_knowledge", "textbook_knowledge_agent", True, rag_strategy or "hybrid_search", route_reason="命中 Markdown/文本教材知识点整理意图，统一交给教材知识点智能体。")
        if any(token in normalized for token in ("教材", "课本", "章节", "考点", "知识点", "课程内容")):
            return LeaderPlan("textbook_knowledge", "textbook_knowledge_agent", True, rag_strategy or "hybrid_search", route_reason="命中教材/课程知识意图，使用教材知识点智能体检索增强。")
        return LeaderPlan("campus_search", "textbook_knowledge_agent", True, rag_strategy or "naive_rag", route_reason="未命中特定生成类意图，按校园知识查询处理。")

    def _plan_with_llm(self, input_text: str, rag_strategy: str, chat_service=None) -> LeaderPlan:
        provider = chat_service or get_chat_model_provider()
        text = provider.complete(
            system_prompt=load_agent_prompt(self.name),
            user_prompt=build_leader_router_user_prompt(input_text, rag_strategy),
        )
        plan = parse_json_object(text)
        if not plan:
            raise HTTPException(status_code=502, detail=f"Leader LLM 路由结果不是合法 JSON：{text[:300]}")
        parsed = self._parse_llm_plan(plan, rag_strategy)
        if not parsed:
            raise HTTPException(status_code=502, detail=f"Leader LLM 路由结果字段不合法：{plan}")
        logger.info(
            "leader llm plan intent=%s action=%s target=%s retrieval=%s",
            parsed.intent,
            parsed.action,
            parsed.target_agent,
            parsed.need_retrieval,
        )
        return parsed

    def _parse_llm_plan(self, plan: Dict[str, Any], requested_rag_strategy: str) -> Optional[LeaderPlan]:
        if not isinstance(plan, dict):
            return None
        action = str(plan.get("action") or "delegate_agent").strip()
        tool_name = str(plan.get("tool_name") or plan.get("toolName") or "").strip()
        default_target = "leader_agent" if action in {"direct_answer", "call_tool"} else "textbook_knowledge_agent"
        target_agent = normalize_agent_name(str(plan.get("target_agent") or plan.get("targetAgent") or "")) or default_target
        if target_agent not in {
            "leader_agent",
            *DIAGRAM_AGENT_SPECS.keys(),
            "mind_map_agent",
            "textbook_knowledge_agent",
            *QUESTION_AGENT_SPECS.keys(),
            *MEETING_AGENT_SPECS.keys(),
            *PPT_AGENT_SPECS.keys(),
            "image_agent",
        }:
            return None
        need_retrieval = bool(plan.get("need_retrieval", plan.get("needRetrieval", False)))
        profile = get_agent_profile(target_agent)
        if profile and action == "delegate_agent":
            need_retrieval = bool(profile["needRetrieval"])
        rag_strategy = self._normalize_rag_strategy(plan.get("rag_strategy") or plan.get("ragStrategy"))
        if requested_rag_strategy and need_retrieval:
            rag_strategy = requested_rag_strategy
        elif profile and need_retrieval and not rag_strategy:
            rag_strategy = profile["defaultRagStrategy"]
        elif action == "call_tool" and tool_name == "text_to_sql" and not rag_strategy:
            rag_strategy = "text_to_sql"
        elif not need_retrieval and action != "call_tool":
            rag_strategy = ""
        return LeaderPlan(
            intent=str(plan.get("intent") or "campus_search").strip() or "campus_search",
            target_agent=target_agent,
            need_retrieval=need_retrieval,
            rag_strategy=rag_strategy,
            action=action if action in {"direct_answer", "delegate_agent", "call_tool"} else "delegate_agent",
            tool_name=tool_name,
            route_reason=str(plan.get("route_reason") or plan.get("routeReason") or "Leader LLM 完成意图识别。").strip(),
            answer=str(plan.get("answer") or "").strip(),
        )

    def _plan_for_requested_agent(self, requested_agent: Optional[str], rag_strategy: str) -> Optional[LeaderPlan]:
        agent_name = normalize_agent_name(requested_agent)
        if not agent_name:
            return None
        profile = get_agent_profile(agent_name)
        if not profile or agent_name == "leader_agent":
            return None
        return LeaderPlan(
            intent=profile["intent"],
            target_agent=agent_name,
            need_retrieval=bool(profile["needRetrieval"]),
            rag_strategy=(rag_strategy or profile["defaultRagStrategy"]) if profile["needRetrieval"] else "",
            route_reason=f"用户显式选择 {profile['role']}，Leader 按指定智能体执行。",
        )

    def _is_structured_query(self, normalized_text: str) -> bool:
        query_tokens = ("统计", "数量", "多少", "有多少", "列表", "查询", "查一下", "排名")
        domain_tokens = ("优惠券", "优惠", "满减", "食堂", "餐厅", "档口", "菜品", "课程", "课表")
        return any(token in normalized_text for token in query_tokens) and any(token in normalized_text for token in domain_tokens)

    def _smalltalk_answer(self, input_text: str) -> str:
        normalized = (input_text or "").strip()
        if "谢谢" in normalized:
            return "不客气，我可以继续帮你判断任务该交给哪个智能体，或者直接处理课程资料。"
        if "再见" in normalized:
            return "再见，需要继续做思维导图、知识点、题库、PPT 或配图时再叫我就行。"
        return "你好，我是 Leader 智能体。我会先判断你的意图，再决定直接回答、调用专业智能体，或走 RAG / Text-to-SQL / Java 后端接口。"

    def load_memory(self, session_token: str) -> List[Dict[str, str]]:
        return memory_store.get_history(session_token)

    def save_memory(self, session_token: str, user_input: str, assistant_answer: str) -> None:
        memory_store.append(session_token, user_input, assistant_answer)

    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
        chat_service=None,
    ) -> str:
        provider = chat_service or get_chat_model_provider()
        return provider.answer(
            prompt=prompt,
            input_text=input_text,
            history=history,
            search_keyword=search_keyword,
            search_results=search_results,
        ).strip()

    def _normalize_rag_strategy(self, value: Any) -> str:
        rag_strategy = str(value or "").strip()
        if not rag_strategy or rag_strategy in {"按目标智能体默认策略", "默认策略", "目标智能体默认策略"}:
            return ""
        return rag_strategy if rag_strategy in RAG_STRATEGY_SPECS else ""


leader_agent = LeaderAgent()


def build_leader_router_user_prompt(input_text: str, rag_strategy: str) -> str:
    return json.dumps({
        "user_input": input_text or "",
        "requested_rag_strategy": rag_strategy or "",
        "allowed_rag_strategy_when_needed": rag_strategy or "",
    }, ensure_ascii=False)


def parse_json_object(text: str) -> Dict[str, Any]:
    raw = (text or "").strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```(?:json)?", "", raw, flags=re.IGNORECASE).strip()
        raw = re.sub(r"```$", "", raw).strip()
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        match = re.search(r"\{.*\}", raw, flags=re.DOTALL)
        if not match:
            return {}
        try:
            parsed = json.loads(match.group(0))
            return parsed if isinstance(parsed, dict) else {}
        except Exception:
            return {}
