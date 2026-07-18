from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class LearningPathAgent:
    name = "learning_path_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(
            self.name,
            input_text,
            evidence or [],
            model_provider=chat_service,
        )


learning_path_agent = LearningPathAgent()

__all__ = ["learning_path_agent"]
