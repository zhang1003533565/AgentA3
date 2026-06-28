import json
import re
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.image_generation import get_qwen_image_provider
from app.models.image_generation import ImageBatchRequest, ImageGenerationRequest
from app.model_providers.runtime_config import get_active_llm_config


class ImageAgent:
    name = "image_agent"

    def build_image_prompt(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return self._direct_prompt(topic)

    def generate_images(
        self,
        topic: str,
        evidence: List[Dict[str, Any]],
        style: str = "",
        size: str = "1664x928",
        count: int = 1,
        seed: Optional[int] = None,
        negative_prompt: str = "",
        return_type: str = "url",
        chat_service=None,
    ) -> Dict[str, Any]:
        enhanced_prompt = self._direct_prompt(topic)
        normalized_count = max(1, min(int(count or 1), 8))
        payload = {
            "prompt": enhanced_prompt,
            "style": style or self._infer_style(topic),
            "size": size or "1024x1024",
            "count": normalized_count,
            "seed": seed,
            "negativePrompt": negative_prompt or "低清晰度、文字乱码、人物畸形、过度拥挤、与知识点无关的元素",
            "returnType": return_type,
            "metadata": {
                "topic": topic,
                "source": "image_agent",
                "evidenceCount": len(evidence or []),
            },
        }
        active_config = get_active_llm_config()
        if active_config:
            payload.update({
                "provider": active_config.provider,
                "baseUrl": active_config.base_url,
                "apiKey": active_config.api_key,
                "model": active_config.model,
            })
        provider = get_qwen_image_provider()
        if normalized_count == 1:
            response = provider.generate(ImageGenerationRequest(**payload))
        else:
            response = provider.batch(ImageBatchRequest(**payload, prompts=[enhanced_prompt] * normalized_count))
        return response.model_dump()

    def generate_images_json(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        result = self.generate_images(topic, evidence, chat_service=chat_service)
        return json.dumps(result, ensure_ascii=False)

    def _direct_prompt(self, topic: str) -> str:
        prompt = self._strip_markdown_fence(topic)
        if prompt:
            return prompt
        raise HTTPException(status_code=400, detail="图片智能体缺少生成提示词，已禁止本地兜底生成。")

    def _evidence_summary(self, evidence: List[Dict[str, Any]]) -> str:
        snippets = []
        for item in (evidence or [])[:3]:
            content = str(item.get("content") or item.get("text") or item.get("title") or "").strip()
            if content:
                snippets.append(content[:180])
        return "；".join(snippets)

    def _infer_style(self, topic: str) -> str:
        value = topic or ""
        if re.search(r"PPT|封面|课件", value, re.IGNORECASE):
            return "PPT 封面图"
        if re.search(r"教材|课堂|教学|知识点|课程", value):
            return "扁平教学插画"
        return "高质量数字插画"

    def _strip_markdown_fence(self, text: str) -> str:
        value = (text or "").strip()
        if value.startswith("```"):
            value = re.sub(r"^```[a-zA-Z0-9_-]*\s*", "", value)
            value = re.sub(r"\s*```$", "", value)
        return value.strip()


image_agent = ImageAgent()
