import json
import re
from dataclasses import asdict, dataclass
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.multi_agents.catalog import DIAGRAM_AGENT_SPECS, MEETING_AGENT_SPECS, PPT_AGENT_SPECS, QUESTION_AGENT_SPECS, get_agent_profile, normalize_agent_name
from app.model_providers.factory import get_chat_model_provider
from app.multi_agents.runtime import load_agent_prompt
from app.services.memory_store import memory_store
from app.utils.logger import get_logger
from app.utils.text_utils import is_schedule_intent, is_smalltalk_intent

logger = get_logger("multi_agents.leader")

LEADER_OUTPUT_PUSH_STRATEGIES = [
    {
        "push_type": "image",
        "triggers": ["生成图片", "画一张", "配图", "插图", "封面图", "海报", "图片素材", "架构图", "流程图", "活动图", "思维导图图片"],
        "target_agents": ["image_agent", "diagram_mind_map_agent", "diagram_architecture_agent", "diagram_flowchart_agent", "diagram_activity_agent", "ppt_image_agent"],
        "display_policy": "返回图片 URL 或图片生成 JSON 时，App 会话页以图片卡片展示，支持点击预览。",
    },
    {
        "push_type": "document",
        "triggers": ["导出文档", "生成文档", "文件版", "文档版", "下载文档", "打包下载", "转 Word", "转 DOCX", "题库 Excel", "题库表格", "Mermaid 源文件", "图表源码", "PPT 转 Word", "PPTX 转 DOCX", "PDF", "Word", "Excel", "ZIP"],
        "target_agents": ["textbook_knowledge_agent", "textbook_question_single_choice_agent", "meeting_summary_agent", "ppt_outline_agent", "ppt_to_docx_agent"],
        "display_policy": "知识点、会议纪要、PPT 大纲和题库 JSON 会由 generated_export_tools 自动生成 md/docx/xlsx/zip 附件；Mermaid 图表会额外生成 mmd 源文件；PPTX 转 DOCX 仍由 ppt_to_docx_agent 处理。",
    },
    {
        "push_type": "text",
        "triggers": ["普通问答", "知识解释", "策略说明"],
        "target_agents": ["leader_agent", "textbook_knowledge_agent", "meeting_summary_agent"],
        "display_policy": "默认以文本或 Markdown 展示。",
    },
]


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

    def plan(
        self,
        input_text: str,
        rag_strategy: str = "",
        requested_agent: Optional[str] = None,
        chat_service=None,
        profile_context: Optional[Dict[str, Any]] = None,
        callable_catalog: Optional[Dict[str, Any]] = None,
    ) -> LeaderPlan:
        forced_plan = self._plan_for_requested_agent(requested_agent, rag_strategy)
        if forced_plan:
            return forced_plan

        if self._is_callable_catalog_query(input_text):
            return LeaderPlan(
                intent="leader_callable_catalog",
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="",
                action="direct_answer",
                route_reason="用户询问 Leader 当前可调用的智能体和工具，直接展示后台清单。",
                answer=self._callable_catalog_answer(callable_catalog),
            )

        return self._plan_with_llm(input_text, rag_strategy, chat_service, profile_context=profile_context, callable_catalog=callable_catalog)

    def _plan_with_rules(self, input_text: str, rag_strategy: str = "") -> LeaderPlan:
        normalized = (input_text or "").strip().lower()
        if is_smalltalk_intent(input_text):
            return LeaderPlan(
                intent="smalltalk",
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="",
                action="direct_answer",
                route_reason="命中问候/闲聊意图，Leader 直接回复，不调用工具或本地 RAG。",
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
        if "架构图" in normalized and "提示词" in normalized:
            return LeaderPlan("architecture_diagram_prompt", "architecture_prompt_agent", False, "", route_reason="命中架构图提示词生成意图，分发给图表架构图提示词智能体。")
        if any(token in normalized for token in ("架构图", "系统架构图", "architecture diagram", "architecture")):
            return LeaderPlan("diagram_architecture", "diagram_architecture_agent", False, "", route_reason="命中架构图生成意图，分发给图表架构图智能体。")
        if "活动图" in normalized and "提示词" in normalized:
            return LeaderPlan("diagram_activity_prompt", "diagram_activity_prompt_agent", False, "", route_reason="命中活动图提示词生成意图，分发给活动图提示词智能体。")
        if any(token in normalized for token in ("活动图", "泳道图", "activity diagram", "任务活动图")):
            return LeaderPlan("diagram_activity", "diagram_activity_agent", False, "", route_reason="命中活动图生成意图，分发给图表活动图智能体。")
        if "流程图" in normalized and "提示词" in normalized:
            return LeaderPlan("diagram_flowchart_prompt", "diagram_flowchart_prompt_agent", False, "", route_reason="命中流程图提示词生成意图，分发给流程图提示词智能体。")
        if any(token in normalized for token in ("流程图", "flowchart", "流程")):
            return LeaderPlan("diagram_flowchart", "diagram_flowchart_agent", False, "", route_reason="命中流程图生成意图，分发给图表流程图智能体。")
        if any(token in normalized for token in ("思维导图", "mindmap", "mind map", "脑图")):
            return LeaderPlan("diagram_mind_map", "diagram_mind_map_agent", False, "", route_reason="命中思维导图生成意图，分发给图表思维导图智能体。")
        if any(token in normalized for token in ("生成图片", "画一张", "画张", "配图", "插图", "封面图", "海报", "图片素材", "文生图")):
            return LeaderPlan("image_generation", "image_agent", False, "", route_reason="命中图片生成/配图意图，分发给图片智能体，并在 App 会话页以图片卡片推送。")
        if any(token in normalized for token in ("个人画像汇总", "画像汇总", "画像总结", "画像分析", "画像雷达总结", "profile summary")):
            return LeaderPlan("profile_summary", "profile_summary_agent", False, "", route_reason="命中个人画像汇总意图，分发给个人画像汇总智能体。")
        if any(token in normalized for token in ("多选题", "多项选择")):
            return LeaderPlan("multiple_choice", "textbook_question_multiple_choice_agent", False, "", route_reason="命中多选题生成意图，分发给多选题智能体。")
        if any(token in normalized for token in ("选择题", "单选题", "单项选择")):
            return LeaderPlan("single_choice", "textbook_question_single_choice_agent", False, "", route_reason="命中选择题生成意图，分发给选择题智能体。")
        if any(token in normalized for token in ("填空题", "填空")):
            return LeaderPlan("fill_blank", "textbook_question_fill_blank_agent", False, "", route_reason="命中填空题生成意图，分发给填空题智能体。")
        if any(token in normalized for token in ("判断题", "判断")):
            return LeaderPlan("true_false", "textbook_question_true_false_agent", False, "", route_reason="命中判断题生成意图，分发给判断题智能体。")
        if any(token in normalized for token in ("简答题", "问答题")):
            return LeaderPlan("short_answer", "textbook_question_short_answer_agent", False, "", route_reason="命中简答题生成意图，分发给简答题智能体。")
        if any(token in normalized for token in ("计算题", "计算")):
            return LeaderPlan("calculation", "textbook_question_calculation_agent", False, "", route_reason="命中计算题生成意图，分发给计算题智能体。")
        if any(token in normalized for token in ("编程题", "程序题", "代码题")):
            return LeaderPlan("programming", "textbook_question_programming_agent", False, "", route_reason="命中编程题生成意图，分发给编程题智能体。")
        if any(token in normalized for token in ("题库", "练习题", "试卷", "出题")):
            return LeaderPlan("single_choice", "textbook_question_single_choice_agent", False, "", route_reason="命中出题意图但未指定题型，默认分发给选择题智能体。")
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
            return LeaderPlan("ppt_review", "ppt_review_agent", False, "", route_reason="命中 PPT 审查/评分意图，分发给 PPT 审查智能体。")
        if any(token in normalized for token in ("ppt布局", "ppt排版", "ppt版式", "排布局", "排版")):
            return LeaderPlan("ppt_layout", "ppt_layout_agent", False, "", route_reason="命中 PPT 布局/排版意图，分发给 PPT 布局智能体。")
        if any(token in normalized for token in ("ppt图片", "ppt配图", "ppt插图", "ppt封面", "课件配图")):
            return LeaderPlan("ppt_image", "ppt_image_agent", False, "", route_reason="命中 PPT 图片/配图意图，分发给 PPT 图片智能体。")
        if any(token in normalized for token in ("ppt", "幻灯片", "课件", "演示文稿")):
            return LeaderPlan("ppt_outline", "ppt_outline_agent", False, "", route_reason="命中 PPT/课件生成意图，默认分发给 PPT 大纲智能体。")
        if any(token in normalized for token in ("md", "markdown", "知识点提取", "提取知识点", "知识点整理")):
            return LeaderPlan("textbook_knowledge", "textbook_knowledge_agent", False, "", route_reason="命中 Markdown/文本教材知识点整理意图，统一交给教材知识点智能体。")
        if any(token in normalized for token in ("教材", "课本", "章节", "考点", "知识点", "课程内容")):
            return LeaderPlan("textbook_knowledge", "textbook_knowledge_agent", False, "", route_reason="命中教材/课程知识意图，使用教材知识点智能体直接整理。")
        return LeaderPlan("campus_search", "textbook_knowledge_agent", False, "", route_reason="未命中特定生成类意图，按校园知识查询处理。")

    def _plan_with_llm(
        self,
        input_text: str,
        rag_strategy: str,
        chat_service=None,
        profile_context: Optional[Dict[str, Any]] = None,
        callable_catalog: Optional[Dict[str, Any]] = None,
    ) -> LeaderPlan:
        provider = chat_service or get_chat_model_provider()
        text = provider.complete(
            system_prompt=load_agent_prompt(self.name),
            user_prompt=build_leader_router_user_prompt(
                input_text,
                rag_strategy,
                profile_context=profile_context,
                callable_catalog=callable_catalog,
            ),
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
            "profile_summary_agent",
            "architecture_prompt_agent",
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
        if action == "call_tool" and tool_name == "text_to_sql" and not rag_strategy:
            rag_strategy = "text_to_sql"
        elif action != "call_tool":
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
            rag_strategy="",
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
        return "你好，我是 Leader 智能体。我会先判断你的意图，再决定直接回答、调用专业智能体，或走 Text-to-SQL 或 Java 后端接口。"

    def _is_callable_catalog_query(self, input_text: str) -> bool:
        normalized = (input_text or "").strip().lower()
        if not normalized:
            return False
        action_tokens = ("能调用", "会调用", "调用哪些", "调用什么", "有哪些", "有什么", "清单", "列表", "能力")
        target_tokens = ("智能体", "agent", "工具", "tool", "功能", "能力")
        return any(token in normalized for token in action_tokens) and any(token in normalized for token in target_tokens)

    def _callable_catalog_answer(self, callable_catalog: Optional[Dict[str, Any]]) -> str:
        catalog = callable_catalog if isinstance(callable_catalog, dict) else {}
        agents = [item for item in catalog.get("agents", []) if isinstance(item, dict)]
        tools = [item for item in catalog.get("tools", []) if isinstance(item, dict)]
        content_tools = [item for item in catalog.get("contentTools", []) if isinstance(item, dict)]
        if not agents and not tools:
            return "当前还没有拿到后台可调用清单。请先确认 AI Server 的智能体目录和工具目录是否正常返回。"

        lines = ["我当前会按后台启用状态调用这些能力：", ""]
        enabled_agents = [item for item in agents if item.get("enabled") is not False]
        disabled_agents = [item for item in agents if item.get("enabled") is False]
        grouped_agents: Dict[str, List[Dict[str, Any]]] = {}
        for item in enabled_agents:
            grouped_agents.setdefault(str(item.get("category") or "other"), []).append(item)
        lines.append("可调用智能体：")
        for category, items in grouped_agents.items():
            names = "、".join(f"{item.get('role') or item.get('name')}（{item.get('name')}）" for item in items[:8])
            overflow = f" 等 {len(items)} 个" if len(items) > 8 else ""
            lines.append(f"- {self._category_label(category)}：{names}{overflow}")
        if disabled_agents:
            lines.append(f"- 已关闭：{len(disabled_agents)} 个，Leader 识别到也不会继续执行。")

        lines.append("")
        lines.append("Leader 可直接调用的工具：")
        for item in tools:
            status = "可用" if item.get("enabled") is not False else "已关闭"
            lines.append(f"- {self._tool_display_name(item)}（{status}）：{item.get('purpose') or ''}")

        if content_tools:
            enabled_content_tools = [item for item in content_tools if item.get("enabled") is not False]
            disabled_content_tools = [item for item in content_tools if item.get("enabled") is False]
            lines.append("")
            lines.append("内容整理子工具：")
            if enabled_content_tools:
                lines.append("- 可用：" + "、".join(self._tool_display_name(item) for item in enabled_content_tools))
            if disabled_content_tools:
                lines.append("- 已关闭：" + "、".join(self._tool_display_name(item) for item in disabled_content_tools))

        lines.append("")
        lines.append("规则：我只能调用清单里开启的项；关闭的智能体或工具不会被兜底调用。")
        return "\n".join(lines).strip()

    def _category_label(self, category: str) -> str:
        labels = {
            "profile": "画像",
            "diagram": "图表",
            "image": "图片",
            "textbook": "教材知识",
            "question_bank": "题库",
            "meeting": "会议",
            "ppt": "PPT",
            "other": "其他",
        }
        return labels.get(category or "", category or "其他")

    def _tool_display_name(self, tool: Dict[str, Any]) -> str:
        name = str(tool.get("name") or "").strip()
        display_name = str(tool.get("displayName") or "").strip()
        if display_name:
            return display_name
        zh_name = str(tool.get("zhName") or "").strip()
        return f"{zh_name}（{name}）" if zh_name and name else (zh_name or name)

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
        return "text_to_sql" if rag_strategy == "text_to_sql" else ""


leader_agent = LeaderAgent()


def build_leader_router_user_prompt(
    input_text: str,
    rag_strategy: str,
    profile_context: Optional[Dict[str, Any]] = None,
    callable_catalog: Optional[Dict[str, Any]] = None,
) -> str:
    return json.dumps({
        "user_input": input_text or "",
        "requested_rag_strategy": rag_strategy or "",
        "allowed_rag_strategy_when_needed": rag_strategy or "",
        "profile_snapshot": profile_context or {},
        "leader_callable_catalog": callable_catalog or {},
        "profile_usage_policy": [
            "必须参考 profile_snapshot，但用户当前问题优先级最高。",
            "高置信度画像可以用于推荐顺序、解释深度和资源形式。",
            "中低置信度画像只能作为倾向，不能武断判断用户能力。",
            "行为证据实时沉淀，雷达图分数由 Java 后端定时汇总更新。",
            "Leader 不能直接更新画像分数；发现明确证据或冲突时只在 route_reason 中说明，由 Java 按 campus-profile-evidence-v1 记录候选证据。",
            "当前输入与画像冲突时，以当前输入完成本轮回答，并在 route_reason 中说明冲突倾向。",
            "如果 profile_snapshot.outputPreferenceHints 显示用户稳定偏好文件/图片等输出形式，同类任务默认参考该偏好，并在结尾提示可补另一种形式。",
            "如果任务既可做图片又可做文件且没有稳定偏好，先询问用户要图片形式还是文件形式。",
            "用户要求文件版/文档版/Excel/Word/打包下载时，优先路由到能生成知识、题库、会议纪要或 PPT 大纲的专业智能体；AI Server 会自动调用 generated_export_tools 生成附件，不要把长内容只当纯文字回复。",
            "用户要求题库表格或题库 Excel 时，仍先选择对应题型智能体生成严格题库 JSON，再由导出工具转换为 md/docx/xlsx/zip。",
            "用户要求 Mermaid 源文件、图表源码或后续编辑图表时，图表智能体返回 Mermaid 后会自动生成 mmd/md/zip 附件。",
            "如果用户已经提供了要导出的 Markdown、普通文本或标准题库 JSON，且只要求转成文件，可以直接 call_tool: generated_export_tools。",
            "路由时只能选择 leader_callable_catalog 中 enabled=true 的 agents/tools；关闭项只可在 route_reason 中说明，不允许绕过后台配置。",
            "target_agent 必须来自 leader_callable_catalog.agents.name；tool_name 必须来自 leader_callable_catalog.tools.name。",
        ],
        "leader_output_push_strategies": LEADER_OUTPUT_PUSH_STRATEGIES,
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
