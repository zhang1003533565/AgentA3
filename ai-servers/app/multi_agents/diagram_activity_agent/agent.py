import json
from typing import Any, Dict, List

from fastapi import HTTPException

from app.image_generation import get_qwen_image_provider
from app.models.image_generation import ImageGenerationRequest
from app.model_providers.runtime_config import get_active_llm_config
from app.multi_agents.diagram_common import DiagramMermaidAgent


class ActivityDiagramAgent(DiagramMermaidAgent):
    def __init__(self) -> None:
        super().__init__("diagram_activity_agent", ("flowchart", "graph"))

    def generate_images_json(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        """生成活动图图片，返回包含图片 URL 的 JSON。"""
        prompt = self._prepare_prompt(topic, evidence, chat_service=chat_service)
        return self._call_image_api(prompt)

    def _prepare_prompt(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        normalized = (topic or "").strip()
        if normalized:
            return normalized
        raise HTTPException(status_code=400, detail="图表活动图智能体缺少生成提示词，已禁止本地兜底生成。")

    def _call_image_api(self, prompt: str) -> str:
        active_config = get_active_llm_config()
        payload = {
            "prompt": prompt,
            "size": "1664x928",
            "count": 1,
            "negativePrompt": "低清晰度、文字乱码、模糊线条、错误的文字、混乱的排版",
            "returnType": "url",
            "chartType": "activity",
            "metadata": {
                "source": self.name,
                "diagramType": "activity",
            },
        }
        if active_config:
            payload.update({
                "provider": active_config.provider,
                "baseUrl": active_config.base_url,
                "apiKey": active_config.api_key,
                "model": active_config.model,
            })
        provider = get_qwen_image_provider()
        response = provider.generate(ImageGenerationRequest(**payload))
        return json.dumps(response.model_dump(), ensure_ascii=False)


diagram_activity_agent = ActivityDiagramAgent()
