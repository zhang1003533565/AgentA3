import json
from importlib import import_module
from typing import Any, Callable, Dict, List, Optional

from fastapi import HTTPException

from app.multi_agents.catalog import AGENT_PROFILES, normalize_agent_name


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
    if hasattr(agent, "generate_mind_map_image_json"):
        # diagram_mind_map_agent: 接收提示词生成图片
        return agent.generate_mind_map_image_json(prompt=input_text)
    if hasattr(agent, "summarize_knowledge_points"):
        return agent.summarize_knowledge_points(input_text, evidence, chat_service=chat_service)
    if hasattr(agent, "generate_questions"):
        return agent.generate_questions(input_text, evidence, chat_service=chat_service)
    if hasattr(agent, "generate_images_json"):
        return agent.generate_images_json(input_text, evidence, chat_service=chat_service)
    if hasattr(agent, "process"):
        return agent.process(input_text, evidence, chat_service=chat_service)
    raise HTTPException(status_code=400, detail=f"不支持的智能体：{normalized}")


class LearningWorkflowRunner:
    """Production adapter from registered specialist dispatch to workflow contracts."""

    def __init__(
        self,
        chat_service=None,
        dispatcher: Optional[Callable[..., str]] = None,
    ):
        self.chat_service = chat_service
        self.dispatcher = dispatcher or run_specialist_agent

    def run(
        self,
        agent_name: str,
        input_text: str,
        evidence: List[Dict[str, Any]],
    ) -> str:
        answer = self.dispatcher(
            agent_name,
            input_text,
            evidence,
            chat_service=self.chat_service,
        )
        if agent_name not in {
            "textbook_knowledge_agent",
            "diagram_mind_map_agent",
            "ppt_outline_agent",
        }:
            return answer
        evidence_ids = _workflow_evidence_ids(evidence)
        return _adapt_legacy_workflow_resource(agent_name, answer, evidence_ids)


def _workflow_evidence_ids(evidence: List[Dict[str, Any]]) -> List[str]:
    evidence_ids: List[str] = []
    for index, item in enumerate(evidence or []):
        evidence_id = str(item.get("id") or "").strip() if isinstance(item, dict) else ""
        if not evidence_id:
            raise HTTPException(
                status_code=400,
                detail=f"学习工作流 evidence[{index}] 缺少 canonical id",
            )
        if evidence_id not in evidence_ids:
            evidence_ids.append(evidence_id)
    if not evidence_ids:
        raise HTTPException(status_code=400, detail="学习工作流必须提供 evidence")
    return evidence_ids


def _adapt_legacy_workflow_resource(
    agent_name: str,
    answer: str,
    evidence_ids: List[str],
) -> str:
    content = str(answer or "").strip()
    if not content:
        raise HTTPException(status_code=502, detail=f"{agent_name} 返回内容为空")
    if agent_name == "textbook_knowledge_agent":
        payload = {
            "resourceType": "knowledge_note",
            "content": content,
            "payload": {
                "kind": "knowledge_note",
                "note": {"markdown": content},
            },
            "evidenceIds": evidence_ids,
        }
    elif agent_name == "diagram_mind_map_agent":
        try:
            mind_map = json.loads(content)
        except json.JSONDecodeError as exc:
            raise HTTPException(
                status_code=502,
                detail="diagram_mind_map_agent 返回内容不是合法 JSON",
            ) from exc
        if not isinstance(mind_map, dict) or not mind_map:
            raise HTTPException(
                status_code=502,
                detail="diagram_mind_map_agent 返回空图片结果",
            )
        payload = {
            "resourceType": "mind_map",
            "content": content,
            "payload": {"kind": "mind_map", "mindMap": mind_map},
            "evidenceIds": evidence_ids,
        }
    else:
        payload = {
            "resourceType": "presentation",
            "content": content,
            "payload": {
                "kind": "presentation",
                "outline": content,
                "metadata": {},
            },
            "evidenceIds": evidence_ids,
        }
    return json.dumps(payload, ensure_ascii=False)


def _load_agent(agent_name: str) -> Any:
    if agent_name not in AGENT_PROFILES:
        raise HTTPException(status_code=400, detail=f"不支持的智能体：{agent_name}")
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


__all__ = ["LearningWorkflowRunner", "run_specialist_agent"]
