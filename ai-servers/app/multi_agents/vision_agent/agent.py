from typing import Any, Dict, List

from fastapi import HTTPException

from app.model_providers.multimodal import extract_image_references
from app.multi_agents.runtime import complete_agent_or_raise


class VisionAgent:
    name = "vision_agent"

    def process(
        self,
        input_text: str,
        evidence: List[Dict[str, Any]],
        chat_service=None,
    ) -> str:
        _, image_urls = extract_image_references(input_text)
        if not image_urls:
            raise HTTPException(status_code=400, detail="图片识别智能体没有收到可识别的图片")
        return complete_agent_or_raise(
            self.name,
            input_text,
            evidence or [],
            model_provider=chat_service,
        )


vision_agent = VisionAgent()

__all__ = ["VisionAgent", "vision_agent"]
