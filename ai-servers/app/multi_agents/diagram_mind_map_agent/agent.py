import json
from typing import Any, Dict, List, Optional

from app.image_generation import get_qwen_image_provider
from app.models.image_generation import ImageGenerationRequest
from app.model_providers.runtime_config import get_active_llm_config
from app.multi_agents.diagram_common import DiagramMermaidAgent


class DiagramMindMapAgent(DiagramMermaidAgent):
    """思维导图图片生成智能体 - 接收提示词并生成实际的思维导图图片"""

    def __init__(self) -> None:
        super().__init__("diagram_mind_map_agent", ("mindmap",))

    def build_mind_map(self, topic, evidence, chat_service=None):
        return self.build_diagram(topic, evidence, chat_service=chat_service)

    def generate_mind_map_image(
        self,
        prompt: str,
        style: str = "扁平化教学插画风格",
        size: Optional[str] = None,
        seed: Optional[int] = None,
        negative_prompt: str = "文字乱码、模糊不清、色彩混乱、布局拥挤、畸形图标",
        return_type: str = "url",
    ) -> Dict[str, Any]:
        """
        根据提示词生成思维导图图片
        
        Args:
            prompt: 思维导图图片的提示词描述（由 mind_map_agent 生成）
            style: 图片风格
            size: 图片尺寸（可选，不传则使用模型默认尺寸）
            seed: 随机种子
            negative_prompt: 负面提示词
            return_type: 返回类型 (url/base64)
        
        Returns:
            包含图片 URL/Base64 和任务信息的字典
        """
        normalized_prompt = (prompt or "").strip()
        if not normalized_prompt:
            raise ValueError("提示词不能为空")
        
        # 构建图片生成请求
        payload = {
            "prompt": normalized_prompt,
            "style": style or "扁平化教学插画风格",
            "count": 1,
            "seed": seed,
            "negativePrompt": negative_prompt or "文字乱码、模糊不清、色彩混乱、布局拥挤、畸形图标",
            "returnType": return_type,
            "metadata": {
                "source": "diagram_mind_map_agent",
                "diagramType": "mindmap",
            },
        }
        
        # 只有当 size 有值时才添加
        if size:
            payload["size"] = size
        
        # 添加 LLM 配置
        active_config = get_active_llm_config()
        if active_config:
            payload.update({
                "provider": active_config.provider,
                "baseUrl": active_config.base_url,
                "apiKey": active_config.api_key,
                "model": active_config.model,
            })
        
        # 调用图片生成服务
        provider = get_qwen_image_provider()
        response = provider.generate(ImageGenerationRequest(**payload))
        
        return response.model_dump()

    def generate_mind_map_image_json(
        self,
        prompt: str,
        style: str = "扁平化教学插画风格",
        size: Optional[str] = None,
        seed: Optional[int] = None,
        negative_prompt: str = "文字乱码、模糊不清、色彩混乱、布局拥挤、畸形图标",
        return_type: str = "url",
    ) -> str:
        """生成思维导图图片并返回 JSON 格式结果"""
        result = self.generate_mind_map_image(
            prompt=prompt,
            style=style,
            size=size,
            seed=seed,
            negative_prompt=negative_prompt,
            return_type=return_type,
        )
        return json.dumps(result, ensure_ascii=False)


diagram_mind_map_agent = DiagramMindMapAgent()

__all__ = ["DiagramMindMapAgent", "diagram_mind_map_agent"]
