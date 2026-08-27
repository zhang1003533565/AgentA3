from pathlib import Path
from typing import Any, Dict, Optional

from fastapi import HTTPException

ALL_RAG_STRATEGIES: list[str] = []
TEXT_MODEL_MODALITY = ["text"]
IMAGE_MODEL_MODALITY = ["image"]
VISION_MODEL_MODALITY = ["vision"]
VIDEO_MODEL_MODALITY = ["video"]
EXAMPLE_INPUT_FILENAME = "example_input.md"

QUESTION_AGENT_SPECS = {
    "textbook_question_single_choice_agent": ("选择题智能体", "single_choice", "生成单选题、选项、正确答案和解析。", "生成 5 道数据结构栈与队列的选择题"),
    "textbook_question_fill_blank_agent": ("填空题智能体", "fill_blank", "生成填空题、标准答案和解析。", "生成 5 道数据结构栈与队列的填空题"),
    "textbook_question_true_false_agent": ("判断题智能体", "true_false", "生成判断题、正确判断和解析。", "生成 5 道数据结构栈与队列的判断题"),
    "textbook_question_multiple_choice_agent": ("多选题智能体", "multiple_choice", "生成多选题、多个正确选项和解析。", "生成 5 道数据结构栈与队列的多选题"),
    "textbook_question_short_answer_agent": ("简答题智能体", "short_answer", "生成简答题、答案要点和评分参考。", "生成 5 道数据结构栈与队列的简答题"),
    "textbook_question_calculation_agent": ("计算题智能体", "calculation", "生成计算题、解题步骤和最终答案。", "生成 5 道数据结构栈与队列的计算题"),
    "textbook_question_programming_agent": ("编程题智能体", "programming", "生成编程题、输入输出要求、参考思路和测试用例。", "生成 3 道数据结构栈与队列的编程题"),
}

MEETING_AGENT_SPECS = {
    "meeting_controller_agent": ("会议总控智能体", "meeting_control", "负责会议状态管理、任务分发和流程调度。", "根据这段会议记录梳理会议状态、议程进度、任务分发和下一步流程"),
    "meeting_transcription_agent": ("语音转写智能体", "meeting_transcription", "负责语音识别结果整理、说话人区分和发言文本规范化。", "整理这段会议转写文本，区分说话人并修正发言格式"),
    "meeting_summary_agent": ("会议总结智能体", "meeting_summary", "负责提炼核心观点、主要结论、任务分工和后续计划。", "总结这段会议的核心观点、结论、任务分工和后续计划"),
    "meeting_member_analysis_agent": ("成员分析智能体", "meeting_member_analysis", "负责识别成员知识薄弱点、理解偏差和参与特征。", "分析这段会议中各成员的理解偏差、薄弱点和参与特征"),
    "meeting_resource_recommendation_agent": ("资源推荐智能体", "meeting_resource_recommendation", "负责为不同成员选择学习资源和推送策略。", "根据这段会议为每位成员推荐学习资源和推送策略"),
    "meeting_voice_broadcast_agent": ("语音播报智能体", "meeting_voice_broadcast", "负责将总结报告、学习建议和推荐内容转换为适合播报的文本。", "把这段会议总结改写成适合语音播报的脚本"),
}

PPT_AGENT_SPECS = {
    "ppt_outline_agent": ("PPT 大纲智能体", "ppt_outline", "负责生成 PPT 整体大纲、页序、每页标题、讲解目标和内容要点。", "根据数据结构中栈与队列的知识点生成 6 页 PPT 大纲"),
    "ppt_structure_agent": ("PPT 结构智能体", "ppt_structure", "按照 Presenton 的结构选择契约，为每一页选择模板中的布局组件。", "根据确认后的 PPT 大纲和模板布局目录选择逐页 layoutId"),
    "ppt_content_agent": ("PPT 逐页内容智能体", "ppt_content", "根据确认后的大纲和原始资料撰写逐页标题、要点、解释与视觉建议。", "根据确认后的大纲生成逐页可展示内容"),
    "ppt_review_agent": ("PPT 审查智能体", "ppt_review", "负责审查 PPT 内容、布局和教学适配度，并输出问题清单与置信度评分。", "审查这份 PPT 大纲和布局，给出问题清单、修改建议和置信度"),
    "ppt_image_agent": ("PPT 配图提示词智能体", "ppt_image", "只负责为 PPT 封面、插图、示意图生成图片提示词和视觉素材建议，不直接调用图片模型。", "根据这份 PPT 大纲生成封面图和关键页面插图提示词"),
    "ppt_to_docx_agent": ("PPT 转 DOCX 智能体", "ppt_to_docx", "负责将 PPTX 文件转换为 DOCX，按幻灯片顺序重排内容并保留图片。", "上传 PPTX 文件后转换为 DOCX，允许 Word 重新排版"),
}

LEARNING_WORKFLOW_AGENT_SPECS = {
    "python_code_lab_agent": (
        "Python 代码实验智能体",
        "python_code_lab",
        "根据学习路径、画像、掌握度和课程证据生成可运行、可验证的 Python 代码实验。",
        "为 Python 循环与函数生成分步代码实验、预期输出和自检项",
        ["strict_code_lab_json"],
    ),
    "python_practice_set_agent": (
        "Python 混合练习智能体",
        "python_practice_set",
        "生成至少覆盖单选、多选、判断、填空和代码输出的证据化 Python 混合练习。",
        "为 Python 循环与函数生成五种题型的混合练习和解析",
        ["strict_mixed_practice_json"],
    ),
    "extension_reading_agent": (
        "Python 拓展阅读智能体",
        "extension_reading",
        "围绕当前 Python 学习节点生成难度递进、带证据来源的拓展阅读。",
        "为 Python 循环学习节点生成函数入门拓展阅读",
        ["strict_extended_reading_json"],
    ),
    "resource_review_agent": (
        "学习资源审核智能体",
        "resource_review",
        "统一审核多类学习资源的证据、正确性、教学适配度和输出契约。",
        "审核一批 Python 学习资源并逐项返回通过或拒绝结论",
        ["strict_resource_review_json"],
    ),
    "resource_package_agent": (
        "学习资源整合智能体",
        "resource_package",
        "将满足核心资源与五类通过门槛的资源组装为学习包元数据。",
        "把审核通过的 Python 资源按学习路径组装为学习包",
        ["strict_resource_package_json"],
    ),
    "learning_path_agent": (
        "Python 学习路径智能体",
        "learning_path",
        "综合画像、掌握度、现有路径和课程证据，生成共享路径草案与资源简报。",
        "根据画像和掌握度为 Python 循环与函数生成路径草案",
        ["strict_learning_path_json"],
    ),
}

RESUME_AGENT_SPECS = {
    "resume_create_agent": (
        "AI 简历生成智能体",
        "resume_create",
        "通过多轮对话收集用户个人信息、教育背景、工作经历等，最终输出结构化简历 JSON。",
        "帮我写一份数据结构课程的简历",
        ["resume_json"],
    ),
    "resume_edit_agent": (
        "AI 一键改简历智能体",
        "resume_edit",
        "分析用户上传的现有简历问题，逐段优化完善，使简历更专业、更有竞争力。",
        "帮我看一下这份简历哪里需要优化，如何改进？",
        ["resume_optimization_json"],
    ),
}

DIAGRAM_AGENT_SPECS = {
    "diagram_mind_map_agent": ("图表思维导图智能体", "diagram_mind_map", "把知识点层级、概念关系和学习路径整理成 Mermaid 思维导图。", "进程调度知识点思维导图材料"),
    "diagram_flowchart_agent": ("图表流程图智能体", "diagram_flowchart", "把算法步骤、业务过程和知识点流程整理成 Mermaid 流程图。", "括号匹配算法流程材料"),
    "diagram_activity_agent": ("图表活动图智能体", "diagram_activity", "把角色协作、任务执行和活动顺序整理成 Mermaid 活动图。", "会议任务活动流程材料"),
    "diagram_architecture_agent": ("图表架构图智能体", "diagram_architecture", "把系统模块、服务依赖和数据流整理成 Mermaid 架构图。", "智慧校园 AI 智能体架构材料"),
    "diagram_flowchart_prompt_agent": ("流程图提示词智能体", "diagram_flowchart_prompt", "把算法步骤、业务流程整理成用于生成流程图图片的文生图提示词。", "括号匹配算法流程"),
    "diagram_activity_prompt_agent": ("活动图提示词智能体", "diagram_activity_prompt", "把角色协作、任务流程整理成用于生成活动图图片的文生图提示词。", "会议任务活动流程"),
    "knowledge_graph_prompt_agent": ("知识图谱提示词智能体", "knowledge_graph_prompt", "把实体、概念和关系整理成用于生成知识图谱图片的文生图提示词。", "操作系统进程调度知识图谱"),
}

AGENT_ORDER = [
    "leader_agent",
    "tool_intent_router_agent",
    "profile_summary_agent",
    "vision_agent",
    "architecture_prompt_agent",
    *DIAGRAM_AGENT_SPECS.keys(),
    "mind_map_agent",
    "image_agent",
    "file_content_planner_agent",
    "textbook_knowledge_agent",
    *QUESTION_AGENT_SPECS.keys(),
    *MEETING_AGENT_SPECS.keys(),
    *PPT_AGENT_SPECS.keys(),
    *RESUME_AGENT_SPECS.keys(),
    *LEARNING_WORKFLOW_AGENT_SPECS.keys(),
    "python_coding_tutor_agent",
    "python_problem_generator_agent",
]

LEARNING_WORKFLOW_INTERNAL_AGENTS = frozenset(LEARNING_WORKFLOW_AGENT_SPECS)
DIAGRAM_SOURCE_AGENTS = frozenset({
    "diagram_mind_map_agent",
    "diagram_flowchart_agent",
    "diagram_activity_agent",
    "diagram_architecture_agent",
})
INTERNAL_VISUAL_AGENTS = frozenset({
    "vision_agent",
    "image_agent",
    "mind_map_agent",
    "architecture_prompt_agent",
    "diagram_flowchart_prompt_agent",
    "diagram_activity_prompt_agent",
    "knowledge_graph_prompt_agent",
    "ppt_image_agent",
})
FILE_EXPORT_INTERNAL_AGENTS = frozenset({"file_content_planner_agent"})
RESUME_INTERNAL_AGENTS = frozenset({"resume_create_agent", "resume_edit_agent"})
INTERNAL_ONLY_AGENT_NAMES = frozenset({"tool_intent_router_agent"})
LEADER_CALLABLE_AGENT_ORDER = tuple(
    agent_name
    for agent_name in AGENT_ORDER
    if agent_name != "leader_agent"
    and agent_name not in INTERNAL_ONLY_AGENT_NAMES
    and agent_name not in LEARNING_WORKFLOW_INTERNAL_AGENTS
    and agent_name not in DIAGRAM_SOURCE_AGENTS
    and agent_name not in INTERNAL_VISUAL_AGENTS
    and agent_name not in FILE_EXPORT_INTERNAL_AGENTS
    and agent_name not in RESUME_INTERNAL_AGENTS
)


def _question_agent_profile(agent_name: str, role: str, intent: str, purpose: str, example_input: str) -> Dict[str, Any]:
    return {
        "role": role,
        "purpose": f"基于用户输入或 Java 已接入的第三方知识库能力{purpose}",
        "inputs": ["topic", "evidence", "count"],
        "outputs": ["strict_question_bank_json"],
        "skills": ["question generation", "answer key", "assessment design", intent],
        "intent": intent,
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": f"直接生成{role.replace('智能体', '')}",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [intent, role, role.replace("智能体", ""), agent_name],
        "exampleInput": example_input,
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    }


def _meeting_agent_profile(agent_name: str, role: str, intent: str, purpose: str, example_input: str) -> Dict[str, Any]:
    return {
        "role": role,
        "purpose": purpose,
        "inputs": ["meeting_content", "participants", "context"],
        "outputs": ["meeting_markdown"],
        "skills": ["meeting analysis", "workflow orchestration", intent],
        "intent": intent,
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": f"直接处理会议内容生成{role.replace('智能体', '')}结果",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [intent, role, role.replace("智能体", ""), agent_name],
        "exampleInput": example_input,
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    }


def _learning_workflow_agent_profile(
    agent_name: str,
    role: str,
    intent: str,
    purpose: str,
    example_input: str,
    outputs: list[str],
) -> Dict[str, Any]:
    inputs = {
        "learning_path_agent": [
            "topic", "profile_snapshot", "mastery_snapshot", "path_snapshot", "evidence",
        ],
        "resource_review_agent": ["resources", "package_rules", "evidence"],
        "resource_package_agent": ["path_draft", "passed_resources", "package_rules", "evidence"],
    }.get(
        agent_name,
        ["topic", "resource_brief", "profile_snapshot", "mastery_snapshot", "path_snapshot", "evidence"],
    )
    return {
        "role": role,
        "purpose": purpose,
        "inputs": inputs,
        "outputs": outputs,
        "skills": ["typed learning workflow", "evidence grounding", intent],
        "intent": intent,
        "needRetrieval": False,
        "executionMode": "workflow_internal",
        "executionModeLabel": "仅由 Python 学习资源 DAG 内部调用",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [intent, role, role.replace("智能体", ""), agent_name],
        "exampleInput": example_input,
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    }


def _resume_agent_profile(agent_name: str, role: str, intent: str, purpose: str, example_input: str, outputs: list[str]) -> Dict[str, Any]:
    input_map = {
        "resume_create_agent": ["user_request", "conversation_context"],
        "resume_edit_agent": ["uploaded_resume", "target_position", "conversation_context"],
    }
    return {
        "role": role,
        "purpose": purpose,
        "inputs": input_map[agent_name],
        "outputs": outputs,
        "skills": ["resume generation", "resume optimization", intent],
        "intent": intent,
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": f"直接{role.replace('智能体', '')}",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [intent, role, role.replace("智能体", ""), agent_name],
        "exampleInput": example_input,
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    }

def _ppt_profile(agent_name: str, role: str, intent: str, purpose: str, example_input: str) -> Dict[str, Any]:
    output_type = {
        "ppt_outline_agent": "ppt_outline_markdown",
        "ppt_structure_agent": "presenton_structure_json",
        "ppt_content_agent": "slide_json",
        "ppt_review_agent": "ppt_review_markdown",
        "ppt_image_agent": "ppt_image_prompt_markdown",
        "ppt_to_docx_agent": "docx_file",
    }[agent_name]
    alias_core = intent.replace("ppt_", "")
    extra_aliases = []
    if agent_name == "ppt_outline_agent":
        extra_aliases = ["ppt", "课件大纲智能体", "PPT 大纲智能体"]
    elif agent_name == "ppt_to_docx_agent":
        extra_aliases = ["ppt 转 docx", "pptx 转 docx", "ppt 转 word", "pptx 转 word", "PPT 转 DOCX 智能体", "PPT 转 Word 智能体"]
    return {
        "role": role,
        "purpose": purpose,
        "inputs": ["pptx_file", "conversion_request"] if agent_name == "ppt_to_docx_agent" else ["topic_or_upstream_ppt_result", "evidence", "constraints"],
        "outputs": [output_type],
        "skills": ["pptx conversion", "docx generation", intent] if agent_name == "ppt_to_docx_agent" else ["ppt generation", "presentation design", intent],
        "intent": intent,
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": "直接转换 PPTX 文件为 DOCX" if agent_name == "ppt_to_docx_agent" else f"直接生成{role.replace('智能体', '')}结果",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [
            intent,
            alias_core,
            *extra_aliases,
            role,
            role.replace("智能体", ""),
            agent_name,
        ],
        "exampleInput": example_input,
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    }


def _diagram_profile(agent_name: str, role: str, intent: str, purpose: str, example_input: str) -> Dict[str, Any]:
    output_type = {
        "diagram_mind_map_agent": "mermaid_mindmap",
        "diagram_flowchart_agent": "mermaid_flowchart",
        "diagram_activity_agent": "mermaid_activity_flowchart",
        "diagram_architecture_agent": "mermaid_architecture",
        "diagram_flowchart_prompt_agent": "flowchart_prompt_text",
        "diagram_activity_prompt_agent": "activity_prompt_text",
        "knowledge_graph_prompt_agent": "knowledge_graph_prompt_text",
    }[agent_name]
    alias_map = {
        "diagram_mind_map_agent": ["mind_map", "mindmap", "思维导图", "脑图", "思维导图智能体", "mind_map_agent"],
        "diagram_flowchart_agent": ["flowchart", "流程图", "流程图智能体", "流程"],
        "diagram_activity_agent": ["activity_diagram", "活动图", "活动图智能体", "泳道图", "任务活动图"],
        "diagram_architecture_agent": ["architecture_diagram", "架构图", "系统架构图", "架构图智能体", "系统架构"],
        "diagram_flowchart_prompt_agent": ["flowchart_prompt", "流程图提示词", "流程图提示词智能体"],
        "diagram_activity_prompt_agent": ["activity_prompt", "活动图提示词", "活动图提示词智能体"],
        "knowledge_graph_prompt_agent": ["knowledge_graph_prompt", "知识图谱提示词", "知识图谱提示词智能体", "概念图提示词"],
    }
    is_prompt_agent = agent_name.endswith("_prompt_agent")
    return {
        "role": role,
        "purpose": purpose,
        "inputs": ["diagram_material", "evidence"],
        "outputs": [output_type],
        "skills": ["prompt generation", "visual description", intent] if is_prompt_agent else ["diagram generation", "mermaid", intent],
        "intent": intent,
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": f"直接生成{role.replace('智能体', '')}",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [intent, role, role.replace("智能体", ""), agent_name, *alias_map[agent_name]],
        "exampleInput": example_input,
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    }


AGENT_PROFILES: Dict[str, Dict[str, Any]] = {
    "leader_agent": {
        "role": "Leader 智能体",
        "purpose": "统一理解用户任务，路由到个人画像汇总、思维导图、教材知识点、各题型出题、会议处理、PPT、图片等专业智能体，并基于 Java 数据库中的 LLM 配置完成必要的直接回答。",
        "inputs": ["user_query", "rag_strategy", "session_token", "history"],
        "outputs": ["intent", "target_agent", "need_retrieval", "answer"],
        "skills": ["task routing", "agent orchestration", "memory", "llm direct answering"],
        "intent": "auto",
        "needRetrieval": False,
        "executionMode": "leader_orchestration",
        "executionModeLabel": "Leader 意图识别与自动分发",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": ["leader", "leader_agent", "总控智能体", "leader 智能体"],
        "exampleInput": "帮我把数据结构中的栈与队列整理成 PPT 大纲",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    },
    "profile_summary_agent": {
        "role": "个人画像汇总智能体",
        "purpose": "把 Java 后端画像快照整理成强项、欠缺、置信度说明、补证建议和 Leader 参考规则；只解释画像，不修改分数。",
        "inputs": ["profile_snapshot", "dimensions", "evidence_counts", "confidence_level"],
        "outputs": ["strict_profile_summary_json"],
        "skills": ["profile summarization", "confidence explanation", "leader personalization policy"],
        "intent": "profile_summary",
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": "直接汇总个人画像快照",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": ["profile_summary", "profile_summary_agent", "个人画像汇总", "画像汇总智能体", "个人画像汇总智能体"],
        "exampleInput": "根据用户画像快照生成强项、欠缺、置信度说明和补证建议 JSON",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    },
    "file_content_planner_agent": {
        "role": "文件内容编排智能体",
        "purpose": "识别待转换内容和目标格式，生成供 Word、Excel、Markdown、PPT 文件工具消费的结构化内容草稿。",
        "inputs": ["user_request", "target_format", "source_content", "conversation_context"],
        "outputs": ["strict_file_content_plan_json"],
        "skills": ["content selection", "document structuring", "format-aware planning"],
        "intent": "file_content_planning",
        "needRetrieval": False,
        "executionMode": "tool_internal",
        "executionModeLabel": "仅由文件导出工具内部调用",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": ["file_content_planner_agent", "文件内容编排智能体", "Word 知识转换智能体", "文件知识转换智能体"],
        "exampleInput": "把刚才关于 Python 发展历史的内容整理成 Word 文档",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    },
    "tool_intent_router_agent": {
        "role": "工具意图识别智能体",
        "purpose": "在 Leader 路由前强制提取用户意图、关键词、实体、约束和查询变体；不由 Leader 作为业务智能体路由，但允许后台单独测试和绑定模型。",
        "inputs": ["user_query", "enabled_tools"],
        "outputs": ["intent", "keywords", "entities", "constraints", "query_variants"],
        "skills": ["intent extraction", "keyword extraction", "entity extraction", "query rewriting"],
        "intent": "tool_intent_routing",
        "needRetrieval": False,
        "executionMode": "internal_tool",
        "executionModeLabel": "生产环境由 tool_intent_router 强制自动调用；后台可单独测试",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": ["tool_intent_router", "tool_intent_router_agent", "工具意图识别", "意图识别智能体"],
        "exampleInput": "从用户问题中提取意图、关键词、实体、约束和最多三个查询变体",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
        "internalOnly": True,
        "mandatory": True,
        "toolName": "tool_intent_router",
    },
    "vision_agent": {
        "role": "图片识别智能体",
        "purpose": "使用视觉理解模型识别聊天中上传的图片，结合用户问题描述画面、读取可见文字、分析图表或界面，并明确不确定内容。",
        "inputs": ["user_query", "image_urls", "image_attachments", "conversation_context"],
        "outputs": ["image_analysis_text"],
        "skills": ["image understanding", "visual question answering", "ocr", "chart analysis", "screenshot analysis"],
        "intent": "image_understanding",
        "needRetrieval": False,
        "executionMode": "tool_internal",
        "executionModeLabel": "由识图工具调用视觉理解模型",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": ["vision", "vision_agent", "图片识别智能体", "识图智能体", "图片理解", "识图"],
        "exampleInput": "请识别我上传的图片，概括画面内容并回答图片中的问题",
        "requiredModelModalities": VISION_MODEL_MODALITY,
    },
    **{
        agent_name: _diagram_profile(agent_name, *spec)
        for agent_name, spec in DIAGRAM_AGENT_SPECS.items()
    },
    "architecture_prompt_agent": {
        "role": "图表架构图提示词智能体",
        "purpose": "把系统说明、模块依赖和输入上下文整理为纯文本提示词，供图表架构图智能体继续生成 Mermaid 架构图。",
        "inputs": ["topic", "evidence"],
        "outputs": ["architecture_prompt_text"],
        "skills": ["prompt generation", "architecture visualization planning", "visual description"],
        "intent": "architecture_diagram_prompt",
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": "直接生成架构图提示词",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": ["architecture_diagram_prompt", "架构图提示词", "架构图提示词智能体", "图表架构图提示词", "architecture_prompt_agent"],
        "exampleInput": "为智慧校园 AI 智能体系统生成一段可交给图表架构图智能体使用的提示词",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    },
    "mind_map_agent": {
        "role": "思维导图图片提示词智能体",
        "purpose": "把教材知识点或用户主题转化为用于生成思维导图图片的文生图提示词（纯文本）。",
        "inputs": ["topic", "evidence"],
        "outputs": ["image_prompt_text"],
        "skills": ["image prompt generation", "mind map visualization design", "visual description"],
        "intent": "mind_map_image_prompt",
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": "直接生成思维导图图片的文字提示词",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": ["mind_map_image_prompt", "思维导图图片提示词", "思维导图图片提示词智能体"],
        "exampleInput": "为操作系统进程调度知识点生成思维导图图片的提示词",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    },
    "textbook_knowledge_agent": {
        "role": "教材知识点智能体",
        "purpose": "有材料时严格整理教材章节、课程内容、知识点和考点；无材料且用户明确要求自行生成时，根据用户主题生成带模型来源标记的知识材料。第三方知识库证据由 Java 后端接入。",
        "inputs": ["topic", "evidence"],
        "outputs": ["knowledge_markdown"],
        "skills": ["textbook knowledge extraction", "model-generated knowledge drafting", "markdown knowledge organization"],
        "intent": "textbook_knowledge",
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": "直接整理教材知识点",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [
            "textbook_knowledge",
            "教材知识点",
            "教材知识点智能体",
            "课本知识点智能体",
        ],
        "exampleInput": "查询并整理数据结构中栈与队列的教材知识点，输出 Markdown",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    },
    **{
        agent_name: _question_agent_profile(agent_name, *spec)
        for agent_name, spec in QUESTION_AGENT_SPECS.items()
    },
    **{
        agent_name: _meeting_agent_profile(agent_name, *spec)
        for agent_name, spec in MEETING_AGENT_SPECS.items()
    },
    **{
        agent_name: _ppt_profile(agent_name, *spec)
        for agent_name, spec in PPT_AGENT_SPECS.items()
    },
    **{
        agent_name: _learning_workflow_agent_profile(agent_name, *spec)
        for agent_name, spec in LEARNING_WORKFLOW_AGENT_SPECS.items()
    },
    "image_agent": {
        "role": "图片智能体",
        "purpose": "根据用户需求、课程主题、知识点证据和用户画像生成单张或批量图片，并返回图片 URL/Base64、任务状态和完整生成参数。",
        "inputs": ["topic", "evidence", "prompt", "style", "size", "count", "seed", "negativePrompt"],
        "outputs": ["image_generation_result"],
        "skills": ["text-to-image", "batch image generation", "image prompt", "visual planning", "multimodal context"],
        "intent": "image",
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": "直接生成图片或批量图片",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": ["image", "image_agent", "图片智能体", "配图智能体", "文生图", "批量文生图"],
        "exampleInput": "为操作系统进程调度知识点生成 4 张课堂教学配图，风格为扁平教学插画，尺寸 1024x1024",
        "requiredModelModalities": IMAGE_MODEL_MODALITY,
    },
    **{
        agent_name: _resume_agent_profile(agent_name, *spec)
        for agent_name, spec in RESUME_AGENT_SPECS.items()
    },
    "python_coding_tutor_agent": {
        "role": "Python 编程辅导智能体",
        "purpose": "参照 LeetCode AI 助手，为在线刷题用户提供分级提示、思路讲解、代码解释与报错分析；辅助而非代劳。",
        "inputs": ["questionType", "problem", "userCode", "judgeResult", "followUp", "history"],
        "outputs": ["markdown"],
        "skills": ["code tutoring", "progressive hints", "debug guidance", "code explanation", "anti-cheating guidance"],
        "intent": "python_coding_tutor",
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": "直接生成编程辅导回答",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [
            "python_coding_tutor",
            "python_coding_tutor_agent",
            "编程辅导",
            "编程辅导智能体",
            "AI 编程助手",
            "代码解释",
            "给我提示",
            "报错分析",
        ],
        "exampleInput": "帮我分析这段两数之和的代码为什么超时",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    },
    "python_problem_generator_agent": {
        "role": "Python 刷题题目生成器",
        "purpose": "按主题/难度/数量生成可直接入库的 Python 刷题题目（对齐 python_problem 表结构，含判题用例与多解标准答案）。",
        "inputs": ["topic", "difficulty", "count"],
        "outputs": ["python_problem_set_json"],
        "skills": ["problem generation", "testcase authoring", "python", "multi-solution authoring"],
        "intent": "python_problem_generation",
        "needRetrieval": False,
        "executionMode": "direct_agent",
        "executionModeLabel": "直接生成 Python 刷题题目",
        "defaultRagStrategy": "",
        "supportedRagStrategies": [],
        "aliases": [
            "python_problem_generator",
            "python_problem_generator_agent",
            "生成 Python 题目",
            "AI 生成题目",
            "刷题题目生成",
        ],
        "exampleInput": "数组 + 双指针，中等难度，生成 2 道",
        "requiredModelModalities": TEXT_MODEL_MODALITY,
    },
}
