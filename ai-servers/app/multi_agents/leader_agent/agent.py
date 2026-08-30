import json
import os
import re
from dataclasses import asdict, dataclass
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.model_providers.multimodal import extract_image_references
from app.model_providers.factory import get_chat_model_provider
from app.multi_agents.catalog import LEADER_CALLABLE_AGENT_ORDER, get_agent_profile, normalize_agent_name
from app.multi_agents.runtime import load_agent_prompt
from app.services.memory_store import memory_store
from app.utils.logger import get_logger
from app.utils.text_utils import is_all_semester_schedule_query, is_course_count_query, is_course_teacher_query, is_course_time_query, is_schedule_intent, is_semester_schedule_query

logger = get_logger("multi_agents.leader")

LEADER_OUTPUT_PUSH_STRATEGIES = [
    {
        "push_type": "image",
        "triggers": ["生成图片", "画一张", "配图", "插图", "封面图", "海报", "图片素材", "架构图", "流程图", "活动图", "思维导图图片"],
        "target_agents": [],
        "target_tools": [
            "generate_image_tool",
            "generate_mind_map_tool",
            "generate_flowchart_tool",
            "generate_architecture_tool",
        ],
        "display_policy": "图片请求返回图片卡片；思维导图/流程图/架构图返回可编辑结构化图表。",
    },
    {
        "push_type": "document",
        "triggers": ["导出文档", "生成文档", "文件版", "文档版", "下载文档", "打包下载", "转 Word", "转 DOCX", "题库 Excel", "题库表格", "PPT 转 Word", "PPTX 转 DOCX", "PDF", "Word", "Excel", "ZIP"],
        "target_agents": ["textbook_knowledge_agent", "textbook_question_single_choice_agent", "meeting_summary_agent", "ppt_outline_agent", "ppt_to_docx_agent"],
        "display_policy": "知识点、会议纪要、PPT 大纲和题库 JSON 会按后台已启用的格式工具分别生成附件；Mermaid 图表可按 Markdown 文件导出；PPTX 转 DOCX 仍由 ppt_to_docx_agent 处理。",
    },
    {
        "push_type": "text",
        "triggers": ["普通问答", "知识解释", "策略说明"],
        "target_agents": ["leader_agent", "textbook_knowledge_agent", "meeting_summary_agent"],
        "display_policy": "默认以文本或 Markdown 展示。",
    },
]

CAMPUS_SERVICE_TOOL_NAMES = {
    "java_schedule_api",
    "java_activity_api",
    "java_meeting_api",
    "java_canteen_api",
    "java_facility_api",
    "java_secondhand_api",
}

VISUAL_GENERATION_TOOL_NAMES = {
    "generate_image_tool",
}

STRUCTURED_DIAGRAM_TOOL_NAMES = {
    "generate_mind_map_tool",
    "generate_flowchart_tool",
    "generate_architecture_tool",
}

# Set to false/0/off to restore the original model-only routing path immediately.
LEADER_FAST_ROUTE_ENABLED_ENV = "AI_LEADER_FAST_ROUTE_ENABLED"

_FAST_ROUTE_ACTIVITY_TOKENS = (
    "校园活动",
    "活动安排",
    "最近活动",
    "近期活动",
    "有什么活动",
    "有什么讲座",
    "最近讲座",
    "近期讲座",
    "讲座安排",
    "有什么比赛",
    "最近比赛",
    "近期比赛",
    "比赛安排",
    "报名活动",
)
_FAST_ROUTE_MEETING_TOKENS = (
    "我的会议",
    "会议列表",
    "会议状态",
    "预约的会议",
    "已预约会议",
    "预约会议安排",
    "有什么会议",
    "查询会议安排",
    "查看会议安排",
    "今天的会议安排",
    "明天的会议安排",
    "本周会议安排",
)
_FAST_ROUTE_CANTEEN_TOKENS = (
    "食堂",
    "档口",
    "食堂菜单",
    "餐厅菜单",
    "有什么菜",
    "菜品价格",
    "吃什么",
    "餐饮优惠",
    "餐饮信息",
)
_FAST_ROUTE_FOLLOWUP_TOKENS = (
    "刚才",
    "上一个",
    "前一个",
    "那个",
    "这个呢",
    "它呢",
    "那明天",
    "那后天",
    "还有吗",
    "还有哪些",
    "还有什么",
    "除此之外",
    "另外呢",
    "继续",
    "同样",
)
_FAST_ROUTE_MULTI_INTENT_TOKENS = (
    "顺便",
    "同时",
    "并且",
    "以及",
    "再帮我",
    "然后",
)
_EXPLICIT_VISUAL_OUTPUT_TOKENS = (
    "生成图片", "生成一张", "生成张", "画一张", "画张", "画图", "做一张", "来一张", "配图", "插图", "封面图", "海报",
    "图片版", "图解版", "文生图", "思维导图", "脑图", "流程图", "活动图", "泳道图", "架构图",
)
_VISUAL_GENERATION_AGENTS = frozenset({"image_agent"})
_FAST_ROUTE_QUERY_ACTION_TOKENS = (
    "查询",
    "查看",
    "查一下",
    "查查",
    "看看",
    "列出",
    "显示",
    "帮我查",
)
_FAST_ROUTE_QUERY_SCOPE_TOKENS = (
    "今天",
    "今日",
    "明天",
    "后天",
    "本周",
    "这周",
    "下周",
    "本月",
    "最近",
    "近期",
    "当前",
    "本学期",
    "这学期",
    "当前学期",
    "所有学期",
    "全部学期",
)
_FAST_ROUTE_QUESTION_TOKENS = (
    "有什么",
    "有哪些",
    "有没有",
    "有吗",
    "多少",
    "几个",
    "几场",
    "什么时候",
    "几点",
    "哪天",
    "哪个",
    "哪家",
    "推荐",
)
_FAST_ROUTE_META_HARD_TOKENS = (
    "接口",
    "api",
    "sdk",
    "代码",
    "源码",
    "表结构",
    "字段",
    "参数",
    "返回值",
    "状态码",
    "架构",
    "设计原则",
    "技术方案",
    "实现原理",
    "调用链",
    "路由规则",
    "缓存策略",
    "单元测试",
    "测试用例",
    "日志",
    "报错",
    "错误",
    "异常",
    "故障",
    "bug",
    "加载失败",
    "请求失败",
    "无法加载",
    "打不开",
    "没显示",
    "不显示",
)
_FAST_ROUTE_META_PHRASES = (
    "怎么实现",
    "如何实现",
    "怎么开发",
    "如何开发",
    "怎么设计",
    "如何设计",
    "怎么编写",
    "如何编写",
    "怎么接入",
    "如何接入",
    "实现方式",
    "开发方式",
)
_FAST_ROUTE_KNOWLEDGE_INTENT_TOKENS = (
    "介绍",
    "分析",
    "科普",
    "解释",
    "讲解",
    "讲讲",
    "谈谈",
    "概述",
    "讨论",
    "研究",
    "评价",
    "解读",
)
_FAST_ROUTE_KNOWLEDGE_TOPIC_TOKENS = (
    "文化",
    "历史",
    "意义",
    "发展",
    "背景",
    "起源",
    "概念",
    "定义",
    "原理",
    "价值",
    "作用",
    "影响",
)
_FAST_ROUTE_SERVICE_ENTITY_TOKENS = (
    "课表",
    "课程安排",
    "课程列表",
    "校园活动",
    "活动安排",
    "活动",
    "讲座",
    "比赛",
    "会议",
    "食堂",
    "档口",
    "菜单",
    "菜品",
    "餐饮",
)
_MEETING_NON_QUERY_TOKENS = (
    "会议总结",
    "会议纪要",
    "会议摘要",
    "会议转写",
    "语音转写",
    "成员分析",
    "资源推荐",
    "语音播报",
    "播报脚本",
    "会议总控",
    "会议调度",
    "创建会议",
    "发起会议",
    "帮我预约",
)
_ACTIVITY_NON_QUERY_TOKENS = (
    "活动图",
    "泳道图",
    "策划",
    "创建",
    "发布",
    "生成活动",
    "策划活动",
    "创建活动",
    "发布活动",
)
_SCHEDULE_NON_QUERY_TOKENS = (
    "生成课表",
    "制作课表",
    "设计课表",
    "制定课程安排",
    "规划课程安排",
    "设计课程安排",
    "怎么安排",
    "如何安排",
    "课表设计",
)
_CANTEEN_NON_QUERY_TOKENS = (
    "食堂管理",
    "餐厅设计",
    "菜单设计",
    "生成菜单",
    "制定菜单",
)


@dataclass
class LeaderPlan:
    intent: str
    target_agent: str
    need_retrieval: bool
    rag_strategy: str
    action: str = "direct_answer"
    tool_name: str = ""
    route_reason: str = ""
    answer: str = ""
    route_mode: str = ""

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)



_VERBATIM_EXPORT_ACTION_TOKENS = (
    "转成", "转换成", "转换为", "导出为", "保存为",
)
_VERBATIM_TEXT_MARKERS = (
    "这段文字", "这段话", "这段文本", "以下文字", "以下文本", "下面内容", "以下内容", "按原文",
)


class LeaderAgent:
    name = "leader_agent"

    def _plan_verbatim_text_to_file_request(
        self,
        input_text: str,
        callable_catalog: Optional[Dict[str, Any]] = None,
    ) -> Optional[LeaderPlan]:
        """Route verbatim text-to-file requests to the matching format-specific tool.

        Content-organization phrasing such as "整理成" routes to the matching format-specific
        content export tool. PDF input conversions ("把 pdf 转成 word") use Word 内容整理.
        """
        normalized = self._normalize_fast_route_text(input_text)
        if not normalized:
            return None
        if "整理成" in normalized:
            return None
        if not any(token in normalized for token in _VERBATIM_EXPORT_ACTION_TOKENS):
            return None

        if "txt" in normalized or "纯文本" in normalized:
            return self._text_to_file_plan(
                input_text,
                "text_to_txt_tool",
                "用户要求按原文导出 txt/纯文本文件，调用文本转 TXT 工具。",
                callable_catalog,
            )
        has_verbatim_marker = any(token in normalized for token in _VERBATIM_TEXT_MARKERS)
        if not has_verbatim_marker:
            return None
        if any(token in normalized for token in ("md", "markdown")):
            return self._text_to_file_plan(
                input_text,
                "text_to_markdown_tool",
                "用户已提供原文并要求转成 Markdown 文件，调用 Markdown 内容整理。",
                callable_catalog,
            )
        if any(token in normalized for token in ("word", "docx")):
            return self._text_to_file_plan(
                input_text,
                "text_to_docx_tool",
                "用户已提供原文并要求转成 Word 文件，调用 Word 内容整理。",
                callable_catalog,
            )
        return None

    def _resolve_explicit_export_tool(
        self,
        normalized: str,
        callable_catalog: Optional[Dict[str, Any]] = None,
    ) -> Optional[str]:
        if "txt" in normalized or "纯文本" in normalized:
            return "text_to_txt_tool"
        if "markdown" in normalized or "md文件" in normalized or (
            "md" in normalized and "mmd" not in normalized
        ):
            return "text_to_markdown_tool"
        if any(token in normalized for token in ("word", "docx")):
            return "text_to_docx_tool"
        if any(token in normalized for token in ("excel", "xlsx", "表格版")):
            return None
        if any(token in normalized for token in ("ppt", "pptx", "幻灯片")):
            return "ai_ppt_generation_tool"
        if any(token in normalized for token in ("文档版", "文件版")):
            for tool_name in ("text_to_docx_tool", "text_to_markdown_tool", "text_to_txt_tool"):
                if self._catalog_tool_enabled(callable_catalog, tool_name):
                    return tool_name
        return None

    def _text_to_file_plan(
        self,
        input_text: str,
        tool_name: str,
        reason: str,
        callable_catalog: Optional[Dict[str, Any]] = None,
    ) -> Optional[LeaderPlan]:
        if not self._catalog_tool_enabled(callable_catalog, tool_name):
            return None
        return LeaderPlan(
            intent="document_export",
            target_agent="leader_agent",
            need_retrieval=False,
            rag_strategy="",
            action="call_tool",
            tool_name=tool_name,
            route_reason=reason,
            route_mode="rules",
        )

    def plan(
        self,
        input_text: str,
        rag_strategy: str = "",
        requested_agent: Optional[str] = None,
        chat_service=None,
        profile_context: Optional[Dict[str, Any]] = None,
        callable_catalog: Optional[Dict[str, Any]] = None,
        conversation_context: Optional[Dict[str, Any]] = None,
        learning_context: Optional[Dict[str, Any]] = None,
        routing_input_text: Optional[str] = None,
    ) -> LeaderPlan:
        forced_plan = self._plan_for_requested_agent(requested_agent, rag_strategy)
        if forced_plan:
            return forced_plan

        route_text = str(routing_input_text or input_text or "")
        file_export_plan = self._plan_explicit_file_export_request(route_text, callable_catalog)
        if file_export_plan:
            return file_export_plan

        tool_selection = callable_catalog.get("toolSelection") if isinstance(callable_catalog, dict) else {}
        if (
            isinstance(tool_selection, dict)
            and tool_selection.get("intent") == "capability_inquiry"
        ):
            return LeaderPlan(
                intent="capability_inquiry",
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="",
                action="call_tool",
                tool_name="tool_capability_query",
                route_reason="用户询问系统能力，调用能力查询工具读取当前已启用工具。",
                answer="正在查询当前已启用的工具能力。",
                route_mode="rules",
            )

        if self._is_explicit_visual_generation_request(input_text):
            visual_plan = self._plan_with_rules(input_text, rag_strategy)
            if visual_plan.action == "call_tool" and visual_plan.tool_name in VISUAL_GENERATION_TOOL_NAMES:
                return visual_plan

        fast_plan = self._plan_high_confidence_service_query(
            input_text,
            rag_strategy=rag_strategy,
            callable_catalog=callable_catalog,
            conversation_context=conversation_context,
        )
        if fast_plan:
            logger.info(
                "leader fast route intent=%s tool=%s",
                fast_plan.intent,
                fast_plan.tool_name,
            )
            return fast_plan

        return self._plan_with_llm(
            input_text,
            rag_strategy,
            chat_service,
            profile_context=profile_context,
            callable_catalog=callable_catalog,
            conversation_context=conversation_context,
        )

    def _plan_learning_workflow(
        self,
        learning_context: Optional[Dict[str, Any]],
    ) -> Optional[LeaderPlan]:
        context = learning_context if isinstance(learning_context, dict) else {}
        intent = str(context.get("intent") or "").strip()
        if str(context.get("courseKey") or "").strip().lower() != "python":
            return None
        if intent not in {
            "resource_package",
            "learning_plan",
            "weakness_review",
            "path_replanning",
        }:
            return None
        return LeaderPlan(
            intent=intent,
            target_agent="leader_agent",
            need_retrieval=True,
            rag_strategy="",
            action="run_learning_workflow",
            route_reason="Python 课程个性化学习请求进入受控多智能体工作流。",
            route_mode="workflow",
        )

    def _plan_learning_guidance_agent(
        self,
        input_text: str,
        callable_catalog: Optional[Dict[str, Any]],
    ) -> Optional[LeaderPlan]:
        normalized = self._normalize_fast_route_text(input_text)
        if not normalized or self._has_explicit_visual_output_request(normalized):
            return None
        if not any(token in normalized for token in (
            "想学", "想学习", "怎么学", "如何学", "如何学习", "学习路线", "入门", "从哪开始",
        )):
            return None
        if not self._catalog_agent_enabled(callable_catalog, "textbook_knowledge_agent"):
            return None
        return LeaderPlan(
            intent="learning_guidance",
            target_agent="textbook_knowledge_agent",
            need_retrieval=False,
            rag_strategy="",
            action="delegate_agent",
            route_reason="用户询问学习方法且未明确要求图片，调用教材知识点智能体生成学习建议；画像输出偏好不覆盖当前指令。",
            route_mode="rules",
        )

    def _has_explicit_visual_output_request(self, input_text: str) -> bool:
        normalized = self._normalize_fast_route_text(input_text)
        if any(token in normalized for token in _EXPLICIT_VISUAL_OUTPUT_TOKENS):
            return True
        return "图片" in normalized and any(token in normalized for token in ("生成", "画", "制作", "做成", "转成"))

    def _plan_explicit_knowledge_generation_request(
        self,
        input_text: str,
        callable_catalog: Optional[Dict[str, Any]],
    ) -> Optional[LeaderPlan]:
        normalized = self._normalize_fast_route_text(input_text)
        has_authorization = any(token in normalized for token in (
            "授权模型", "模型自行生成", "自行生成知识", "自己生成知识", "无需材料", "不用材料",
        ))
        has_knowledge_target = any(token in normalized for token in (
            "知识材料", "知识点", "教材内容", "学习材料",
        ))
        if not has_authorization or not has_knowledge_target:
            return None
        if not self._catalog_agent_enabled(callable_catalog, "textbook_knowledge_agent"):
            return None
        return LeaderPlan(
            intent="textbook_knowledge",
            target_agent="textbook_knowledge_agent",
            need_retrieval=False,
            rag_strategy="",
            action="delegate_agent",
            route_reason="用户已授权模型在无材料时自行生成知识材料，直接调用教材知识点智能体，不再重复确认来源。",
            route_mode="rules",
        )

    def _is_explicit_visual_generation_request(self, input_text: str) -> bool:
        normalized = self._normalize_fast_route_text(input_text)
        if not self._has_explicit_visual_output_request(normalized):
            return False
        return any(token in normalized for token in (
            "我想生成", "帮我生成", "请生成", "给我生成", "生成一个", "生成一张",
            "画一个", "画一张", "制作一个", "制作一张", "做一个", "做一张", "转成",
        ))

    def _plan_explicit_file_export_request(
        self,
        input_text: str,
        callable_catalog: Optional[Dict[str, Any]] = None,
    ) -> Optional[LeaderPlan]:
        normalized = self._normalize_fast_route_text(input_text)
        if "ppt大纲" in normalized or "pptx大纲" in normalized or "幻灯片大纲" in normalized:
            return None
        text_to_file_plan = self._plan_verbatim_text_to_file_request(input_text, callable_catalog)
        if text_to_file_plan:
            return text_to_file_plan
        format_tokens = (
            "word", "docx", "excel", "xlsx", "markdown", "md文件", "ppt", "pptx",
            "文档版", "文件版", "表格版", "幻灯片",
        )
        action_tokens = (
            "导出", "生成", "转成", "转换成", "整理成", "制作成", "做成", "保存为", "给我",
        )
        if not any(token in normalized for token in format_tokens):
            return None
        if not any(token in normalized for token in action_tokens):
            return None
        tool_name = self._resolve_explicit_export_tool(normalized, callable_catalog)
        if not tool_name or not self._catalog_tool_enabled(callable_catalog, tool_name):
            return None
        return LeaderPlan(
            intent="document_export",
            target_agent="leader_agent",
            need_retrieval=False,
            rag_strategy="",
            action="call_tool",
            tool_name=tool_name,
            route_reason="用户明确要求生成指定格式文件，由对应内容整理工具先调用文件内容编排智能体，再生成附件。",
            route_mode="rules",
        )

    def _plan_with_rules(self, input_text: str, rag_strategy: str = "") -> LeaderPlan:
        plan = self._plan_with_rules_impl(input_text, rag_strategy)
        if not plan.route_mode:
            plan.route_mode = "rules"
        return plan

    def _plan_with_rules_impl(self, input_text: str, rag_strategy: str = "") -> LeaderPlan:
        normalized = (input_text or "").strip().lower()
        if is_schedule_intent(input_text):
            intent, answer = self._schedule_fast_route_response(input_text)
            return LeaderPlan(
                intent=intent,
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="",
                action="call_tool",
                tool_name="java_schedule_api",
                route_reason="命中课表意图，优先调用 Java 后端课表接口。",
                answer=answer,
            )
        if any(token in normalized for token in ("知识图谱", "实体关系图", "概念关系图", "knowledge graph")):
            return LeaderPlan("knowledge_graph_image", "leader_agent", False, "", action="call_tool", tool_name="generate_image_tool", route_reason="命中知识图谱生成意图，调用通用图片生成工具。")
        if "架构图" in normalized and "提示词" in normalized:
            return LeaderPlan("architecture_diagram_prompt", "leader_agent", False, "", action="call_tool", tool_name="generate_architecture_tool", route_reason="命中架构图生成意图，调用架构图生成工具。")
        if any(token in normalized for token in ("架构图", "系统架构图", "architecture diagram", "architecture")):
            return LeaderPlan("diagram_architecture", "leader_agent", False, "", action="call_tool", tool_name="generate_architecture_tool", route_reason="命中架构图生成意图，调用架构图生成工具。")
        if "活动图" in normalized and "提示词" in normalized:
            return LeaderPlan("diagram_activity_prompt", "leader_agent", False, "", action="call_tool", tool_name="generate_image_tool", route_reason="命中活动图生成意图，调用通用图片生成工具。")
        if any(token in normalized for token in ("活动图", "泳道图", "activity diagram", "任务活动图")):
            return LeaderPlan("diagram_activity", "leader_agent", False, "", action="call_tool", tool_name="generate_image_tool", route_reason="命中活动图生成意图，调用通用图片生成工具。")
        if "流程图" in normalized and "提示词" in normalized:
            return LeaderPlan("diagram_flowchart_prompt", "leader_agent", False, "", action="call_tool", tool_name="generate_flowchart_tool", route_reason="命中流程图生成意图，调用流程图生成工具。")
        if any(token in normalized for token in ("流程图", "flowchart", "流程")):
            return LeaderPlan("diagram_flowchart", "leader_agent", False, "", action="call_tool", tool_name="generate_flowchart_tool", route_reason="命中流程图生成意图，调用流程图生成工具。")
        if any(token in normalized for token in ("思维导图", "mindmap", "mind map", "脑图")):
            return LeaderPlan("diagram_mind_map", "leader_agent", False, "", action="call_tool", tool_name="generate_mind_map_tool", route_reason="命中思维导图生成意图，调用思维导图生成工具。")
        if (
            any(token in normalized for token in ("生成图片", "画一张", "画张", "配图", "插图", "封面图", "海报", "图片素材", "文生图"))
            and not any(token in normalized for token in ("ppt", "课件", "幻灯片"))
        ):
            return LeaderPlan("image_generation", "leader_agent", False, "", action="call_tool", tool_name="generate_image_tool", route_reason="命中图片生成/配图意图，调用通用图片生成工具。")
        if any(token in normalized for token in ("个人画像汇总", "画像汇总", "画像总结", "画像分析", "画像雷达总结", "profile summary")):
            return LeaderPlan("profile_summary", "profile_summary_agent", False, "", route_reason="命中个人画像汇总意图，分发给个人画像汇总智能体。")
        if any(token in normalized for token in (
            "热门岗位", "岗位推荐", "岗位雷达", "就业方向", "本周热招", "招聘趋势",
            "软件工程岗位", "hot job", "job recommendation", "weekly job",
        )):
            return LeaderPlan(
                "weekly_job_recommendation",
                "leader_agent",
                False,
                "",
                action="call_tool",
                tool_name="weekly_job_recommendation_tool",
                route_reason="命中热门岗位/岗位雷达意图，调用岗位雷达工具。",
            )
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
            return LeaderPlan("ppt_structure", "ppt_structure_agent", False, "", route_reason="命中 PPT 布局/排版意图，分发给 Presenton 结构智能体。")
        if (
            any(token in normalized for token in ("ppt图片", "ppt配图", "ppt插图", "ppt封面", "课件配图"))
            or (
                any(token in normalized for token in ("ppt", "课件", "幻灯片"))
                and any(token in normalized for token in ("图片", "配图", "插图", "封面图", "封面"))
            )
        ):
            return LeaderPlan("ppt_image", "leader_agent", False, "", action="call_tool", tool_name="generate_image_tool", route_reason="命中 PPT 图片/配图意图，调用通用图片生成工具。")
        if any(token in normalized for token in ("ppt", "幻灯片", "课件", "演示文稿")):
            return LeaderPlan(
                "ppt_generation",
                "leader_agent",
                False,
                "",
                action="call_tool",
                tool_name="ai_ppt_generation_tool",
                route_reason="命中 PPT/课件生成意图，调用 AI PPT 生成工具。",
            )
        if any(token in normalized for token in ("md", "markdown", "知识点提取", "提取知识点", "知识点整理")):
            return LeaderPlan("textbook_knowledge", "textbook_knowledge_agent", False, "", route_reason="命中 Markdown/文本教材知识点整理意图，统一交给教材知识点智能体。")
        if any(token in normalized for token in ("教材", "课本", "章节", "考点", "知识点", "课程内容")):
            return LeaderPlan("textbook_knowledge", "textbook_knowledge_agent", False, "", route_reason="命中教材/课程知识意图，使用教材知识点智能体直接整理。")
        return LeaderPlan("campus_search", "textbook_knowledge_agent", False, "", route_reason="未命中特定生成类意图，按校园知识查询处理。")

    def _plan_high_confidence_service_query(
        self,
        input_text: str,
        rag_strategy: str = "",
        callable_catalog: Optional[Dict[str, Any]] = None,
        conversation_context: Optional[Dict[str, Any]] = None,
    ) -> Optional[LeaderPlan]:
        if not self._fast_route_enabled() or str(rag_strategy or "").strip():
            return None
        try:
            normalized = self._normalize_fast_route_text(input_text)
            if not normalized or self._requires_llm_routing(normalized, conversation_context):
                return None

            domains = self._fast_route_domains(normalized)
            if len(domains) != 1:
                return None
            domain = domains[0]
            tool_name = {
                "schedule": "java_schedule_api",
                "activity": "java_activity_api",
                "meeting": "java_meeting_api",
                "canteen": "java_canteen_api",
            }[domain]
            if not self._catalog_tool_enabled(callable_catalog, tool_name):
                return None

            if domain == "schedule":
                intent, answer = self._schedule_fast_route_response(input_text)
                return LeaderPlan(
                    intent=intent,
                    target_agent="leader_agent",
                    need_retrieval=False,
                    rag_strategy="",
                    action="call_tool",
                    tool_name=tool_name,
                    route_reason="高置信度命中明确课表查询，跳过模型路由并调用 Java 后端课表接口。",
                    answer=answer,
                    route_mode="rules",
                )

            labels = {
                "activity": ("activity_query", "校园活动", "正在为你查询校园活动。"),
                "meeting": ("meeting_query", "会议安排", "正在为你查询会议安排。"),
                "canteen": ("canteen_query", "食堂餐饮", "正在为你查询食堂餐饮信息。"),
            }
            intent, label, answer = labels[domain]
            return LeaderPlan(
                intent=intent,
                target_agent="leader_agent",
                need_retrieval=False,
                rag_strategy="",
                action="call_tool",
                tool_name=tool_name,
                route_reason=f"高置信度命中明确{label}查询，跳过模型路由并调用 Java 后端接口。",
                answer=answer,
                route_mode="rules",
            )
        except Exception as exc:
            logger.warning("leader fast route failed; fallback to llm: %s", exc)
            return None

    def _fast_route_domains(self, normalized: str) -> List[str]:
        if self._is_service_meta_question(normalized):
            return []

        domains: List[str] = []
        if self._is_high_confidence_schedule_query(normalized):
            domains.append("schedule")
        if self._is_high_confidence_activity_query(normalized):
            domains.append("activity")
        if self._is_high_confidence_meeting_query(normalized):
            domains.append("meeting")
        if self._is_high_confidence_canteen_query(normalized):
            domains.append("canteen")
        return domains

    def _is_service_meta_question(self, normalized: str) -> bool:
        if any(token in normalized for token in _FAST_ROUTE_META_HARD_TOKENS):
            return True
        if any(phrase in normalized for phrase in _FAST_ROUTE_META_PHRASES):
            return True
        if self._is_service_knowledge_question(normalized):
            return True
        if "系统" in normalized and any(
            token in normalized
            for token in ("为什么", "为何", "实现", "开发", "设计", "原理", "失败", "有问题")
        ):
            return True
        return False

    def _is_service_knowledge_question(self, normalized: str) -> bool:
        if any(token in normalized for token in _FAST_ROUTE_KNOWLEDGE_INTENT_TOKENS):
            return True
        for entity in _FAST_ROUTE_SERVICE_ENTITY_TOKENS:
            entity_index = normalized.find(entity)
            if entity_index < 0:
                continue
            knowledge_start = entity_index + len(entity)
            if any(normalized.find(topic, knowledge_start) >= 0 for topic in _FAST_ROUTE_KNOWLEDGE_TOPIC_TOKENS):
                return True
        return False

    def _has_explicit_service_query_signal(self, normalized: str) -> bool:
        return any(token in normalized for token in _FAST_ROUTE_QUERY_ACTION_TOKENS) or any(
            token in normalized for token in _FAST_ROUTE_QUERY_SCOPE_TOKENS
        ) or any(token in normalized for token in _FAST_ROUTE_QUESTION_TOKENS)

    def _is_high_confidence_activity_query(self, normalized: str) -> bool:
        if any(token in normalized for token in _ACTIVITY_NON_QUERY_TOKENS):
            return False
        if not any(token in normalized for token in _FAST_ROUTE_ACTIVITY_TOKENS):
            return False
        if normalized in {
            "校园活动",
            "活动安排",
            "最近活动",
            "近期活动",
            "讲座安排",
            "比赛安排",
            "报名活动",
        }:
            return True
        return self._has_explicit_service_query_signal(normalized)

    def _is_high_confidence_meeting_query(self, normalized: str) -> bool:
        if any(token in normalized for token in _MEETING_NON_QUERY_TOKENS):
            return False
        if not any(token in normalized for token in _FAST_ROUTE_MEETING_TOKENS):
            return False
        if normalized in {
            "我的会议",
            "会议列表",
            "会议状态",
            "预约的会议",
            "已预约会议",
            "预约会议安排",
            "有什么会议",
            "查询会议安排",
            "查看会议安排",
            "今天的会议安排",
            "明天的会议安排",
            "本周会议安排",
        }:
            return True
        return self._has_explicit_service_query_signal(normalized) or any(
            token in normalized for token in ("我的会议", "预约的会议", "已预约会议")
        )

    def _is_high_confidence_canteen_query(self, normalized: str) -> bool:
        if any(token in normalized for token in _CANTEEN_NON_QUERY_TOKENS):
            return False
        if not any(token in normalized for token in _FAST_ROUTE_CANTEEN_TOKENS):
            return False
        if normalized in {
            "食堂",
            "档口",
            "食堂菜单",
            "餐厅菜单",
            "有什么菜",
            "菜品价格",
            "吃什么",
            "餐饮优惠",
            "餐饮信息",
        }:
            return True
        if any(token in normalized for token in ("有什么菜", "吃什么")):
            return True
        return self._has_explicit_service_query_signal(normalized)

    def _is_high_confidence_schedule_query(self, normalized: str) -> bool:
        if any(token in normalized for token in _SCHEDULE_NON_QUERY_TOKENS):
            return False
        typical_query_tokens = (
            "下节课",
            "下一节课",
            "再下一节课",
            "后一节课",
            "下一门课",
            "再下一门课",
            "下门课",
            "今天有什么课",
            "明天有什么课",
            "后天有什么课",
            "今天有课吗",
            "明天有课吗",
            "后天有课吗",
            "今天有没有课",
            "明天有没有课",
            "后天有没有课",
            "本周有没有课",
            "这周有没有课",
            "本周有什么课",
            "这周有什么课",
            "今日课表",
            "今天的课表",
            "明天的课表",
            "后天的课表",
            "本周课表",
            "这周课表",
            "下周课表",
            "我的课表",
        )
        if any(token in normalized for token in typical_query_tokens):
            return True
        if is_semester_schedule_query(normalized) or is_all_semester_schedule_query(normalized):
            return True
        if normalized in {
            "课表",
            "我的课表",
            "查询课表",
            "查看课表",
            "查课表",
            "课程安排",
            "我的课程安排",
            "课程列表",
            "全部课程",
        }:
            return True
        has_schedule_entity = any(token in normalized for token in ("课表", "课程安排", "课程列表"))
        if has_schedule_entity and self._has_explicit_service_query_signal(normalized):
            return True
        return bool(
            re.search(
                r"(?:第\d+周|周[一二三四五六日天]|星期[一二三四五六日天]).*(?:有|上|没|没有).*课",
                normalized,
            )
        )

    def _requires_llm_routing(
        self,
        normalized: str,
        conversation_context: Optional[Dict[str, Any]],
    ) -> bool:
        if any(token in normalized for token in _FAST_ROUTE_MULTI_INTENT_TOKENS):
            return True
        context = conversation_context if isinstance(conversation_context, dict) else {}
        has_context = bool(context.get("summary") or context.get("turns") or context.get("lastSubjects"))
        if has_context and any(token in normalized for token in _FAST_ROUTE_FOLLOWUP_TOKENS):
            return True
        if has_context:
            subjects = [
                self._normalize_fast_route_text(subject)
                for subject in (context.get("lastSubjects") or [])
                if str(subject or "").strip()
            ]
            is_subject_followup = any(subject and subject in normalized for subject in subjects)
            if is_subject_followup and any(
                token in normalized
                for token in ("老师", "教师", "谁教", "几节课", "几次课", "课时", "什么时候", "哪天", "周几", "几点", "在哪", "哪里")
            ):
                return True
        return False

    def _sanitize_planned_tool(
        self,
        plan: LeaderPlan,
        callable_catalog: Optional[Dict[str, Any]],
    ) -> LeaderPlan:
        if plan.action != "call_tool" or not str(plan.tool_name or "").strip():
            return plan
        if self._catalog_tool_enabled(callable_catalog, plan.tool_name):
            return plan
        return LeaderPlan(
            intent=plan.intent or "campus_search",
            target_agent="leader_agent",
            need_retrieval=False,
            rag_strategy="",
            action="direct_answer",
            tool_name="",
            route_reason="所选能力不在当前启用清单，改为直接回答用户问题。",
            answer=plan.answer,
            route_mode="llm_fallback",
        )

    def _catalog_tool_enabled(self, callable_catalog: Optional[Dict[str, Any]], tool_name: str) -> bool:
        if not isinstance(callable_catalog, dict):
            return True
        tools = callable_catalog.get("tools")
        if not isinstance(tools, list):
            return True
        for item in tools:
            if not isinstance(item, dict) or str(item.get("name") or "").strip() != tool_name:
                continue
            return item.get("enabled") is not False
        return False

    def _catalog_agent_enabled(self, callable_catalog: Optional[Dict[str, Any]], agent_name: str) -> bool:
        if not isinstance(callable_catalog, dict):
            return True
        agents = callable_catalog.get("agents")
        if not isinstance(agents, list):
            return True
        for item in agents:
            if not isinstance(item, dict) or str(item.get("name") or "").strip() != agent_name:
                continue
            return item.get("enabled") is not False
        return False

    def _schedule_fast_route_response(self, input_text: str) -> tuple[str, str]:
        if is_course_teacher_query(input_text):
            return "course_teacher", "正在为你查询课程老师。"
        if is_course_count_query(input_text):
            return "course_count", "正在为你查询课程次数。"
        if is_course_time_query(input_text):
            return "course_time", "正在为你查询课程时间。"
        if is_semester_schedule_query(input_text) or is_all_semester_schedule_query(input_text):
            return "schedule", "正在为你查询本学期课表。"
        return "schedule", "正在为你查询课程安排。"

    def _fast_route_enabled(self) -> bool:
        value = str(os.getenv(LEADER_FAST_ROUTE_ENABLED_ENV, "true") or "").strip().lower()
        return value not in {"0", "false", "no", "off", "disabled"}

    def _normalize_fast_route_text(self, value: Any) -> str:
        return re.sub(r"[\s，。！？,.!?？、；;：:（）()\[\]{}\"']", "", str(value or "").strip().lower())

    def _plan_with_llm(
        self,
        input_text: str,
        rag_strategy: str,
        chat_service=None,
        profile_context: Optional[Dict[str, Any]] = None,
        callable_catalog: Optional[Dict[str, Any]] = None,
        conversation_context: Optional[Dict[str, Any]] = None,
    ) -> LeaderPlan:
        provider = chat_service or get_chat_model_provider()
        text = provider.complete(
            system_prompt=load_agent_prompt(self.name),
            user_prompt=build_leader_router_user_prompt(
                input_text,
                rag_strategy,
                profile_context=profile_context,
                callable_catalog=callable_catalog,
                conversation_context=conversation_context,
            ),
        )
        plan = parse_json_object(text)
        if not plan:
            # 路由模型偶尔会忽略 JSON 约束或在 JSON 外追加解释。
            # 解析失败时只能安全地退化为直接回答，不能猜测工具并执行。
            fallback_answer = str(text or "").strip()
            if fallback_answer:
                logger.warning("leader llm returned non-JSON; fallback to direct answer: %s", fallback_answer[:300])
                return LeaderPlan(
                    intent="campus_search",
                    target_agent="leader_agent",
                    need_retrieval=False,
                    rag_strategy="",
                    action="direct_answer",
                    answer=fallback_answer,
                    route_reason="Leader 路由结果不是合法 JSON，已安全降级为直接回答，未执行任何工具。",
                    route_mode="llm_fallback",
                )
            raise HTTPException(status_code=502, detail="Leader LLM 路由结果为空，无法生成安全路由。")
        parsed = self._parse_llm_plan(plan, rag_strategy)
        if not parsed:
            raise HTTPException(status_code=502, detail=f"Leader LLM 路由结果字段不合法：{plan}")
        parsed = self._enforce_current_input_output_intent(parsed, input_text, callable_catalog)
        parsed = self._sanitize_planned_tool(parsed, callable_catalog)
        logger.info(
            "leader llm plan intent=%s action=%s target=%s retrieval=%s",
            parsed.intent,
            parsed.action,
            parsed.target_agent,
            parsed.need_retrieval,
        )
        return parsed

    def _enforce_current_input_output_intent(
        self,
        plan: LeaderPlan,
        input_text: str,
        callable_catalog: Optional[Dict[str, Any]],
    ) -> LeaderPlan:
        if (
            plan.target_agent not in _VISUAL_GENERATION_AGENTS
            and plan.tool_name not in VISUAL_GENERATION_TOOL_NAMES
        ):
            return plan
        if self._has_explicit_visual_output_request(input_text):
            return plan
        if self._catalog_agent_enabled(callable_catalog, "textbook_knowledge_agent"):
            return LeaderPlan(
                intent="textbook_knowledge",
                target_agent="textbook_knowledge_agent",
                need_retrieval=False,
                rag_strategy="",
                action="delegate_agent",
                route_reason="当前输入没有明确要求图片或图表，已阻止画像输出偏好覆盖本轮指令，并改由教材知识点智能体处理。",
                route_mode="rules",
            )
        raise HTTPException(
            status_code=502,
            detail="Leader 模型选择了与当前输入不符的图片能力，已拒绝生成系统纠偏回复。",
        )

    def _parse_llm_plan(self, plan: Dict[str, Any], requested_rag_strategy: str) -> Optional[LeaderPlan]:
        if not isinstance(plan, dict):
            return None
        action = str(plan.get("action") or "direct_answer").strip()
        tool_name = str(plan.get("tool_name") or plan.get("toolName") or "").strip()
        if action not in {"direct_answer", "call_tool"}:
            action = "direct_answer"
        target_agent = "leader_agent"
        need_retrieval = False
        rag_strategy = self._normalize_rag_strategy(plan.get("rag_strategy") or plan.get("ragStrategy"))
        if action != "call_tool":
            rag_strategy = ""
        return LeaderPlan(
            intent=str(plan.get("intent") or "campus_search").strip() or "campus_search",
            target_agent=target_agent,
            need_retrieval=need_retrieval,
            rag_strategy=rag_strategy,
            action=action,
            tool_name=tool_name if action == "call_tool" else "",
            route_reason=str(plan.get("route_reason") or plan.get("routeReason") or "Leader LLM 完成意图识别。").strip(),
            answer=str(plan.get("answer") or "").strip(),
            route_mode="llm",
        )

    def _plan_for_requested_agent(self, requested_agent: Optional[str], rag_strategy: str) -> Optional[LeaderPlan]:
        agent_name = normalize_agent_name(requested_agent)
        if not agent_name:
            return None
        profile = get_agent_profile(agent_name)
        if not profile or agent_name == "leader_agent":
            return None
        # 专业智能体不再作为 Leader 的独立路由目标；相关能力必须以系统工具形式进入工具目录。
        return None

    def load_memory(self, session_token: str) -> List[Dict[str, str]]:
        return memory_store.get_history(session_token)

    def save_memory(self, session_token: str, user_input: str, assistant_answer: str) -> None:
        memory_store.append(session_token, user_input, assistant_answer)

    def load_context(self, session_token: str) -> Dict[str, Any]:
        return memory_store.get_context(session_token)

    def save_context(
        self,
        session_token: str,
        user_input: str,
        assistant_answer: str,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> None:
        memory_store.append_context_turn(session_token, user_input, assistant_answer, metadata=metadata or {})

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

    def summarize_tool_result(
        self,
        *,
        input_text: str,
        plan: LeaderPlan,
        tool_display_name: str,
        tool_results: List[Dict[str, Any]],
        chat_service=None,
    ) -> str:
        provider = chat_service or get_chat_model_provider()
        bounded_results = self._shape_tool_results_for_answer(input_text, plan, tool_results)
        answer_policy = self._tool_result_answer_policy(input_text, plan, tool_results)
        payload = {
            "user_input": input_text or "",
            "leader_intent": plan.intent,
            "leader_route_reason": plan.route_reason,
            "leader_planning_answer": plan.answer,
            "tool_name": plan.tool_name,
            "tool_display_name": tool_display_name,
            "tool_result_count": len(tool_results or []),
            "answer_policy": answer_policy,
            "tool_results": bounded_results,
            "answer_requirements": [
                "用自然中文回答用户，不要输出 JSON。",
                "严格按用户问题的范围回答；不要主动扩展成文档、报告、分析或完整明细。",
                "前端支持 Markdown 渲染：优先用 ##/### 标题、列表、加粗组织层级；代码用 ```语言 围栏；表格仅在确有必要时使用。",
                "answer_policy 若要求纯文本或固定格式，则遵守该策略，不要强行套 Markdown。",
                "不要在答案末尾反问用户还要哪部分；用户需要会继续问。",
                "只能基于 tool_results 里的数据整理，不要编造课程、活动、会议、菜品、位置、物品、时间、地点或数量。",
                "当用户提到简称、英文缩写、别名或不完整名称时，由你在 tool_results 中做语义匹配；不要要求完全同名。",
                "如果多个结果都可能匹配且无法判断，先说明候选并请用户确认，不要随便选。",
                "如果 tool_results 为空，要说明已调用对应系统能力但暂时没有可展示数据，并给出可能检查项。",
                "如果 tool_results 中包含 type=tool_empty_result，要明确告诉用户本次查询没有查到数据，不要伪造结果。",
                "如果 tool_results 中包含 type=tool_execution_error，要明确告诉用户工具调用失败，并说明错误状态；不要把失败说成没有数据。",
                "如果 leader_planning_answer 不为空，可承接它，但最终必须给出查询结果或空结果说明。",
                *answer_policy.get("requirements", []),
            ],
        }
        text = provider.complete(
            system_prompt=(
                "你负责把智慧校园系统接口返回的数据整理成给用户看的最终回答。"
                "你不是路由器，不要重新选择工具或智能体；不要输出 JSON 或内部字段名。"
                "默认用清晰的 Markdown 层级组织答案；若 answer_policy 指定纯文本或固定格式，则严格遵守。"
                "必须遵守用户意图对应的 answer_policy，答案短而准。"
            ),
            user_prompt=json.dumps(payload, ensure_ascii=False),
        )
        answer = (text or "").strip()
        parsed = parse_json_object(answer)
        if parsed and parsed.get("action") and parsed.get("intent"):
            return ""
        return self._clean_tool_answer(answer, plan)

    def _clean_tool_answer(self, answer: str, plan: LeaderPlan) -> str:
        text = str(answer or "").strip()
        if plan.tool_name not in CAMPUS_SERVICE_TOOL_NAMES:
            return text
        text = re.sub(r"^#{1,6}\s*", "", text, flags=re.MULTILINE)
        text = re.sub(r"\*\*(.*?)\*\*", r"\1", text)
        text = re.sub(r"`([^`]+)`", r"\1", text)
        text = re.sub(r"^\s*[-*_]{3,}\s*$", "", text, flags=re.MULTILINE)
        text = re.sub(
            r"^\s*(你需要的是哪部分的具体信息.*|还需要.*吗[？?]?|是否还需要.*|如果需要.*|需要我.*|要不要.*)$",
            "",
            text,
            flags=re.IGNORECASE | re.MULTILINE,
        )
        text = re.sub(r"\n{3,}", "\n\n", text)
        return text.strip()

    def _tool_result_answer_policy(
        self,
        input_text: str,
        plan: LeaderPlan,
        tool_results: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        if plan.tool_name == "tool_capability_query":
            return {
                "mode": "capability_query",
                "format": "concise_capability_summary",
                "requirements": [
                    "只根据 tool_results 中 enabledTools 的 purpose 和 category 总结当前可用能力。",
                    "按用户能理解的功能分类，最多列出 5 类；每类用一句话概括，不要逐项照抄工具名称。",
                    "不要输出 tool_name、agent_name、category 英文值、outputs 或其他内部字段。",
                    "不要把禁用工具、未出现在 enabledTools 中的能力或静态提示词中的能力说成可用。",
                    "如果 enabledTools 为空，只说明当前没有已启用的业务工具，仍可以进行普通对话。",
                ],
            }
        if plan.tool_name == "java_schedule_api":
            mode = self._schedule_answer_mode(input_text, tool_results)
            if mode == "course_time":
                return {
                    "mode": mode,
                    "format": "course_time_answer",
                    "requirements": [
                        "用户是在问某门课什么时候学/什么时候上，只回答该课程的上课时间和地点。",
                        "你需要从 tool_results 中找出与用户原话最接近的课程；例如用户说 python，可以匹配课程名 Python程序设计。",
                        "输出纯文本，格式必须接近：课程名：...\n学期：...\n理论课（老师）：周几 节次（周次）；地点：...\n实验课（老师）：周几 节次（周次）；地点：...",
                        "同一类课有多个时间，用中文分号隔开；不同理论/实验/实践课分行展示。",
                        "如果 tool_results 的 queryScope 为 all_semesters_fallback，要先说明当前学期没查到，已自动扩大到所有学期，并回答历史学期命中的课程。",
                        "不要输出 Markdown 的 **、###、---；不要最后反问用户。",
                        "不要列出无关课程。",
                    ],
                }
            if mode == "course_count":
                return {
                    "mode": mode,
                    "format": "course_count_answer",
                    "requirements": [
                        "用户是在问某门课本学期有几节/几次/多少课时，只回答该课程的上课次数或课时估算。",
                        "你需要从 tool_results 中找出与用户原话最接近的课程；例如用户说 linux，可以匹配课程名 Linux系统。",
                        "可以根据 scheduleItems、classSessions、weekRange 推算；无法精确推算时，说明按当前课表记录能看到几条上课安排。",
                        "如果 tool_results 的 queryScope 为 all_semesters_fallback，要先说明当前学期没查到，已自动扩大到所有学期，再回答历史学期结果。",
                        "不要回答老师是谁，不要介绍课程知识点，不要列出无关课程。",
                        "如果没有匹配课程，只说暂未在课表中查到这门课的上课安排。",
                    ],
                }
            if mode == "course_teacher":
                return {
                    "mode": mode,
                    "format": "course_teacher_answer",
                    "requirements": [
                        "用户是在问某门课由谁教，只回答课程名和老师姓名。",
                        "你需要从 tool_results 中找出与用户原话最接近的课程；例如用户说 linux，可以匹配课程名 Linux系统。",
                        "不要解释课程知识点，不要介绍 Linux/教材内容，不要展开上课时间、周次、地点或完整课表。",
                        "如果 tool_results 的 queryScope 为 all_semesters_fallback，要先说明当前学期没查到，已自动扩大到所有学期，再回答历史学期任课老师。",
                        "如果多条记录是同一门课同一位老师，只合并回答一次。",
                        "如果没有匹配课程，只说暂未在课表中查到这门课的任课老师。",
                    ],
                }
            if mode == "course_list":
                return {
                    "mode": mode,
                    "format": "short_course_list",
                    "requirements": [
                        "用户是在问本学期/全部学期有哪些课，只列课程清单，不展开每次上课时间、周次、地点或教室。",
                        "把同一门课的多条上课安排合并成一门课；课程数量按去重后的课程数，不按上课安排条数。",
                        "可以保留学期、课程名、教师、学分、考核方式；没有字段就不要提。",
                        "不要输出“具体如下”“完整课表”“上课安排”“第几周”“第几节”等多余说明。",
                        "不要使用三级标题、分隔线或文档式 Markdown；最多用一句引导语加简短项目符号。",
                    ],
                }
            if mode == "session_lookup":
                return {
                    "mode": mode,
                    "format": "specific_class_lookup",
                    "requirements": [
                        "用户在问某天/某节/本周有没有课，直接回答对应课程、节次、地点和教师。",
                        "不要列出整个学期的其他课程。",
                        "如果没有课，只说该时间段暂未查到课程。",
                    ],
                }
            return {
                "mode": mode,
                "format": "schedule_overview",
                "requirements": [
                    "按用户询问的日期、星期或周次列课表；只展开必要的节次、课程、地点和教师。",
                    "不要主动补充学分、考核方式等与上课安排无关的信息，除非用户问课程详情。",
                ],
            }
        if plan.tool_name == "java_activity_api":
            return {
                "mode": "activity_query",
                "format": "activity_list_answer",
                "requirements": [
                    "用户是在问校园活动、讲座、比赛或报名信息，只回答匹配到的活动内容。",
                    "每条活动优先包含活动名、时间、地点、报名/状态；字段不存在就不要提。",
                    "用户问最近/可报名/某类活动时，只筛选并展示相关活动，不要把所有接口字段铺开。",
                    "结果较多时最多先展示 5 条，并说明还查到多少条。",
                    "不要编造报名链接、名额、主办方或地点。",
                ],
            }
        if plan.tool_name == "java_meeting_api":
            return {
                "mode": "meeting_query",
                "format": "meeting_list_answer",
                "requirements": [
                    "用户是在问会议列表、我的会议、预约会议或会议状态，只回答会议安排信息。",
                    "每条会议优先包含会议名、时间、地点/会议室、状态；字段不存在就不要提。",
                    "会议纪要、总结、转写和成员分析不是这个工具的输出，不要把查询结果写成会议纪要。",
                    "结果较多时最多先展示 5 条，并说明还查到多少条。",
                    "不要编造参会人、会议链接或审批状态。",
                ],
            }
        if plan.tool_name == "java_canteen_api":
            return {
                "mode": "canteen_query",
                "format": "canteen_answer",
                "requirements": [
                    "用户是在问食堂、餐厅、档口、菜品、吃什么或餐饮优惠，只回答餐饮相关结果。",
                    "每条结果优先包含名称、食堂/档口、价格/优惠、位置；字段不存在就不要提。",
                    "用户问某个菜品或吃什么时，由你在 tool_results 中匹配菜品、档口或优惠，不要要求完全同名。",
                    "结果较多时最多先展示 5 条，并说明还查到多少条。",
                    "不要编造价格、库存、营业时间或优惠规则。",
                ],
            }
        if plan.tool_name == "java_facility_api":
            return {
                "mode": "facility_location",
                "format": "facility_location_answer",
                "requirements": [
                    "用户是在问教学楼、宿舍、操场、食堂等设施位置，只回答匹配设施的位置和必要定位信息。",
                    "每条结果优先包含设施名、位置、楼宇/校区、类型；字段不存在就不要提。",
                    "用户问路线或导航时，只能基于 tool_results 已有位置提示，不能编造路线。",
                    "结果较多时最多先展示 5 条，并说明还查到多少条。",
                    "不要输出经纬度以外的内部字段名。",
                ],
            }
        if plan.tool_name == "java_secondhand_api":
            return {
                "mode": "secondhand_query",
                "format": "secondhand_answer",
                "requirements": [
                    "用户是在问旧物、二手、闲置或转让物品，只回答匹配物品信息。",
                    "每条结果优先包含物品名、价格、成色/状态、发布位置或时间；字段不存在就不要提。",
                    "不要展示手机号、微信号、精确个人联系信息等敏感信息；如接口返回联系人，只提示在系统内查看。",
                    "结果较多时最多先展示 5 条，并说明还查到多少条。",
                    "不要编造砍价建议、交易状态或联系方式。",
                ],
            }
        return {
            "mode": "tool_result",
            "format": "concise_answer",
            "requirements": [
                "优先回答用户直接问的对象，不要把接口返回的所有字段都铺开。",
                "结果较多时先给核心列表，再提示还有多少条可继续查看。",
            ],
        }

    def _schedule_answer_mode(self, input_text: str, tool_results: List[Dict[str, Any]]) -> str:
        text = (input_text or "").strip()
        compact = re.sub(r"\s+", "", text)
        if is_course_time_query(text):
            return "course_time"
        if is_course_count_query(text):
            return "course_count"
        if is_course_teacher_query(text):
            return "course_teacher"
        asks_full_detail = any(token in compact for token in ("完整课表", "详细课表", "上课安排", "什么时候", "几点", "教室", "地点", "在哪", "哪儿"))
        asks_specific_slot = any(token in compact for token in ("今天", "明天", "后天", "周一", "周二", "周三", "周四", "周五", "周六", "周日", "星期", "第"))
        result_types = {str(item.get("type") or "") for item in tool_results or [] if isinstance(item, dict)}
        has_course_summary = "course_schedule_summary" in result_types
        if has_course_summary and not asks_full_detail and (
            is_semester_schedule_query(text)
            or is_all_semester_schedule_query(text)
            or any(token in compact for token in ("有什么课", "有哪些课", "什么课程", "课程列表"))
        ):
            return "course_list"
        if asks_specific_slot:
            return "session_lookup"
        return "schedule_overview"

    def _shape_tool_results_for_answer(
        self,
        input_text: str,
        plan: LeaderPlan,
        tool_results: List[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        if not isinstance(tool_results, list):
            return []
        bounded_results = tool_results[:30]
        schedule_mode = self._schedule_answer_mode(input_text, tool_results)
        if plan.tool_name != "java_schedule_api" or schedule_mode not in {"course_list", "course_teacher"}:
            return bounded_results
        shaped_results: List[Dict[str, Any]] = []
        for item in bounded_results:
            if not isinstance(item, dict):
                continue
            shaped = {
                key: value
                for key, value in item.items()
                if key not in {"scheduleItems", "scheduleCount", "classSessions", "weekRange", "weekday", "weekdayText", "location"}
            }
            shaped_results.append(shaped)
        return shaped_results

    def _normalize_rag_strategy(self, value: Any) -> str:
        rag_strategy = str(value or "").strip()
        if not rag_strategy or rag_strategy in {"按目标智能体默认策略", "默认策略", "目标智能体默认策略"}:
            return ""
        return ""


leader_agent = LeaderAgent()


def _catalog_has_tool(callable_catalog: Optional[Dict[str, Any]], tool_name: str) -> bool:
    if not isinstance(callable_catalog, dict):
        return True
    tools = callable_catalog.get("tools")
    if not isinstance(tools, list):
        return True
    return any(
        isinstance(item, dict) and str(item.get("name") or "").strip() == tool_name
        for item in tools
    )


def _leader_profile_usage_policy(callable_catalog: Optional[Dict[str, Any]]) -> List[str]:
    policies = [
        "必须参考 profile_snapshot，但用户当前问题优先级最高。",
        "高置信度画像可以用于推荐顺序、解释深度和资源形式。",
        "中低置信度画像只能作为倾向，不能武断判断用户能力。",
        "行为证据实时沉淀，雷达图分数由 Java 后端定时汇总更新。",
        "Leader 不能直接更新画像分数；发现明确证据或冲突时只在 route_reason 中说明，由 Java 按 campus-profile-evidence-v1 记录候选证据。",
        "当前输入与画像冲突时，以当前输入完成本轮回答，并在 route_reason 中说明冲突倾向。",
        "profile_snapshot.outputPreferenceHints 只能用于提供后续图片版/文件版选项，不能凭偏好把普通学习、解释或问答请求改成生图任务。只有当前 user_input 明确要求图片、图解或具体图表时，才允许选择图片/图表智能体。",
        "如果任务既可做图片又可做文件且没有稳定偏好，先询问用户要图片形式还是文件形式。",
    ]
    content_export_tools = (
        "text_to_markdown_tool",
        "text_to_docx_tool",
        "text_to_txt_tool",
        "ai_ppt_generation_tool",
    )
    if any(_catalog_has_tool(callable_catalog, tool_name) for tool_name in content_export_tools):
        policies.extend([
            "用户要求文件版/文档版/Word/Markdown 文件时，按目标格式选择 text_to_markdown_tool、text_to_docx_tool 或 text_to_txt_tool；工具内部负责组织内容并生成附件，不要把长内容只当纯文字回复。",
            "用户要求 PPT/PPTX/幻灯片文件时，选择 ai_ppt_generation_tool 生成完整演示文稿。",
            "用户要求题库表格或题库 Excel 时，仍先选择对应题型智能体生成严格题库 JSON，再由 md/docx 内容整理工具导出。",
            "用户已经提供了要导出的 Markdown、普通文本或标准题库 JSON，且要求整理成文件时，调用对应格式的内容整理工具。",
        ])
    if any(
        _catalog_has_tool(callable_catalog, tool_name)
        for tool_name in ("text_to_txt_tool", "text_to_markdown_tool", "text_to_docx_tool")
    ):
        policies.append(
            "用户要求把已提供的文本按原文转成 txt/md/word 文件时，按目标格式分别选择 text_to_txt_tool、text_to_markdown_tool 或 text_to_docx_tool，不整理、不改写内容。"
        )
    policies.extend([
        "Mermaid 图表如需文件版，可按 Markdown 文件导出；不再单独提供 mmd 源码附件。",
        "用户表达课表、活动、会议列表/状态、食堂餐饮、设施位置、旧物二手等查询意图时，你必须根据当前语义自行从 leader_callable_catalog.tools 中选择对应的 Java 后端服务工具，而不是依赖系统关键词规则或编造答案。",
        "当前 user_input 永远优先于 conversation_context；只有 user_input 缺少主语或对象时，才允许用 conversation_context 补全。",
        "如果 user_input 是追问、省略主语或短句，例如“上几次呢”“老师呢”“在哪上”“什么时候呢”“这个呢”，必须结合 conversation_context 的最近主题、最近工具和摘要恢复真实意图；上下文已能确定时不要再反问用户。",
        "如果 user_input 已经表达新的课表状态意图，例如“今天有课吗”“从什么时候开始没有课”“哪天没课”，不要把最近课程主题补进去；这类问题是课表查询，不是某门课时间查询。",
        "conversation_context 只用于理解本会话追问，不得当作事实来源编造答案；涉及课表、会议、食堂等业务数据仍必须调用对应启用工具。",
        "用户问某门课的老师是谁、任课老师、授课教师、谁教某门课时，这是课程信息查询，必须优先选择 java_schedule_api，不要路由到教材知识点智能体。",
        "用户问某门课什么时候学、什么时候上课、周几几点上时，也是课程信息查询，必须优先选择 java_schedule_api；但“什么时候开始没有课/哪天没课/今天有课吗”属于课表状态查询，不要套用最近课程。",
        "用户问某门课本学期有几节课、几次课、多少课时或上课次数时，也是课程信息查询，必须优先选择 java_schedule_api。",
        "会议纪要/总结/转写/成员分析以及活动图/流程图等能力，统一通过已启用的系统工具处理，不要单独委派专业智能体。",
        "路由只有两种：普通问题使用 direct_answer；确实需要系统能力时使用 call_tool。只能选择 leader_callable_catalog.tools 中的工具；该列表已经过滤掉后台关闭项，禁止选择或提及未出现在清单中的工具。",
        "用户询问你有什么功能、是否支持生图/PPT/题库/文档/图表等能力时，必须 action=call_tool，tool_name=tool_capability_query；能力清单由该工具读取当前后台开关后返回，不能由 Leader 根据静态目录直接回答。",
        "问候和闲聊可以由 Leader 直接回答；能力询问不允许 direct_answer，必须调用 tool_capability_query。",
        "能力查询工具返回结果后，面向普通用户说明可完成的事情，不要主动输出内部 agent/tool 标识；只有用户明确询问技术名称时才可给出。",
        "某项能力未出现在启用清单中时，必须明确回答当前不可用或尚未完成配置，不得声称可以生成后再执行失败，也不得向用户暴露已关闭工具的内部名称。",
        "leader_output_push_strategies 只是已启用能力的输出路由提示，不能单独证明某种生成能力当前可用。",
        "所有图片、活动图、知识图谱和 PPT 配图请求都必须 action=call_tool，并从 leader_callable_catalog.tools 选择 generate_image_tool；思维导图、流程图、架构图必须选择对应的 generate_*_tool 结构化图表工具；不要单独委派任何智能体。",
        "target_agent 固定为 leader_agent；call_tool 时 tool_name 必须来自 leader_callable_catalog.tools.name。",
        "action=call_tool 时，answer 必须是一句简短自然的进行中回复，例如“正在为你查询今日课表。”；最终结果会在工具返回后再由模型整理。",
        "用户要求生成图片/海报/配图，且出现「这样的」「类似」「同款」等指代时，必须先结合 conversation_context 与最近识图/讨论结果整理完整画面需求，再调用 generate_image_tool；不要只把用户最后一句话直接交给图片工具。",
    ])
    return policies


def build_leader_router_user_prompt(
    input_text: str,
    rag_strategy: str,
    profile_context: Optional[Dict[str, Any]] = None,
    callable_catalog: Optional[Dict[str, Any]] = None,
    conversation_context: Optional[Dict[str, Any]] = None,
) -> str:
    cleaned_input, _ = extract_image_references(input_text or "")
    return json.dumps({
        "user_input": cleaned_input,
        "profile_snapshot": profile_context or {},
        "conversation_context": conversation_context or {},
        "leader_callable_catalog": callable_catalog or {},
        "profile_usage_policy": _leader_profile_usage_policy(callable_catalog),
        "leader_output_push_strategies": LEADER_OUTPUT_PUSH_STRATEGIES,
    }, ensure_ascii=False)


def parse_json_object(text: str) -> Dict[str, Any]:
    raw = (text or "").strip().lstrip("\ufeff")
    if raw.startswith("```"):
        raw = re.sub(r"^```(?:json)?", "", raw, flags=re.IGNORECASE).strip()
        raw = re.sub(r"```$", "", raw).strip()
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        # 允许 JSON 前后存在说明文字，但只取第一个可完整解析的对象。
        # 贪婪的 \{.*\} 会在字符串中包含大括号时吞掉后续内容。
        decoder = json.JSONDecoder()
        for index, char in enumerate(raw):
            if char != "{":
                continue
            try:
                parsed, _ = decoder.raw_decode(raw[index:])
            except (TypeError, ValueError, json.JSONDecodeError):
                continue
            return parsed if isinstance(parsed, dict) else {}
        return {}
