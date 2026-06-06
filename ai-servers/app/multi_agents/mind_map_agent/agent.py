from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class MindMapAgent:
    name = "mind_map_agent"

    def build_mind_map(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        """生成思维导图图片的提示词（纯文本）"""
        return complete_agent_or_raise(self.name, topic, evidence, model_provider=chat_service)


mind_map_agent = MindMapAgent()
