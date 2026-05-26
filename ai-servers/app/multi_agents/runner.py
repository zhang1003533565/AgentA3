from typing import Any, Dict, List

from app.multi_agents.catalog import normalize_agent_name
from app.multi_agents.image_agent.agent import image_agent
from app.multi_agents.md_knowledge_agent.agent import md_knowledge_agent
from app.multi_agents.mind_map_agent.agent import mind_map_agent
from app.multi_agents.ppt_agent.agent import ppt_agent
from app.multi_agents.textbook_knowledge_agent.agent import textbook_knowledge_agent
from app.multi_agents.textbook_question_bank_agent.agent import textbook_question_bank_agent


def run_specialist_agent(
    agent_name: str | None,
    input_text: str,
    evidence: List[Dict[str, Any]],
    fallback_answer: str = "",
) -> str:
    normalized = normalize_agent_name(agent_name)
    if not normalized or normalized == "leader_agent":
        return fallback_answer
    if normalized == "mind_map_agent":
        return mind_map_agent.build_mind_map(input_text, evidence)
    if normalized == "md_knowledge_agent":
        return md_knowledge_agent.extract_knowledge_points(input_text, evidence)
    if normalized == "textbook_knowledge_agent":
        return textbook_knowledge_agent.summarize_knowledge_points(input_text, evidence)
    if normalized == "textbook_question_bank_agent":
        return textbook_question_bank_agent.generate_questions(input_text, evidence)
    if normalized == "ppt_agent":
        return ppt_agent.build_outline(input_text, evidence)
    if normalized == "image_agent":
        return image_agent.build_image_prompt(input_text, evidence)
    return fallback_answer
