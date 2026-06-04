from importlib import import_module
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.multi_agents.catalog import normalize_agent_name


def run_specialist_agent(
    agent_name: Optional[str],
    input_text: str,
    evidence: List[Dict[str, Any]],
    chat_service=None,
) -> str:
    normalized = normalize_agent_name(agent_name)
    if not normalized or normalized == "leader_agent":
        raise HTTPException(status_code=400, detail=f"无法执行专业智能体：{agent_name or '未指定'}")
    agent = _load_agent(normalized)
    if hasattr(agent, "build_diagram"):
        return agent.build_diagram(input_text, evidence, chat_service=chat_service)
    if hasattr(agent, "build_mind_map"):
        return agent.build_mind_map(input_text, evidence, chat_service=chat_service)
    if hasattr(agent, "summarize_knowledge_points"):
        return agent.summarize_knowledge_points(input_text, evidence, chat_service=chat_service)
    if hasattr(agent, "generate_questions"):
        return agent.generate_questions(input_text, evidence, chat_service=chat_service)
    if hasattr(agent, "generate_images_json"):
        return agent.generate_images_json(input_text, evidence, chat_service=chat_service)
    if hasattr(agent, "process"):
        return agent.process(input_text, evidence, chat_service=chat_service)
    raise HTTPException(status_code=400, detail=f"不支持的智能体：{normalized}")


def _load_agent(agent_name: str) -> Any:
    module_name = f"app.multi_agents.{agent_name}.agent"
    try:
        module = import_module(module_name)
    except ModuleNotFoundError as exc:
        if exc.name != module_name and not module_name.startswith(f"{exc.name}."):
            raise
        raise HTTPException(status_code=400, detail=f"不支持的智能体：{agent_name}") from exc
    agent = getattr(module, agent_name, None)
    if agent is None:
        raise HTTPException(status_code=500, detail=f"{agent_name} 智能体目录缺少运行实例")
    return agent
