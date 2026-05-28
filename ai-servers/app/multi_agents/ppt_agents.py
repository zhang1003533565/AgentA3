from dataclasses import dataclass
from typing import Any, Dict, List

from app.services.langchain_chat_service import get_chat_service


@dataclass(frozen=True)
class PptAgent:
    name: str

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        service = chat_service or get_chat_service()
        return service.generate_specialist_answer(self.name, input_text, evidence or [])


ppt_outline_agent = PptAgent("ppt_outline_agent")
ppt_layout_agent = PptAgent("ppt_layout_agent")
ppt_review_agent = PptAgent("ppt_review_agent")
ppt_image_agent = PptAgent("ppt_image_agent")

PPT_AGENTS = {
    agent.name: agent
    for agent in (
        ppt_outline_agent,
        ppt_layout_agent,
        ppt_review_agent,
        ppt_image_agent,
    )
}
