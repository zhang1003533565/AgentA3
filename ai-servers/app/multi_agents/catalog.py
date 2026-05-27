from pathlib import Path
from typing import Any, Dict, Optional

from app.rag.core import RAG_STRATEGY_SPECS


ALL_RAG_STRATEGIES = sorted(RAG_STRATEGY_SPECS.keys())

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

AGENT_ORDER = [
    "leader_agent",
    "mind_map_agent",
    "textbook_knowledge_agent",
    *QUESTION_AGENT_SPECS.keys(),
    *MEETING_AGENT_SPECS.keys(),
    "ppt_agent",
    "image_agent",
]


def _question_agent_profile(agent_name: str, role: str, intent: str, purpose: str, example_input: str) -> Dict[str, Any]:
    return {
        "role": role,
        "purpose": f"基于教材知识点和检索证据{purpose}",
        "inputs": ["topic", "evidence", "count"],
        "outputs": ["question_markdown"],
        "skills": ["question generation", "answer key", "assessment design", intent],
        "intent": intent,
        "needRetrieval": True,
        "executionMode": "rag_then_agent",
        "executionModeLabel": f"RAG 检索后生成{role.replace('智能体', '')}",
        "defaultRagStrategy": "multi_agent_rag",
        "supportedRagStrategies": ALL_RAG_STRATEGIES,
        "aliases": [intent, role, role.replace("智能体", ""), agent_name],
        "exampleInput": example_input,
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
    }


AGENT_PROFILES: Dict[str, Dict[str, Any]] = {
    "leader_agent": {
        "role": "Leader 智能体",
        "purpose": "统一理解用户任务，路由到思维导图、教材知识点、各题型出题、会议处理、PPT、图片等专业智能体，并基于 Java 数据库中的 LLM 配置完成必要的直接回答。",
        "inputs": ["user_query", "rag_strategy", "session_token", "history"],
        "outputs": ["intent", "target_agent", "need_retrieval", "answer"],
        "skills": ["task routing", "agent orchestration", "memory", "llm direct answering"],
        "intent": "auto",
        "needRetrieval": False,
        "executionMode": "leader_orchestration",
        "executionModeLabel": "Leader 意图识别与自动分发",
        "defaultRagStrategy": "naive_rag",
        "supportedRagStrategies": [],
        "aliases": ["leader", "leader_agent", "总控智能体", "leader智能体"],
        "exampleInput": "帮我把数据结构中的栈与队列整理成 PPT 大纲",
    },
    "mind_map_agent": {
        "role": "思维导图智能体",
        "purpose": "把教材知识点或用户主题整理成层级化 Markdown/Mermaid 思维导图。",
        "inputs": ["topic", "evidence"],
        "outputs": ["mind_map_markdown"],
        "skills": ["mind map", "hierarchy extraction", "mermaid"],
        "intent": "mind_map",
        "needRetrieval": True,
        "executionMode": "rag_then_agent",
        "executionModeLabel": "RAG 检索后生成思维导图",
        "defaultRagStrategy": "multi_agent_rag",
        "supportedRagStrategies": ALL_RAG_STRATEGIES,
        "aliases": ["mind_map", "mindmap", "思维导图", "思维导图智能体", "脑图智能体"],
        "exampleInput": "把操作系统进程调度整理成思维导图",
    },
    "textbook_knowledge_agent": {
        "role": "教材知识点智能体",
        "purpose": "围绕教材章节、课程内容、Markdown 教材文本、知识点和考点做检索增强，并统一整理为 Markdown 教材知识点。",
        "inputs": ["authorization", "intent", "keyword", "input_text", "rag_strategy"],
        "outputs": ["matched_results", "retrieval_meta"],
        "skills": ["textbook retrieval", "markdown knowledge extraction", "hybrid search", "reranking", "graph/text-to-sql retrieval"],
        "intent": "textbook_knowledge",
        "needRetrieval": True,
        "executionMode": "rag_then_agent",
        "executionModeLabel": "RAG/接口召回后整理教材知识点",
        "defaultRagStrategy": "hybrid_search",
        "supportedRagStrategies": ALL_RAG_STRATEGIES,
        "aliases": [
            "textbook_knowledge",
            "教材知识点",
            "教材知识点智能体",
            "课本知识点智能体",
        ],
        "exampleInput": "查询并整理数据结构中栈与队列的教材知识点，输出 Markdown",
    },
    **{
        agent_name: _question_agent_profile(agent_name, *spec)
        for agent_name, spec in QUESTION_AGENT_SPECS.items()
    },
    **{
        agent_name: _meeting_agent_profile(agent_name, *spec)
        for agent_name, spec in MEETING_AGENT_SPECS.items()
    },
    "ppt_agent": {
        "role": "PPT 智能体",
        "purpose": "把主题和知识点证据整理成 PPT 页结构、讲解目标和页面内容建议。",
        "inputs": ["topic", "evidence", "slide_count"],
        "outputs": ["ppt_outline_markdown"],
        "skills": ["slide outline", "teaching flow", "presentation planning"],
        "intent": "ppt",
        "needRetrieval": True,
        "executionMode": "rag_then_agent",
        "executionModeLabel": "RAG 检索后生成 PPT 大纲",
        "defaultRagStrategy": "multi_agent_rag",
        "supportedRagStrategies": ALL_RAG_STRATEGIES,
        "aliases": ["ppt", "ppt_agent", "课件智能体", "PPT智能体"],
        "exampleInput": "根据数据结构中栈与队列的知识点生成 6 页课件大纲",
    },
    "image_agent": {
        "role": "图片智能体",
        "purpose": "根据课程主题和知识点证据生成教学配图、封面图、插图提示词。",
        "inputs": ["topic", "evidence"],
        "outputs": ["image_prompt"],
        "skills": ["image prompt", "visual planning", "multimodal context"],
        "intent": "image",
        "needRetrieval": True,
        "executionMode": "rag_then_agent",
        "executionModeLabel": "多模态 RAG 后生成图片提示词",
        "defaultRagStrategy": "multimodal_rag",
        "supportedRagStrategies": ALL_RAG_STRATEGIES,
        "aliases": ["image", "image_agent", "图片智能体", "配图智能体"],
        "exampleInput": "为操作系统进程调度知识点生成一张课堂教学配图提示词",
    },
}

AGENT_ALIASES = {
    alias.lower(): agent_name
    for agent_name, profile in AGENT_PROFILES.items()
    for alias in [agent_name, *profile.get("aliases", [])]
}


def get_agent_catalog() -> Dict[str, Any]:
    agents = [_build_agent(agent_name, include_documents=True) for agent_name in AGENT_ORDER]
    return {
        "total": len(agents),
        "invocation": {
            "chatEndpoint": "POST /internal/chat",
            "ragQueryEndpoint": "POST /internal/rag/query",
            "parameter": "agentName",
            "automaticRouting": "agentName 留空或填写 leader_agent",
            "strategyRule": "只有 needRetrieval=true 的专业智能体才需要 ragStrategy；Leader 不传 ragStrategy。",
            "llmConfigRule": "Leader 意图识别和所有专业智能体生成都必须由 Java 后端转发 ai.service.* 模型配置；配置缺失或模型失败会直接报错。",
        },
        "executionModes": {
            "leader_direct_answer": "Leader 直接回答",
            "leader_call_tool": "Leader 调用接口/工具",
            "leader_routed_direct_agent": "Leader 分发给非检索智能体",
            "leader_routed_rag": "Leader 分发给 RAG 智能体",
            "direct_agent": "专业智能体直接处理",
            "rag_then_agent": "RAG 检索后交给专业智能体",
        },
        "workflow": {
            "default": ["leader_agent", "textbook_knowledge_agent"],
            "mindMap": ["leader_agent", "textbook_knowledge_agent", "mind_map_agent"],
            "markdownKnowledge": ["leader_agent", "textbook_knowledge_agent"],
            "textbookKnowledge": ["leader_agent", "textbook_knowledge_agent"],
            "questionBank": ["leader_agent", "textbook_knowledge_agent", *QUESTION_AGENT_SPECS.keys()],
            "meeting": ["leader_agent", *MEETING_AGENT_SPECS.keys()],
            "ppt": ["leader_agent", "textbook_knowledge_agent", "ppt_agent"],
            "image": ["leader_agent", "textbook_knowledge_agent", "image_agent"],
        },
        "agents": agents,
    }


def get_agent_detail(agent_name: str) -> Optional[Dict[str, Any]]:
    agent_name = normalize_agent_name(agent_name) or ""
    if agent_name not in AGENT_PROFILES:
        return None
    return _build_agent(agent_name, include_documents=True)


def normalize_agent_name(agent_name: Optional[str]) -> Optional[str]:
    value = (agent_name or "").strip()
    if not value:
        return None
    return AGENT_ALIASES.get(value.lower())


def get_agent_profile(agent_name: Optional[str]) -> Optional[Dict[str, Any]]:
    normalized = normalize_agent_name(agent_name)
    if not normalized:
        return None
    return {"name": normalized, **AGENT_PROFILES[normalized]}


def _build_agent(agent_name: str, include_documents: bool) -> Dict[str, Any]:
    agent_dir = _agent_dir(agent_name)
    profile = dict(AGENT_PROFILES[agent_name])
    payload: Dict[str, Any] = {
        "name": agent_name,
        "role": profile["role"],
        "purpose": profile["purpose"],
        "inputs": profile["inputs"],
        "outputs": profile["outputs"],
        "skills": profile["skills"],
        "intent": profile["intent"],
        "needRetrieval": profile["needRetrieval"],
        "executionMode": profile["executionMode"],
        "executionModeLabel": profile["executionModeLabel"],
        "defaultRagStrategy": profile["defaultRagStrategy"],
        "supportedRagStrategies": profile["supportedRagStrategies"],
        "aliases": profile["aliases"],
        "invokeExample": {
            "input": profile.get("exampleInput", f"请使用{profile['role']}处理这段课程内容"),
            "agentName": agent_name,
            **(
                {"executionMode": "leader_orchestration"}
                if agent_name == "leader_agent"
                else (
                    {"ragStrategy": profile["defaultRagStrategy"]}
                    if profile["needRetrieval"]
                    else {"executionMode": "direct_agent"}
                )
            ),
        },
        "runtime": f"app.multi_agents.{agent_name}.agent",
        "directory": str(agent_dir),
        "files": {
            "agent": str(agent_dir / "agent.py"),
            "skill": str(agent_dir / "skill.md"),
            "prompt": str(agent_dir / "prompt.md"),
            "contract": str(agent_dir / "contract.md"),
            "tools": str(agent_dir / "tools.yaml"),
        },
    }
    if include_documents:
        payload["documents"] = {
            "skill": _read_text(agent_dir / "skill.md"),
            "prompt": _read_text(agent_dir / "prompt.md"),
            "contract": _read_text(agent_dir / "contract.md"),
            "tools": _read_text(agent_dir / "tools.yaml"),
            "readme": _read_text(agent_dir / "README.md"),
        }
    return payload


def _agent_dir(agent_name: str) -> Path:
    return Path(__file__).resolve().parent / agent_name


def _read_text(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")
