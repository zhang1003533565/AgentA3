import json
from pathlib import Path
from typing import Any, Dict, Iterator, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.model_providers.factory import get_chat_model_provider


AGENT_ROOT = Path(__file__).resolve().parent


def load_agent_prompt(agent_name: str) -> str:
    prompt_path = AGENT_ROOT / agent_name / "prompt.md"
    try:
        prompt = prompt_path.read_text(encoding="utf-8").strip()
    except FileNotFoundError as exc:
        raise HTTPException(status_code=400, detail=f"智能体缺少 prompt.md：{agent_name}") from exc
    if not prompt:
        raise HTTPException(status_code=400, detail=f"智能体 prompt.md 为空：{agent_name}")
    return prompt


def build_agent_user_prompt(agent_name: str, input_text: str, evidence: List[Dict[str, Any]]) -> str:
    return json.dumps({
        "agent_name": agent_name,
        "user_input": input_text or "",
        "evidence": normalize_evidence(evidence),
        "failure_policy": "如果模型无法完成，请直接说明缺少什么信息或配置；不要输出本地兜底模板。",
    }, ensure_ascii=False, indent=2)


def normalize_evidence(evidence: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    normalized: List[Dict[str, Any]] = []
    for item in evidence or []:
        if not isinstance(item, dict):
            continue
        normalized.append({
            "id": item.get("id"),
            "source": item.get("source") or item.get("name") or item.get("type"),
            "content": str(item.get("content") or item.get("name") or "")[:1200],
            "score": item.get("score"),
            "metadata": item.get("metadata") if isinstance(item.get("metadata"), dict) else {},
        })
        if len(normalized) >= 8:
            break
    return normalized


def get_agent_model(model_provider: Optional[ChatModelProvider] = None) -> ChatModelProvider:
    return model_provider or get_chat_model_provider()


def complete_agent(
    agent_name: str,
    input_text: str,
    evidence: List[Dict[str, Any]],
    model_provider: Optional[ChatModelProvider] = None,
) -> str:
    provider = get_agent_model(model_provider)
    return provider.complete(
        system_prompt=load_agent_prompt(agent_name),
        user_prompt=build_agent_user_prompt(agent_name, input_text, evidence),
    )


def stream_agent(
    agent_name: str,
    input_text: str,
    evidence: List[Dict[str, Any]],
    model_provider: Optional[ChatModelProvider] = None,
) -> Iterator[str]:
    provider = get_agent_model(model_provider)
    yield from provider.stream_complete(
        system_prompt=load_agent_prompt(agent_name),
        user_prompt=build_agent_user_prompt(agent_name, input_text, evidence),
    )


def complete_agent_or_raise(
    agent_name: str,
    input_text: str,
    evidence: List[Dict[str, Any]],
    model_provider: Optional[ChatModelProvider] = None,
) -> str:
    answer = (complete_agent(agent_name, input_text, evidence, model_provider=model_provider) or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail=f"{agent_name} LLM 返回内容为空，已禁止本地模板兜底")
    return answer
