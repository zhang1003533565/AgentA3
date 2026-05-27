from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.multi_agents.catalog import normalize_agent_name
from app.multi_agents.image_agent.agent import image_agent
from app.multi_agents.mind_map_agent.agent import mind_map_agent
from app.multi_agents.ppt_agent.agent import ppt_agent
from app.multi_agents.question_type_agents import QUESTION_TYPE_AGENTS
from app.multi_agents.textbook_knowledge_agent.agent import textbook_knowledge_agent


def run_specialist_agent(
    agent_name: Optional[str],
    input_text: str,
    evidence: List[Dict[str, Any]],
    chat_service=None,
) -> str:
    normalized = normalize_agent_name(agent_name)
    if not normalized or normalized == "leader_agent":
        raise HTTPException(status_code=400, detail=f"无法执行专业智能体：{agent_name or '未指定'}")
    if normalized == "mind_map_agent":
        return mind_map_agent.build_mind_map(input_text, evidence, chat_service=chat_service)
    if normalized == "textbook_knowledge_agent":
        return textbook_knowledge_agent.summarize_knowledge_points(input_text, evidence, chat_service=chat_service)
    if normalized in QUESTION_TYPE_AGENTS:
        return QUESTION_TYPE_AGENTS[normalized].generate_questions(input_text, evidence, chat_service=chat_service)
    if normalized == "ppt_agent":
        return ppt_agent.build_outline(input_text, evidence, chat_service=chat_service)
    if normalized == "image_agent":
        return image_agent.build_image_prompt(input_text, evidence, chat_service=chat_service)
    raise HTTPException(status_code=400, detail=f"不支持的智能体：{normalized}")
