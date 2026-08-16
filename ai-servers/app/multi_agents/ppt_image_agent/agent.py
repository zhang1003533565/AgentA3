from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class PptImageAgent:
    name = "ppt_image_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(self.name, input_text, evidence or [], model_provider=chat_service)


ppt_image_agent = PptImageAgent()

__all__ = ["ppt_image_agent"]
