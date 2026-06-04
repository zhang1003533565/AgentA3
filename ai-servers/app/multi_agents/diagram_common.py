import re
from typing import Any, Dict, List, Sequence

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


class DiagramMermaidAgent:
    def __init__(self, name: str, allowed_declarations: Sequence[str]) -> None:
        self.name = name
        self.allowed_declarations = tuple(allowed_declarations)

    def build_diagram(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return _sanitize_mermaid_answer(
            self.name,
            complete_agent_or_raise(self.name, topic, evidence, model_provider=chat_service),
            self.allowed_declarations,
        )

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return self.build_diagram(input_text, evidence, chat_service=chat_service)


def _sanitize_mermaid_answer(agent_name: str, text: str, allowed_declarations: Sequence[str]) -> str:
    match = re.search(r"```mermaid\s*([\s\S]*?)```", text or "", flags=re.IGNORECASE)
    if not match:
        raise HTTPException(status_code=502, detail=f"{agent_name} 必须返回 Mermaid 代码块，当前返回不符合约束")
    code = (match.group(1) or "").strip()
    if not code:
        raise HTTPException(status_code=502, detail=f"{agent_name} 返回了空 Mermaid 代码块")
    first_line = code.splitlines()[0].strip().lower() if code.splitlines() else ""
    if not any(first_line.startswith(item.lower()) for item in allowed_declarations):
        allowed = "、".join(allowed_declarations)
        raise HTTPException(status_code=502, detail=f"{agent_name} 返回的 Mermaid 代码块必须以 {allowed} 开头")
    return f"```mermaid\n{code}\n```"
