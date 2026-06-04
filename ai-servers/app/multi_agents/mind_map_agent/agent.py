import re
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


class MindMapAgent:
    name = "mind_map_agent"

    def build_mind_map(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return _sanitize_mind_map_answer(
            complete_agent_or_raise(self.name, topic, evidence, model_provider=chat_service)
        )


mind_map_agent = MindMapAgent()


def _sanitize_mind_map_answer(text: str) -> str:
    match = re.search(r"```mermaid\s*([\s\S]*?)```", text or "", flags=re.IGNORECASE)
    if not match:
        raise HTTPException(status_code=502, detail="mind_map_agent 必须返回 Mermaid 代码块，当前返回不符合约束")
    code = (match.group(1) or "").strip()
    if not code:
        raise HTTPException(status_code=502, detail="mind_map_agent 返回了空 Mermaid 代码块")
    if not re.search(r"(^|\n)\s*mindmap\b", code, flags=re.IGNORECASE):
        raise HTTPException(status_code=502, detail="mind_map_agent 返回的 Mermaid 代码块缺少 mindmap 声明")
    return f"```mermaid\n{code}\n```"
