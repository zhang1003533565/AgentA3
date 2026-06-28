import json
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.image_generation import get_qwen_image_provider
from app.models.image_generation import ImageGenerationRequest
from app.model_providers.runtime_config import get_active_llm_config


class DiagramArchitectureAgent:
    """架构图图片生成智能体 - 接收架构材料并生成实际架构图图片"""

    name = "diagram_architecture_agent"

    def generate_images(
        self,
        topic: str,
        evidence: List[Dict[str, Any]],
        style: str = "专业系统架构图，简洁分层信息图风格",
        size: str = "1664x928",
        count: int = 1,
        seed: Optional[int] = None,
        negative_prompt: str = "文字乱码、标签错误、箭头混乱、布局拥挤、装饰过多、与系统无关的元素、手绘草图感",
        return_type: str = "url",
        chat_service=None,
    ) -> Dict[str, Any]:
        prompt = self._compose_prompt(topic, evidence)
        payload = {
            "prompt": prompt,
            "style": style or "专业系统架构图，简洁分层信息图风格",
            "size": size or "1664x928",
            "count": max(1, min(int(count or 1), 8)),
            "seed": seed,
            "negativePrompt": negative_prompt or "文字乱码、标签错误、箭头混乱、布局拥挤、装饰过多、与系统无关的元素、手绘草图感",
            "returnType": return_type,
            "chartType": "architecture",
            "metadata": {
                "topic": (topic or "").strip(),
                "source": self.name,
                "usage": "系统架构图",
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
        response = provider.generate(ImageGenerationRequest(**payload))
        return response.model_dump()

    def generate_images_json(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        result = self.generate_images(topic, evidence, chat_service=chat_service)
        return json.dumps(result, ensure_ascii=False)

    def _compose_prompt(self, topic: str, evidence: List[Dict[str, Any]]) -> str:
        normalized_topic = (topic or "").strip()
        if not normalized_topic:
            raise HTTPException(status_code=400, detail="图表架构图智能体缺少生成提示词，已禁止本地兜底生成。")
        evidence_summary = self._evidence_summary(evidence)
        evidence_text = f"证据摘要：{evidence_summary}。" if evidence_summary else ""
        return (
            "请生成一张专业、清晰、适合汇报和系统说明场景的架构图图片。"
            "画面需要体现模块分层、服务依赖、数据流方向和主调用链，使用规整的信息图布局，避免艺术化夸张表现。"
            "如果输入中已经包含节点清单、关系清单、分层分组、布局要求、禁止事项或最终出图提示词，要严格优先遵守。"
            f"核心材料：{normalized_topic}。"
            f"{evidence_text}"
            "要求节点命名简洁、区域边界明确、箭头方向直观，突出前端、后端、AI 服务、数据层、外部依赖等已知关系；"
            "禁止补充输入里没有明确出现的组件、数据库、调用链或数据流。"
        )

    def _evidence_summary(self, evidence: List[Dict[str, Any]]) -> str:
        snippets = []
        for item in (evidence or [])[:4]:
            content = str(item.get("content") or item.get("text") or item.get("title") or "").strip()
            if content:
                snippets.append(content[:220])
        return "；".join(snippets)


diagram_architecture_agent = DiagramArchitectureAgent()

__all__ = ["DiagramArchitectureAgent", "diagram_architecture_agent"]
