from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class ActivityPromptAgent:
    name = "diagram_activity_prompt_agent"

    def build_activity_prompt(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        """生成可交给 diagram_activity_agent 的纯文本提示词。"""
        return complete_agent_or_raise(self.name, topic, evidence, model_provider=chat_service)

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return self.build_activity_prompt(input_text, evidence, chat_service=chat_service)


diagram_activity_prompt_agent = ActivityPromptAgent()