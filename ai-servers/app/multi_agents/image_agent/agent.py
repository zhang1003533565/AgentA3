import json
import re
from typing import Any, Dict, List, Optional

from app.image_generation import get_qwen_image_provider
from app.models.image_generation import ImageBatchRequest, ImageGenerationRequest
from app.services.langchain_chat_service import get_chat_service


class ImageAgent:
    name = "image_agent"

    def build_image_prompt(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return self._enhance_prompt(topic, evidence, chat_service=chat_service)

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
        enhanced_prompt = self._enhance_prompt(topic, evidence, chat_service=chat_service)
        normalized_count = max(1, min(int(count or 1), 8))
        payload = {
            "prompt": enhanced_prompt,
            "style": style or self._infer_style(topic),
            "size": size or "1664x928",
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
        provider = get_qwen_image_provider()
        if normalized_count == 1:
            response = provider.generate(ImageGenerationRequest(**payload))
        else:
            response = provider.batch(ImageBatchRequest(**payload, prompts=[enhanced_prompt] * normalized_count))
        return response.model_dump()

    def generate_images_json(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        result = self.generate_images(topic, evidence, chat_service=chat_service)
        return json.dumps(result, ensure_ascii=False)

    def _enhance_prompt(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        normalized_topic = (topic or "").strip()
        service = chat_service or get_chat_service()
        instruction = (
            "请根据用户输入和检索证据，生成一个可直接用于 Qwen 文生图模型的高质量图片提示词。"
            "要求先给中文提示词，再给英文提示词；包含主体、场景、构图、风格、色彩、用途和需要避免的元素。"
            "不要输出解释，只输出提示词正文。用户需求："
            f"{normalized_topic}\n\n证据摘要：{self._evidence_summary(evidence)}"
        )
        try:
            prompt = service.generate_specialist_answer(self.name, instruction, evidence)
        except Exception:
            prompt = ""
        prompt = self._strip_markdown_fence(prompt)
        if prompt:
            return prompt
        return self._fallback_prompt(normalized_topic, evidence)

    def _fallback_prompt(self, topic: str, evidence: List[Dict[str, Any]]) -> str:
        evidence_summary = self._evidence_summary(evidence)
        base = topic or "校园教学主题配图"
        return (
            f"中文提示词：生成一张清晰、专业、适合教学展示的图片，主题是“{base}”。"
            f"画面需要准确表达核心知识点，构图简洁，主体明确，适合课堂或 PPT 使用。"
            f"参考知识证据：{evidence_summary or '暂无检索证据，按用户主题生成'}。"
            "避免低清晰度、乱码文字、错误公式、畸形人物和与主题无关的装饰。\n"
            f"English prompt: Create a clear and professional educational illustration about {base}. "
            "Use a clean composition, accurate visual metaphors, readable layout, and classroom/PPT friendly style. "
            "Avoid blurry details, garbled text, wrong formulas, distorted people, and unrelated decorative elements."
        )

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
