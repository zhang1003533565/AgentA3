from typing import Any, Dict, List

from app.services.langchain_chat_service import get_chat_service


class PptAgent:
    name = "ppt_agent"

    def build_outline(self, topic: str, evidence: List[Dict[str, Any]], slide_count: int = 6, chat_service=None) -> str:
        service = chat_service or get_chat_service()
        prompt = topic if slide_count == 6 else f"{topic}\n\n页数要求：{slide_count}"
        return service.generate_specialist_answer(self.name, prompt, evidence)


ppt_agent = PptAgent()
