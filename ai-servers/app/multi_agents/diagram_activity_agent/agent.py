import json
from typing import Any, Dict, List

from app.image_generation import get_qwen_image_provider
from app.models.image_generation import ImageGenerationRequest
from app.model_providers.runtime_config import get_active_llm_config
from app.multi_agents.runtime import complete_agent_or_raise


class ActivityDiagramAgent:
    name = "diagram_activity_agent"

    def build_diagram(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        """生成活动图图片，返回包含图片 URL 的 JSON。"""
        prompt = self._prepare_prompt(topic, evidence, chat_service=chat_service)
        return self._call_image_api(prompt)

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return self.build_diagram(input_text, evidence, chat_service=chat_service)

    def _prepare_prompt(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        normalized = (topic or "").strip()
        # 如果输入已经是提示词智能体生成的详细描述，直接使用
        if len(normalized) > 200:
            return normalized
        # 否则用 LLM 增强为图片提示词
        instruction = (
            "请将以下活动流程描述转换为文生图提示词，用于生成一张专业的活动图图片。"
            "要求输出中文提示词，包含：泳道/角色分区、节点样式、颜色方案、箭头连接、整体风格。"
            "不需要输出 Mermaid 代码，只输出图片提示词正文。"
            f"活动描述：{normalized}"
        )
        try:
            return complete_agent_or_raise(self.name, instruction, evidence, model_provider=chat_service)
        except Exception:
            return f"生成一张简洁专业的活动图，展示：{normalized}。泳道布局，不同角色不同颜色，节点清晰，箭头明确。"

    def _call_image_api(self, prompt: str) -> str:
        active_config = get_active_llm_config()
        payload = {
            "prompt": prompt,
            "size": "1664x928",
            "count": 1,
            "negativePrompt": "低清晰度、文字乱码、模糊线条、错误的文字、混乱的排版",
            "returnType": "url",
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
