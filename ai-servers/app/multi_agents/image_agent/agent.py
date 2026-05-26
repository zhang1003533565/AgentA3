from typing import Any, Dict, List

from app.services.langchain_chat_service import get_chat_service


class ImageAgent:
    name = "image_agent"

    def build_image_prompt(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        service = chat_service or get_chat_service()
        return service.generate_specialist_answer(self.name, topic, evidence)


image_agent = ImageAgent()
